require "test_helper"

class TransactionEventTest < ActiveSupport::TestCase
  setup do
    sender_user = User.create!(name: "Sender Event User", email: "sender_event@example.com", doc_id: "52998224725")
    receiver_user = User.create!(name: "Receiver Event User", email: "receiver_event@example.com", doc_id: "11444777000161")
    @source_account = Account.create!(user: sender_user, balance: 500.00, status: "active")
    @destination_account = Account.create!(user: receiver_user, balance: 100.00, status: "active")
    @transaction = Transaction.create!(
      end_to_end_id: "E01011010202608311500abcdefghijk",
      source_account: @source_account,
      destination_account: @destination_account,
      pix_key_used: "receiver_event@example.com",
      amount: 50.00,
      status: :processing
    )
  end

  test "valid transaction event is valid" do
    assert build_event.valid?
  end

  test "requires audit fields" do
    event = TransactionEvent.new

    assert_not event.valid?
    assert_includes event.errors[:transaction_record], "can't be blank"
    assert_includes event.errors[:current_status], "can't be blank"
    assert_includes event.errors[:amount], "can't be blank"
    assert_includes event.errors[:source_account], "can't be blank"
    assert_includes event.errors[:destination_account], "can't be blank"
  end

  test "cannot be updated after creation" do
    event = build_event
    event.save!

    assert_raises(ActiveRecord::ReadOnlyRecord) { event.update!(reason: "changed") }
  end

  test "cannot be destroyed after creation" do
    event = build_event
    event.save!

    assert_raises(ActiveRecord::ReadOnlyRecord) { event.destroy! }
  end

  private

  def build_event
    TransactionEvent.new(
      transaction_record: @transaction,
      previous_status: nil,
      current_status: "processing",
      amount: @transaction.amount,
      source_account: @source_account,
      destination_account: @destination_account,
      reason: "Transfer created",
      created_at: Time.current
    )
  end
end