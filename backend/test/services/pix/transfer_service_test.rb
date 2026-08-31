require "test_helper"

class Pix::TransferServiceTest < ActiveSupport::TestCase
  setup do
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
      amount: 150.00,
      description: "Payment for services"
    )

    assert result.success?
    assert_not_nil result.transaction
    assert_equal "completed", result.transaction.status
    assert_equal 150.00, result.transaction.amount
    assert_equal "Payment for services", result.transaction.description
    assert_match(/\AE01011010\d{12}[a-z0-9]{11}\z/i, result.transaction.end_to_end_id)
    assert_equal 850.00, @source_account.reload.balance
    assert_equal 350.00, @dest_account.reload.balance
  end

  test "rejects transfer with amount <= 0" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount: 0.00
    )

    assert_not result.success?
    assert_equal "amount must be greater than zero", result.error
    assert_equal 1000.00, @source_account.reload.balance
    assert_equal 200.00, @dest_account.reload.balance
  end

  test "rejects transfer with insufficient funds without modifying balances and records failure" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount: 1500.00
    )

    assert_not result.success?
    assert_equal "insufficient funds", result.error
    assert_equal 1000.00, @source_account.reload.balance
    assert_equal 200.00, @dest_account.reload.balance

    assert_not_nil result.transaction
    assert_equal "failed", result.transaction.status
    assert_equal "insufficient funds", result.transaction.failure_reason
  end

  test "rejects transfer from blocked source account" do
    @source_account.update!(status: "blocked")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount: 100.00
    )

    assert_not result.success?
    assert_equal "source account is blocked", result.error
    assert_equal 1000.00, @source_account.reload.balance
  end

  test "rejects transfer from closed source account" do
    @source_account.update!(balance: 0, status: "closed")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount: 100.00
    )

    assert_not result.success?
    assert_equal "source account is closed", result.error
  end

  test "allows transfer to blocked destination account per BACEN compliance" do
    @dest_account.update!(status: "blocked")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount: 100.00
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
      amount: 100.00
    )

    assert_not result.success?
    assert_equal "destination account is closed", result.error
    assert_equal 1000.00, @source_account.reload.balance
  end

  test "rejects transfer when PIX key is suspended or cancelled" do
    @pix_key.update!(status: "suspended")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_svc@example.com",
      amount: 100.00
    )

    assert_not result.success?
    assert_equal "PIX key is not active", result.error
  end

  test "rejects self-transfer" do
    own_key = PixKey.create!(account: @source_account, key_type: "email", key_value: "sender_svc@example.com")

    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "sender_svc@example.com",
      amount: 50.00
    )

    assert_not result.success?
    assert_equal "self-transfers are not allowed", result.error
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
            amount: 80.00
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
end
