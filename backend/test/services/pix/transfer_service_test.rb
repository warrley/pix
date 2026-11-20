require "test_helper"

class Pix::TransferServiceTest < ActiveSupport::TestCase
  include ActiveJob::TestHelper

  setup do
    clear_enqueued_jobs
    @sender_user = User.create!(name: "Sender User", email: "sender_svc@example.com", doc_id: "52998224725")
    @receiver_user = User.create!(name: "Receiver User", email: "receiver_svc@example.com", doc_id: "11444777000161")

    @source_account = Account.create!(user: @sender_user, balance: 1000.00, status: "active")
    @dest_account = Account.create!(user: @receiver_user, balance: 200.00, status: "active")

    @pix_key = PixKey.create!(account: @dest_account, key_type: "email", key_value: "receiver_svc@example.com", status: "active")
  end

  test "successful transfer updates balances and creates completed transaction" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 15_000,
      description: "Payment for services"
    )

    assert result.success?
    assert_not_nil result.transaction
    assert_equal "completed", result.transaction.status
    assert_equal 150.00, result.transaction.amount
    assert_equal 15_000, result.transaction.amount_cents
    assert_equal "Payment for services", result.transaction.description
    assert_match(/\AE01011010\d{12}[a-z0-9]{11}\z/i, result.transaction.end_to_end_id)
    assert_equal 850.00, @source_account.reload.balance
    assert_equal 350.00, @dest_account.reload.balance
    assert_enqueued_with(job: TransactionNotificationJob, args: [ result.transaction.id, "completed" ])

    events = result.transaction.transaction_events.order(:id)
    assert_equal 2, events.count
    assert_equal [ nil, "created" ], events.map(&:previous_status)
    assert_equal [ "created", "completed" ], events.map(&:current_status)
    assert_equal [ "Transfer created", "Balance transfer completed" ], events.map(&:reason)
  end

  test "persists location on the transaction and its audit events" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 15_000,
      latitude: "-3.73186210",
      longitude: "-38.52666980"
    )

    assert result.success?
    assert_equal BigDecimal("-3.73186210"), result.transaction.latitude
    assert_equal BigDecimal("-38.52666980"), result.transaction.longitude
    result.transaction.transaction_events.each do |event|
      assert_equal result.transaction.latitude, event.latitude
      assert_equal result.transaction.longitude, event.longitude
    end
  end

  test "rejects partial or out-of-range location" do
    partial = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 15_000,
      latitude: 1
    )
    out_of_range = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 15_000,
      latitude: 91,
      longitude: 0
    )

    assert_not partial.success?
    assert_equal "location.invalid", partial.error.code
    assert_equal :unprocessable_entity, partial.error.http_status
    assert_not out_of_range.success?
    assert_equal "location.invalid", out_of_range.error.code
  end

  test "rejects transfer with amount <= 0" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 0
    )

    assert_not result.success?
    assert_equal "amount.invalid", result.error.code
    assert_equal 1000.00, @source_account.reload.balance
    assert_equal 200.00, @dest_account.reload.balance
  end

  test "returns a typed error for malformed service amounts" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 100.1
    )

    assert_not result.success?
    assert_equal "amount.invalid", result.error.code
    assert_equal :unprocessable_entity, result.error.http_status
  end

  test "rejects transfer with insufficient funds without modifying balances and records failure" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 150_000
    )

    assert_not result.success?
    assert_equal "transfer.insufficient_funds", result.error.code
    assert_equal 1000.00, @source_account.reload.balance
    assert_equal 200.00, @dest_account.reload.balance

    assert_not_nil result.transaction
    assert_equal "failed", result.transaction.status
    assert_equal "Insufficient funds", result.transaction.failure_reason
    assert_enqueued_with(job: TransactionNotificationJob, args: [ result.transaction.id, "failed" ])
  end

  test "rejects transfers that exceed the configured daily limit" do
    @source_account.update!(balance: 6_000.00)

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 500_001
    )

    assert_not result.success?
    assert_equal "transfer.daily_limit_exceeded", result.error.code
    assert_equal 6_000.00, @source_account.reload.balance
  end

  test "allows a transfer that reaches the daily limit exactly" do
    @source_account.update!(balance: 6_000.00)

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 500_000
    )

    assert result.success?
    assert_equal 5_000.00, Pix::DailyTransferLimit.used_cents(account: @source_account.reload) / 100.0
  end

  test "ignores cancelled and failed transfers when calculating the daily limit" do
    @source_account.update!(balance: 6_000.00)

    Transaction.create!(
      source_account: @source_account,
      destination_account: @dest_account,
      end_to_end_id: "E01011010#{SecureRandom.hex(6)}#{SecureRandom.alphanumeric(11).downcase}",
      pix_key_used: @pix_key.key_value,
      amount_cents: 4_000_00,
      status: :cancelled,
      cancelled_at: Time.current
    )
    Transaction.create!(
      source_account: @source_account,
      destination_account: @dest_account,
      end_to_end_id: "E01011010#{SecureRandom.hex(6)}#{SecureRandom.alphanumeric(11).downcase}",
      pix_key_used: @pix_key.key_value,
      amount_cents: 4_000_00,
      status: :failed
    )

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 5_000_00
    )

    assert result.success?
  end

  test "keeps a completed transfer when notification enqueueing fails" do
    result = nil
    with_perform_later_failure do
      result = Pix::TransferService.call(
        source_account_id: @source_account.id,
        pix_key: "receiver_svc@example.com",
        amount_cents: 15_000
      )
    end

    assert result.success?
    assert_equal "completed", result.transaction.status
    assert_equal 850.00, @source_account.reload.balance
    assert_equal 350.00, @dest_account.reload.balance
  end

  test "keeps a completed transfer when metrics recording fails" do
    result = nil
    with_metrics_failure do
      result = Pix::TransferService.call(
        source_account_id: @source_account.id,
        pix_key: "receiver_svc@example.com",
        amount_cents: 15_000
      )
    end

    assert result.success?
    assert_equal "completed", result.transaction.status
    assert_equal 850.00, @source_account.reload.balance
    assert_equal 350.00, @dest_account.reload.balance
  end

  test "rejects transfer from blocked source account" do
    @source_account.update!(status: "blocked")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 10_000
    )

    assert_not result.success?
    assert_equal "account.blocked", result.error.code
    assert_equal 1000.00, @source_account.reload.balance
  end

  test "rejects transfer from closed source account" do
    @source_account.update!(balance: 0, status: "closed")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 10_000
    )

    assert_not result.success?
    assert_equal "account.closed", result.error.code
  end

  test "allows transfer to blocked destination account per BACEN compliance" do
    @dest_account.update!(status: "blocked")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 10_000
    )

    assert result.success?
    assert_equal 900.00, @source_account.reload.balance
    assert_equal 300.00, @dest_account.reload.balance
  end

  test "rejects transfer to closed destination account" do
    @dest_account.update!(balance: 0, status: "closed")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 10_000
    )

    assert_not result.success?
    assert_equal "account.closed", result.error.code
    assert_equal 1000.00, @source_account.reload.balance
  end

  test "rejects transfer when PIX key is suspended or cancelled" do
    @pix_key.update!(status: "suspended")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 10_000
    )

    assert_not result.success?
    assert_equal "pix_key.not_active", result.error.code
  end

  test "rejects self-transfer" do
    own_key = PixKey.create!(account: @source_account, key_type: "email", key_value: "sender_svc@example.com")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "sender_svc@example.com",
      amount_cents: 5_000
    )

    assert_not result.success?
    assert_equal "transfer.self_transfer", result.error.code
    assert_equal 1000.00, @source_account.reload.balance
  end

  test "concurrent transfers do not double spend (race condition safety)" do
    @source_account.update!(balance: 100.00)
    threads = []
    results = []

    2.times do
      threads << Thread.new do
        ActiveRecord::Base.connection_pool.with_connection do
          results << Pix::TransferService.call(
            source_account_id: @source_account.id,
            pix_key: "receiver_svc@example.com",
            amount_cents: 8_000
          )
        end
      end
    end
    threads.each(&:join)

    successes = results.count(&:success?)
    failures = results.count { |r| !r.success? }

    assert_equal 1, successes
    assert_equal 1, failures
    assert_equal 20.00, @source_account.reload.balance
    assert_equal 280.00, @dest_account.reload.balance
  end

  test "fraud_blocked defaults to false" do
    account = Account.create!(user: @sender_user, balance: 100.00, status: "active")
    assert_equal false, account.fraud_blocked
    assert_nil account.fraud_blocked_at
    assert_nil account.fraud_block_reason
  end

  test "rejects outgoing transfer from fraud-blocked source account" do
    @source_account.update!(fraud_blocked: true, fraud_blocked_at: Time.current, fraud_block_reason: "Suspected phishing")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 10_000
    )

    assert_not result.success?
    assert_equal "account.fraud_blocked", result.error.code
    assert_equal 1000.00, @source_account.reload.balance
  end

  test "rejects incoming transfer to fraud-blocked destination account" do
    @dest_account.update!(fraud_blocked: true, fraud_blocked_at: Time.current, fraud_block_reason: "Money laundering risk")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount_cents: 10_000
    )

    assert_not result.success?
    assert_equal "account.fraud_blocked", result.error.code
    assert_equal 1000.00, @source_account.reload.balance
    assert_equal 200.00, @dest_account.reload.balance
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

  def with_metrics_failure
    original = Observability::Metrics.method(:record_transfer)
    Observability::Metrics.define_singleton_method(:record_transfer) do |*|
      raise Errno::ENOSPC, "No space left on device"
    end
    yield
  ensure
    Observability::Metrics.define_singleton_method(:record_transfer) do |*args, **kwargs|
      original.call(*args, **kwargs)
    end
  end
end
