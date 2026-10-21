module Pix
  class CancelTransferService
    Result = Struct.new(:success?, :transaction, :error, keyword_init: true)

    def self.call(...)
      new(...).call
    end

    def initialize(transaction_id:, reason: nil)
      @transaction_id = transaction_id
      @reason = reason.presence || "Cancellation requested"
    end

    def call
      transaction = Transaction.find_by(id: @transaction_id)
      unless transaction
        error = DomainError.new(code: "transaction.not_found", message: "Transaction not found", http_status: :not_found)
        return Result.new(success?: false, transaction: nil, error: error)
      end

      begin
        ActiveRecord::Base.transaction do
          transaction.lock!

          if transaction.completed?
            raise DomainFailure, DomainError.new(code: "transaction.already_completed", message: "Cannot cancel a completed transaction", http_status: :conflict)
          elsif transaction.cancelled?
            raise DomainFailure, DomainError.new(code: "transaction.already_cancelled", message: "Transaction is already cancelled", http_status: :conflict)
          elsif !transaction.processing?
            raise DomainFailure, DomainError.new(code: "transaction.invalid_state", message: "Only pending/processing transactions can be cancelled", http_status: :conflict)
          end

          source_account = transaction.source_account

          source_account.lock!
          source_account.update!(balance_cents: source_account.balance_cents + transaction.amount_cents)

          transaction.update!(
            status: :cancelled,
            cancelled_at: Time.current
          )

          TransactionEvent.create!(
            transaction_record: transaction,
            previous_status: "processing",
            current_status: "cancelled",
            amount_cents: transaction.amount_cents,
            source_account: transaction.source_account,
            destination_account: transaction.destination_account,
            reason: @reason,
            created_at: Time.current
          )
        end
      rescue DomainFailure => e
        return Result.new(success?: false, transaction: nil, error: e.error)
      rescue StandardError
        error = DomainError.new(code: "transaction.cancel_failed", message: "Transaction could not be cancelled", http_status: :unprocessable_entity)
        return Result.new(success?: false, transaction: nil, error: error)
      end

      Notifications::Enqueuer.enqueue(transaction.id, "cancelled")
      Result.new(success?: true, transaction: transaction, error: nil)
    end
  end
end
