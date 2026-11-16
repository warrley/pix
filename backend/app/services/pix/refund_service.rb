require_relative "domain_error"

module Pix
  class RefundService
    Result = Struct.new(:success?, :transaction, :error, keyword_init: true)

    def self.call(...)
      new(...).call
    end

    def initialize(transaction_id:, reason:)
      @transaction_id = transaction_id
      @reason = reason.to_s.strip
    end

    def call
      if @reason.blank?
        error = DomainError.new(
          code: "refund.missing_reason",
          message: "Reason is required to refund a transaction",
          http_status: :unprocessable_entity
        )
        return Result.new(success?: false, transaction: nil, error: error)
      end

      transaction = Transaction.find_by(id: @transaction_id)
      unless transaction
        error = DomainError.new(
          code: "transaction.not_found",
          message: "Transaction not found",
          http_status: :not_found
        )
        return Result.new(success?: false, transaction: nil, error: error)
      end

      begin
        ActiveRecord::Base.transaction do
          transaction.lock!

          if transaction.refunded?
            raise DomainFailure, DomainError.new(
              code: "transaction.already_refunded",
              message: "Transaction is already refunded",
              http_status: :conflict
            )
          elsif !transaction.completed?
            raise DomainFailure, DomainError.new(
              code: "transaction.not_completed",
              message: "Only completed transactions can be refunded",
              http_status: :conflict
            )
          end

          # Lock accounts in consistent ID order to avoid deadlocks
          account_ids = [ transaction.source_account_id, transaction.destination_account_id ].sort
          locked_accounts = Account.lock.where(id: account_ids).index_by(&:id)

          source_account = locked_accounts[transaction.source_account_id]
          destination_account = locked_accounts[transaction.destination_account_id]

          if destination_account.balance_cents < transaction.amount_cents
            raise DomainFailure, DomainError.new(
              code: "refund.insufficient_funds",
              message: "Destination account has insufficient funds to process refund",
              http_status: :unprocessable_entity
            )
          end

          destination_account.update!(balance_cents: destination_account.balance_cents - transaction.amount_cents)
          source_account.update!(balance_cents: source_account.balance_cents + transaction.amount_cents)

          transaction.update!(
            status: :refunded,
            refunded_at: Time.current
          )

          TransactionEvent.create!(
            transaction_record: transaction,
            previous_status: "completed",
            current_status: "refunded",
            amount_cents: transaction.amount_cents,
            source_account: source_account,
            destination_account: destination_account,
            reason: @reason,
            created_at: Time.current
          )
        end
      rescue DomainFailure => e
        return Result.new(success?: false, transaction: nil, error: e.error)
      rescue StandardError => e
        error = DomainError.new(
          code: "transaction.refund_failed",
          message: e.message.presence || "Transaction could not be refunded",
          http_status: :unprocessable_entity
        )
        return Result.new(success?: false, transaction: nil, error: error)
      end

      Notifications::Enqueuer.enqueue(transaction.id, "refunded")
      Result.new(success?: true, transaction: transaction, error: nil)
    end
  end
end
