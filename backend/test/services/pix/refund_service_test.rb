require "test_helper"

module Pix
  class RefundServiceTest < ActiveSupport::TestCase
    include ActiveJob::TestHelper

    setup do
      clear_enqueued_jobs
      @user1 = User.new(name: "Sender", email: "sender_refund@example.com", doc_id: "12345678901")
      @user1.save!(validate: false)

      @user2 = User.new(name: "Receiver", email: "receiver_refund@example.com", doc_id: "98765432109")
      @user2.save!(validate: false)

      # In a completed transaction of 100.0:
      # sender's balance was debited (e.g. 400.0 remaining)
      # receiver's balance was credited (e.g. 300.0 total)
      @source = Account.create!(user: @user1, account_number: "77011", balance: 400.0)
      @destination = Account.create!(user: @user2, account_number: "77022", balance: 300.0)

      @transaction = Transaction.create!(
        amount: 100.0,
        source_account: @source,
        destination_account: @destination,
        end_to_end_id: "E01011010202611081000abcdef12345",
        pix_key_used: "receiver_refund@example.com",
        status: :completed
      )
    end

    test "refunds completed transaction, adjusts balances and records audit event" do
      result = RefundService.call(
        transaction_id: @transaction.id,
        reason: "Fraudulent transaction reported"
      )

      assert result.success?, "Expected refund to succeed, but got error: #{result.error&.message}"
      assert_equal "refunded", result.transaction.status
      assert_not_nil result.transaction.refunded_at

      @source.reload
      @destination.reload

      assert_equal 500.0, @source.balance
      assert_equal 200.0, @destination.balance

      assert_enqueued_with(job: TransactionNotificationJob, args: [ @transaction.id, "refunded" ])

      event = @transaction.transaction_events.order(:id).last
      assert_equal "completed", event.previous_status
      assert_equal "refunded", event.current_status
      assert_equal "Fraudulent transaction reported", event.reason
      assert_equal 100.0, event.amount
      assert_equal @source.id, event.source_account_id
      assert_equal @destination.id, event.destination_account_id
    end

    test "fails when reason is missing or blank" do
      result = RefundService.call(transaction_id: @transaction.id, reason: "")

      assert_not result.success?
      assert_equal "refund.missing_reason", result.error.code
      assert_equal :unprocessable_entity, result.error.http_status
    end

    test "fails when transaction is not found" do
      result = RefundService.call(transaction_id: 999_999, reason: "Some reason")

      assert_not result.success?
      assert_equal "transaction.not_found", result.error.code
      assert_equal :not_found, result.error.http_status
    end

    test "fails when transaction is already refunded (rejects double refund)" do
      @transaction.update!(status: :refunded, refunded_at: Time.current)

      result = RefundService.call(transaction_id: @transaction.id, reason: "Duplicate refund attempt")

      assert_not result.success?
      assert_equal "transaction.already_refunded", result.error.code
      assert_equal :conflict, result.error.http_status
    end

    test "fails when transaction is not in completed state" do
      @transaction.update!(status: :processing)

      result = RefundService.call(transaction_id: @transaction.id, reason: "Refund processing tx")

      assert_not result.success?
      assert_equal "transaction.not_completed", result.error.code
      assert_equal :conflict, result.error.http_status
    end

    test "fails when destination account has insufficient funds to refund" do
      @destination.update!(balance: 50.0) # Needs 100.0

      result = RefundService.call(transaction_id: @transaction.id, reason: "Destination has no balance")

      assert_not result.success?
      assert_equal "refund.insufficient_funds", result.error.code
      assert_equal :unprocessable_entity, result.error.http_status

      @source.reload
      @destination.reload
      assert_equal 400.0, @source.balance
      assert_equal 50.0, @destination.balance
      assert_equal "completed", @transaction.reload.status
    end
  end
end
