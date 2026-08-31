require "test_helper"

class TransactionTest < ActiveSupport::TestCase
  setup do
    @sender_user = User.create!(name: "Sender User", email: "sender_tx@example.com", doc_id: "52998224725")
    @receiver_user = User.create!(name: "Receiver User", email: "receiver_tx@example.com", doc_id: "11444777000161")

    @source_account = Account.create!(user: @sender_user, balance: 500.00, status: "active")
    @destination_account = Account.create!(user: @receiver_user, balance: 100.00, status: "active")
  end

  test "valid transaction is valid" do
    tx = Transaction.new(
      end_to_end_id: "E01011010202608311500abcdefghijk",
      source_account: @source_account,
      destination_account: @destination_account,
      pix_key_used: "receiver_tx@example.com",
      amount: 50.00,
      description: "Dinner",
      status: :completed
    )

    assert tx.valid?
  end

  test "requires end_to_end_id with length 32" do
    tx = Transaction.new(
      end_to_end_id: "short_id",
      source_account: @source_account,
      destination_account: @destination_account,
      pix_key_used: "receiver_tx@example.com",
      amount: 50.00
    )

    assert_not tx.valid?
    assert_includes tx.errors[:end_to_end_id], "is the wrong length (should be 32 characters)"
  end

  test "enforces unique end_to_end_id" do
    e2e_id = "E01011010202608311500abcdefghijk"
    Transaction.create!(
      end_to_end_id: e2e_id,
      source_account: @source_account,
      destination_account: @destination_account,
      pix_key_used: "receiver_tx@example.com",
      amount: 50.00,
      status: :completed
    )

    duplicate = Transaction.new(
      end_to_end_id: e2e_id,
      source_account: @source_account,
      destination_account: @destination_account,
      pix_key_used: "receiver_tx@example.com",
      amount: 25.00
    )

    assert_not duplicate.valid?
    assert_includes duplicate.errors[:end_to_end_id], "has already been taken"
  end

  test "requires amount to be greater than zero" do
    tx = Transaction.new(
      end_to_end_id: "E01011010202608311500abcdefghijk",
      source_account: @source_account,
      destination_account: @destination_account,
      pix_key_used: "receiver_tx@example.com",
      amount: 0.00
    )

    assert_not tx.valid?
    assert_includes tx.errors[:amount], "must be greater than 0"
  end

  test "prevents self-transfers" do
    tx = Transaction.new(
      end_to_end_id: "E01011010202608311500abcdefghijk",
      source_account: @source_account,
      destination_account: @source_account,
      pix_key_used: "sender_tx@example.com",
      amount: 50.00
    )

    assert_not tx.valid?
    assert_includes tx.errors[:destination_account_id], "cannot be the same as source account"
  end
end
