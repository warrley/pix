require "test_helper"

class ProcessScheduledTransfersJobTest < ActiveJob::TestCase
  setup do
    @sender = User.new(name: "Alice", email: "scheduled-job-sender@example.com", doc_id: "68072551061")
    @sender.save!(validate: false)
    @source = Account.create!(user: @sender, account_number: "schedjob1", balance: 100)
    receiver = User.new(name: "Bob", email: "scheduled-job-receiver@example.com", doc_id: "33734063071")
    receiver.save!(validate: false)
    @destination = Account.create!(user: receiver, account_number: "schedjob2", balance: 0)
    PixKey.create!(account: @destination, key_type: :email, key_value: receiver.email)
  end

  test "executes due transfers and links the completed transaction" do
    transfer = @source.scheduled_transfers.create!(destination_account: @destination, pix_key_used: @destination.user.email, amount_cents: 500, scheduled_for: Date.tomorrow)
    travel_to transfer.scheduled_for.in_time_zone.beginning_of_day do
      ProcessScheduledTransfersJob.perform_now
    end

    transfer.reload
    assert_equal "executed", transfer.status
    assert_equal "completed", transfer.completed_transaction.status
    assert_equal 9_500, @source.reload.balance_cents
    assert_equal 500, @destination.reload.balance_cents
  end

  test "leaves future transfers pending" do
    transfer = @source.scheduled_transfers.create!(destination_account: @destination, pix_key_used: @destination.user.email, amount_cents: 500, scheduled_for: Date.tomorrow)
    ProcessScheduledTransfersJob.perform_now
    assert_equal "pending", transfer.reload.status
  end

  test "retries a transient service failure without recording a failed transaction" do
    transfer = @source.scheduled_transfers.create!(destination_account: @destination, pix_key_used: @destination.user.email, amount_cents: 500, scheduled_for: Date.tomorrow)
    error = Pix::DomainError.new(code: "transfer.failed", message: "Temporary failure", http_status: :unprocessable_entity)
    transient_result = Pix::TransferService::Result.new(success?: false, transaction: nil, error: error)

    travel_to transfer.scheduled_for.in_time_zone.beginning_of_day do
      original_call = Pix::TransferService.method(:call)
      Pix::TransferService.define_singleton_method(:call) { |**| transient_result }
      begin
        ProcessScheduledTransfersJob.perform_now
      ensure
        Pix::TransferService.define_singleton_method(:call, original_call)
      end
      assert_equal "pending", transfer.reload.status
      assert_nil Transaction.find_by(end_to_end_id: transfer.execution_key)

      ProcessScheduledTransfersJob.perform_now
    end

    assert_equal "executed", transfer.reload.status
    assert_equal 9_500, @source.reload.balance_cents
  end

  test "recovers a completed transaction from an expired claim" do
    transfer = @source.scheduled_transfers.create!(destination_account: @destination, pix_key_used: @destination.user.email, amount_cents: 500, scheduled_for: Date.tomorrow)
    travel_to transfer.scheduled_for.in_time_zone.beginning_of_day do
      transfer.update!(status: :processing, processing_started_at: 16.minutes.ago)
      result = Pix::TransferService.call(
        source_account_id: @source.id, pix_key: @destination.user.email,
        amount_cents: 500, end_to_end_id: transfer.execution_key
      )
      assert result.success?

      ProcessScheduledTransfersJob.perform_now
    end

    assert_equal "executed", transfer.reload.status
    assert_equal 1, Transaction.where(end_to_end_id: transfer.execution_key).count
    assert_equal 9_500, @source.reload.balance_cents
  end

  test "does not send to a new owner of the same pix key" do
    transfer = @source.scheduled_transfers.create!(destination_account: @destination, pix_key_used: @destination.user.email, amount_cents: 500, scheduled_for: Date.tomorrow)
    @destination.pix_keys.first.update!(status: :cancelled)
    new_owner = User.new(name: "Carol", email: "scheduled-job-carol@example.com", doc_id: "52998224725")
    new_owner.save!(validate: false)
    new_account = Account.create!(user: new_owner, account_number: "schedjob3", balance: 0)
    PixKey.create!(account: new_account, key_type: :email, key_value: @destination.user.email)

    travel_to transfer.scheduled_for.in_time_zone.beginning_of_day do
      ProcessScheduledTransfersJob.perform_now
    end

    assert_equal "failed", transfer.reload.status
    assert_equal 10_000, @source.reload.balance_cents
    assert_equal 0, new_account.reload.balance_cents
  end

  test "continues processing after one scheduled transfer raises" do
    first = @source.scheduled_transfers.create!(destination_account: @destination, pix_key_used: @destination.user.email, amount_cents: 500, scheduled_for: Date.tomorrow)
    second = @source.scheduled_transfers.create!(destination_account: @destination, pix_key_used: @destination.user.email, amount_cents: 500, scheduled_for: Date.tomorrow)
    original_call = Pix::TransferService.method(:call)
    Pix::TransferService.define_singleton_method(:call) do |**arguments|
      raise "temporary failure" if arguments[:end_to_end_id] == first.execution_key

      original_call.call(**arguments)
    end
    begin
      travel_to first.scheduled_for.in_time_zone.beginning_of_day do
        ProcessScheduledTransfersJob.perform_now
      end
    ensure
      Pix::TransferService.define_singleton_method(:call, original_call)
    end

    assert_equal "pending", first.reload.status
    assert_equal "executed", second.reload.status
    assert_equal 9_500, @source.reload.balance_cents
  end

  test "rolls back a debit if a later transfer event raises" do
    transfer = @source.scheduled_transfers.create!(destination_account: @destination, pix_key_used: @destination.user.email, amount_cents: 500, scheduled_for: Date.tomorrow)
    original_create = TransactionEvent.method(:create!)
    calls = 0
    TransactionEvent.define_singleton_method(:create!) do |**attributes|
      calls += 1
      raise "event unavailable" if calls == 2

      original_create.call(**attributes)
    end
    begin
      travel_to transfer.scheduled_for.in_time_zone.beginning_of_day do
        ProcessScheduledTransfersJob.perform_now
      end
    ensure
      TransactionEvent.define_singleton_method(:create!, original_create)
    end

    assert_equal "pending", transfer.reload.status
    assert_equal 10_000, @source.reload.balance_cents
    assert_equal 0, @destination.reload.balance_cents
    assert_nil Transaction.find_by(end_to_end_id: transfer.execution_key)
  end
end
