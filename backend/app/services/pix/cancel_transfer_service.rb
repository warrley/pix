module Pix
  class CancelTransferService
    Result = Struct.new(:success?, :transaction, :error, keyword_init: true)

    def self.call(...)
      new(...).call
    end

    def initialize(transaction_id:)
      @transaction_id = transaction_id
    end

    def call
      transaction = Transaction.find_by(id: @transaction_id)
      return Result.new(success?: false, transaction: nil, error: "Transaction not found") unless transaction

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
      end

      Result.new(success?: true, transaction: transaction, error: nil)
    rescue StandardError => e
      Result.new(success?: false, transaction: nil, error: e.message)
    end
  end
end
