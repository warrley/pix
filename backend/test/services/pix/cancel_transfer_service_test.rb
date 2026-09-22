require "test_helper"

module Pix
  class CancelTransferServiceTest < ActiveSupport::TestCase
    include ActiveJob::TestHelper

    setup do
      clear_enqueued_jobs
      @user1 = User.new(name: "Sender", email: "sender_test@example.com", doc_id: "12345678901")
      @user1.save!(validate: false)

      @user2 = User.new(name: "Receiver", email: "receiver_test@example.com", doc_id: "98765432109")
      @user2.save!(validate: false)

      # O saldo já está debitado (400.0) pois uma transação em :processing
      # significa que o valor foi reservado/debitado da origem.
      @source = Account.create!(user: @user1, account_number: "00011", balance: 400.0)
      @destination = Account.create!(user: @user2, account_number: "00022", balance: 100.0)

      @transaction = Transaction.create!(
        amount: 100.0,
        source_account: @source,
        destination_account: @destination,
        end_to_end_id: "E01011010202609211000abcde123456",
        pix_key_used: "receiver@example.com",
        status: :processing
      )
    end

    test "cancels pending transaction and restores source account balance" do
      result = CancelTransferService.call(transaction_id: @transaction.id)

      assert result.success?
      assert_equal "cancelled", result.transaction.status
      assert_not_nil result.transaction.cancelled_at

      @source.reload
      assert_equal 500.0, @source.balance
      assert_enqueued_with(job: TransactionNotificationJob, args: [ @transaction.id, "cancelled" ])
    end

    test "raises error when attempting to cancel completed transaction" do
      @transaction.update!(status: :completed)

      result = CancelTransferService.call(transaction_id: @transaction.id)

      assert_not result.success?
      assert_equal "Cannot cancel a completed transaction", result.error

      @source.reload
      assert_equal 400.0, @source.balance
      assert_enqueued_jobs 0
    end

    test "raises error when attempting to cancel already cancelled transaction" do
      @transaction.update!(status: :cancelled, cancelled_at: Time.current)

      result = CancelTransferService.call(transaction_id: @transaction.id)

      assert_not result.success?
      assert_equal "Transaction is already cancelled", result.error

      @source.reload
      assert_equal 400.0, @source.balance
      assert_enqueued_jobs 0
    end

    test "keeps the cancellation when notification enqueueing fails" do
      result = nil
      with_perform_later_failure do
        result = CancelTransferService.call(transaction_id: @transaction.id)
      end

      assert result.success?
      assert_equal "cancelled", result.transaction.status
      assert_not_nil result.transaction.cancelled_at
      assert_equal 500.0, @source.reload.balance
    end

    test "fails if transaction does not exist" do
      result = CancelTransferService.call(transaction_id: -1)

      assert_not result.success?
      assert_equal "Transaction not found", result.error
    end

    private

    def with_perform_later_failure
      original = TransactionNotificationJob.method(:perform_later)
      TransactionNotificationJob.define_singleton_method(:perform_later) do |*|
        raise "queue unavailable"
      end
      yield
    ensure
      TransactionNotificationJob.define_singleton_method(:perform_later) do |*args, **kwargs|
        original.call(*args, **kwargs)
      end
    end
  end
end
