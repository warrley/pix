require "test_helper"

class TransactionNotificationJobTest < ActiveSupport::TestCase
  include ActiveJob::TestHelper

  setup do
    clear_enqueued_jobs
    @source_user = User.create!(
      name: "Notification Sender",
      email: "notification_sender@example.com",
      doc_id: "52998224725",
      phone: "+5585999999999"
    )
    @destination_user = User.create!(
      name: "Notification Receiver",
      email: "notification_receiver@example.com",
      doc_id: "11444777000161",
      phone: "+5585888888888"
    )
    @source_account = Account.create!(user: @source_user, balance: 900.00)
    @destination_account = Account.create!(user: @destination_user, balance: 100.00)
    @transaction = Transaction.create!(
      amount: 100.00,
      source_account: @source_account,
      destination_account: @destination_account,
      end_to_end_id: "E01011010202609211000abcde123456",
      pix_key_used: "notification_receiver@example.com",
      status: :completed
    )
  end

  test "uses the default queue" do
    assert_equal "default", TransactionNotificationJob.queue_name
  end

  test "enqueues only scalar transaction arguments" do
    assert_enqueued_with(
      job: TransactionNotificationJob,
      args: [ @transaction.id, "completed" ]
    ) do
      TransactionNotificationJob.perform_later(@transaction.id, "completed")
    end
  end

  test "dispatches completed notifications to both users with inverted counterparties" do
    calls = []
    notifier = Object.new
    notifier.define_singleton_method(:notify) do |user, payload|
      calls << [ user, payload ]
      true
    end

    with_method_stub(TransactionNotificationJob, :notifiers, -> { [ notifier ] }) do
      TransactionNotificationJob.perform_now(@transaction.id, "completed")
    end

    assert_equal [ @source_user.id, @destination_user.id ], calls.map { |user, _| user.id }
    assert_equal @destination_user.name, calls[0][1][:counterparty_name]
    assert_equal @source_user.name, calls[1][1][:counterparty_name]
    calls.each do |_, payload|
      assert_equal "completed", payload[:event_type]
      assert_equal "100.00", payload[:amount]
      assert_equal @transaction.end_to_end_id, payload[:end_to_end_id]
      assert_equal @transaction.created_at.utc.iso8601, payload[:timestamp]
    end
  end

  test "notifies only the source user about a failed transaction" do
    failed_transaction = @transaction.tap do |transaction|
      transaction.update!(status: :failed, failure_reason: "insufficient funds")
    end
    calls = capture_notification_calls

    with_method_stub(TransactionNotificationJob, :notifiers, -> { [ calls[:notifier] ] }) do
      TransactionNotificationJob.perform_now(failed_transaction.id, "failed")
    end

    assert_equal [ @source_user.id ], calls[:calls].map { |user, _| user.id }
    assert_equal "insufficient funds", calls[:calls].first[1][:failure_reason]
    assert_equal @destination_user.name, calls[:calls].first[1][:counterparty_name]
  end

  test "notifies only the source user about a cancellation using cancelled_at" do
    cancelled_at = Time.zone.parse("2026-09-21 12:00:00 UTC")
    cancelled_transaction = @transaction.tap do |transaction|
      transaction.update!(status: :cancelled, cancelled_at: cancelled_at)
    end
    calls = capture_notification_calls

    with_method_stub(TransactionNotificationJob, :notifiers, -> { [ calls[:notifier] ] }) do
      TransactionNotificationJob.perform_now(cancelled_transaction.id, "cancelled")
    end

    assert_equal [ @source_user.id ], calls[:calls].map { |user, _| user.id }
    assert_equal cancelled_at.utc.iso8601, calls[:calls].first[1][:timestamp]
    refute calls[:calls].first[1].key?(:failure_reason)
  end

  test "discards notifications for missing transactions" do
    assert_nothing_raised do
      TransactionNotificationJob.perform_now(-1, "completed")
    end
  end

  test "rejects unsupported events without dispatching" do
    assert_raises(ArgumentError) do
      TransactionNotificationJob.perform_now(@transaction.id, "unknown")
    end
  end

  test "tries every delivery and raises an aggregate delivery error" do
    calls = []
    failing_notifier = Object.new
    failing_notifier.define_singleton_method(:notify) do |user, _payload|
      calls << user.id
      false
    end
    succeeding_notifier = Object.new
    succeeding_notifier.define_singleton_method(:notify) do |user, _payload|
      calls << user.id
      true
    end

    assert_raises(Notifications::DeliveryError) do
      with_method_stub(TransactionNotificationJob, :notifiers, -> { [ failing_notifier, succeeding_notifier ] }) do
        TransactionNotificationJob.new.perform(@transaction.id, "completed")
      end
    end

    assert_equal [ @source_user.id, @source_user.id, @destination_user.id, @destination_user.id ], calls
  end

  private

  def capture_notification_calls
    calls = []
    notifier = Object.new
    notifier.define_singleton_method(:notify) do |user, payload|
      calls << [ user, payload ]
      true
    end
    { calls: calls, notifier: notifier }
  end

  def with_method_stub(object, method_name, callable)
    original = object.method(method_name)
    object.define_singleton_method(method_name) do |*args, **kwargs, &block|
      callable.call(*args, **kwargs, &block)
    end
    yield
  ensure
    object.define_singleton_method(method_name) do |*args, **kwargs, &block|
      original.call(*args, **kwargs, &block)
    end
  end
end
