require "test_helper"

class ScheduledTransferTest < ActiveSupport::TestCase
  setup do
    @user = User.new(name: "Alice", email: "scheduled@example.com", doc_id: "68072551061")
    @user.save!(validate: false)
    @account = Account.create!(user: @user, account_number: "sched001", balance: 100)
    @destination = Account.create!(user: @user, account_number: "sched002", balance: 0)
  end

  test "requires a future local date and positive amount" do
    transfer = @account.scheduled_transfers.new(destination_account: @destination,
      pix_key_used: "bob@example.com", amount_cents: 100, scheduled_for: Date.current
    )
    assert_not transfer.valid?
    assert_includes transfer.errors.attribute_names, :scheduled_for

    transfer.scheduled_for = Date.tomorrow
    transfer.amount_cents = 0
    assert_not transfer.valid?
    assert_includes transfer.errors.attribute_names, :amount_cents
  end

  test "creates an execution key and defaults to pending" do
    transfer = @account.scheduled_transfers.create!(destination_account: @destination,
      pix_key_used: "bob@example.com", amount_cents: 100, scheduled_for: Date.tomorrow
    )
    assert_equal "pending", transfer.status
    assert transfer.execution_key.present?
  end
end
