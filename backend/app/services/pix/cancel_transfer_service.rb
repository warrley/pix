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
      return Result.new(success?: false, transaction: nil, error: "Transaction not found") unless transaction

      begin
        ActiveRecord::Base.transaction do
          transaction.lock!

          if transaction.completed?
            raise StandardError, "Cannot cancel a completed transaction"
          elsif transaction.cancelled?
            raise StandardError, "Transaction is already cancelled"
          elsif !transaction.processing?
            raise StandardError, "Only pending/processing transactions can be cancelled"
          end

          source_account = transaction.source_account

          source_account.lock!
          source_account.update!(balance: source_account.balance + transaction.amount)

          transaction.update!(
            status: :cancelled,
            cancelled_at: Time.current
          )

          TransactionEvent.create!(
            transaction_record: transaction,
            previous_status: "processing",
            current_status: "cancelled",
            amount: transaction.amount,
            source_account: transaction.source_account,
            destination_account: transaction.destination_account,
            reason: @reason,
            created_at: Time.current
          )
        end
      rescue StandardError => e
        return Result.new(success?: false, transaction: nil, error: e.message)
      end

      Notifications::Enqueuer.enqueue(transaction.id, "cancelled")
      Result.new(success?: true, transaction: transaction, error: nil)
    end
  end
end
