require "test_helper"

module Pix
  class CancelTransferServiceTest < ActiveSupport::TestCase
    setup do
      @user1 = User.new(name: "Sender", email: "sender_test@example.com", doc_id: "12345678901")
      @user1.save!(validate: false)
      
      @user2 = User.new(name: "Receiver", email: "receiver_test@example.com", doc_id: "98765432109")
      @user2.save!(validate: false)
      
      @source = Account.create!(user: @user1, account_number: "00011", balance: 500.0)
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
      assert_equal 600.0, @source.balance
    end

    test "raises error when attempting to cancel completed transaction" do
      @transaction.update!(status: :completed)

      result = CancelTransferService.call(transaction_id: @transaction.id)

      assert_not result.success?
      assert_equal "Cannot cancel a completed transaction", result.error
      
      @source.reload
      assert_equal 500.0, @source.balance
    end

    test "raises error when attempting to cancel already cancelled transaction" do
      @transaction.update!(status: :cancelled, cancelled_at: Time.current)

      result = CancelTransferService.call(transaction_id: @transaction.id)

      assert_not result.success?
      assert_equal "Transaction is already cancelled", result.error
      
      @source.reload
      assert_equal 500.0, @source.balance
    end

    test "fails if transaction does not exist" do
      result = CancelTransferService.call(transaction_id: -1)

      assert_not result.success?
      assert_equal "Transaction not found", result.error
    end
  end
end
