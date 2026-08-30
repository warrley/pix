require "test_helper"

class AccountTest < ActiveSupport::TestCase
  setup do
    @user = User.create!(name: "Alice", email: "alice@example.com", doc_id: "52998224725")
  end

  test "creates an account with default balance, status and generated number" do
    account = @user.accounts.create!

    assert_equal "0001", account.agency_number
    assert_equal 0.0, account.balance.to_f
    assert_equal "active", account.status
    assert_match(/\A\d{6}\z/, account.account_number)
  end

  test "requires a valid user" do
    account = Account.new(agency_number: "0001", balance: 0.0, status: "active")

    assert_not account.valid?
    assert_includes account.errors[:user], "must exist"
  end

  test "rejects a negative balance" do
    account = @user.accounts.build(balance: -1.00)

    assert_not account.valid?
    assert_includes account.errors[:balance], "must be greater than or equal to 0"
  end

  test "rejects an invalid status" do
    account = @user.accounts.build(status: "pending")

    assert_not account.valid?
    assert_includes account.errors[:status], "is not included in the list"
  end

  test "generates unique six-digit account numbers" do
    first_account = @user.accounts.create!
    second_account = @user.accounts.create!

    assert_not_equal first_account.account_number, second_account.account_number
    assert_match(/\A\d{6}\z/, first_account.account_number)
    assert_match(/\A\d{6}\z/, second_account.account_number)
  end
end
