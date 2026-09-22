require "test_helper"
require "csv"

class Reports::UserTransactionsServiceTest < ActiveSupport::TestCase
  setup do
    @user = User.create!(name: "Report Owner", email: "report.owner@example.com", doc_id: "52998224725")
    @account_one = Account.create!(user: @user, status: :active)
    @account_two = Account.create!(user: @user, status: :blocked)

    @external_user = User.create!(name: "External User", email: "report.external@example.com", doc_id: "11444777000161")
    @external_account = Account.create!(user: @external_user, status: :active)
  end

  test "consolidates completed transactions from all user accounts" do
    create_transaction(@account_one, @external_account, "100.00")
    create_transaction(@external_account, @account_two, "40.00")
    create_transaction(@account_two, @external_account, "60.00")
    create_transaction(@account_one, @account_two, "25.00")

    report = Reports::UserTransactionsService.call(user_id: @user.id)

    assert_equal @user.id, report[:user_id]
    assert_equal 4, report.dig(:summary, :total_transactions_count)
    assert_equal 185.0, report.dig(:summary, :total_sent_amount)
    assert_equal 65.0, report.dig(:summary, :total_received_amount)
    assert_equal(-120.0, report.dig(:summary, :net_balance_change))
    assert_equal 56.25, report.dig(:summary, :average_ticket_amount)

    assert_equal [ @account_one.id, @account_two.id ], report[:accounts].pluck(:account_id)
    account_one_report = report[:accounts].first
    assert_equal 2, account_one_report[:total_transactions_count]
    assert_equal 125.0, account_one_report[:total_sent_amount]
    assert_equal 0.0, account_one_report[:total_received_amount]
    assert_equal(-125.0, account_one_report[:net_balance_change])
    assert_equal 62.5, account_one_report[:average_ticket_amount]

    account_two_report = report[:accounts].second
    assert_equal 3, account_two_report[:total_transactions_count]
    assert_equal 60.0, account_two_report[:total_sent_amount]
    assert_equal 65.0, account_two_report[:total_received_amount]
    assert_equal 5.0, account_two_report[:net_balance_change]
    assert_in_delta 41.666666, account_two_report[:average_ticket_amount], 0.000001
  end

  test "does not include transactions belonging only to another user" do
    create_transaction(@account_one, @external_account, "100.00")
    other_user = User.create!(name: "Another User", email: "report.another@example.com", doc_id: "39053344705")
    other_account = Account.create!(user: other_user, status: :active)
    create_transaction(other_account, @external_account, "900.00")

    report = Reports::UserTransactionsService.call(user_id: @user.id)

    assert_equal 1, report.dig(:summary, :total_transactions_count)
    assert_equal 100.0, report.dig(:summary, :total_sent_amount)
    assert_equal [ @account_one.id, @account_two.id ], report[:accounts].pluck(:account_id)
  end

  test "only completed transactions affect the report" do
    create_transaction(@account_one, @external_account, "10.00", status: :completed)
    create_transaction(@account_one, @external_account, "20.00", status: :processing)
    create_transaction(@external_account, @account_two, "30.00", status: :failed)
    create_transaction(@account_two, @external_account, "40.00", status: :cancelled)

    report = Reports::UserTransactionsService.call(user_id: @user.id)

    assert_equal 1, report.dig(:summary, :total_transactions_count)
    assert_equal 10.0, report.dig(:summary, :total_sent_amount)
    assert_equal 0.0, report.dig(:summary, :total_received_amount)
    assert_equal(-10.0, report.dig(:summary, :net_balance_change))
    assert_equal 10.0, report.dig(:summary, :average_ticket_amount)
  end

  test "returns zero metrics while preserving accounts without completed transactions" do
    report = Reports::UserTransactionsService.call(user_id: @user.id)

    assert_equal 0, report.dig(:summary, :total_transactions_count)
    assert_equal 0.0, report.dig(:summary, :total_sent_amount)
    assert_equal 0.0, report.dig(:summary, :total_received_amount)
    assert_equal 0.0, report.dig(:summary, :net_balance_change)
    assert_equal 0.0, report.dig(:summary, :average_ticket_amount)
    assert_equal 2, report[:accounts].size
    report[:accounts].each do |account_report|
      assert_equal 0, account_report[:total_transactions_count]
      assert_equal 0.0, account_report[:total_sent_amount]
      assert_equal 0.0, account_report[:total_received_amount]
      assert_equal 0.0, account_report[:net_balance_change]
      assert_equal 0.0, account_report[:average_ticket_amount]
    end
  end

  test "returns a zeroed report for a user without accounts" do
    user_without_accounts = User.create!(name: "No Accounts", email: "report.empty@example.com", doc_id: "11144477735")

    report = Reports::UserTransactionsService.call(user_id: user_without_accounts.id)

    assert_equal user_without_accounts.id, report[:user_id]
    assert_empty report[:accounts]
    assert_equal 0, report.dig(:summary, :total_transactions_count)
    assert_equal 0.0, report.dig(:summary, :total_sent_amount)
    assert_equal 0.0, report.dig(:summary, :total_received_amount)
    assert_equal 0.0, report.dig(:summary, :net_balance_change)
    assert_equal 0.0, report.dig(:summary, :average_ticket_amount)
  end

  test "raises when the user does not exist" do
    assert_raises(ActiveRecord::RecordNotFound) do
      Reports::UserTransactionsService.call(user_id: -1)
    end
  end

  test "calculates a fractional average from completed transactions" do
    create_transaction(@account_one, @external_account, "10.00")
    create_transaction(@external_account, @account_two, "20.00")
    create_transaction(@account_one, @account_two, "25.00")

    report = Reports::UserTransactionsService.call(user_id: @user.id)

    assert_equal 3, report.dig(:summary, :total_transactions_count)
    assert_in_delta 18.333333, report.dig(:summary, :average_ticket_amount), 0.000001
  end

  test "generates a parseable csv with account rows and a summary row" do
    create_transaction(@account_one, @external_account, "100.00")
    create_transaction(@external_account, @account_two, "40.00")

    report = Reports::UserTransactionsService.call(user_id: @user.id)
    table = CSV.parse(Reports::UserTransactionsService.to_csv(report), headers: true)

    assert_equal %w[row_type user_id account_id account_number agency_number status total_transactions_count total_sent_amount total_received_amount net_balance_change average_ticket_amount], table.headers
    assert_equal [ "account", "account", "summary" ], table.map { |row| row["row_type"] }
    assert_equal @account_one.account_number, table[0]["account_number"]
    assert_equal "100.00", table[0]["total_sent_amount"]
    assert_equal "0.00", table[0]["total_received_amount"]
    assert_equal "40.00", table[1]["net_balance_change"]
    assert_equal "2", table[2]["total_transactions_count"]
    assert_equal "100.00", table[2]["total_sent_amount"]
    assert_equal "40.00", table[2]["total_received_amount"]
    assert_equal "70.00", table[2]["average_ticket_amount"]
  end

  test "generates a summary csv row for a user without accounts" do
    user_without_accounts = User.create!(name: "CSV Empty", email: "report.csv.empty@example.com", doc_id: "12345678909")
    report = Reports::UserTransactionsService.call(user_id: user_without_accounts.id)

    table = CSV.parse(Reports::UserTransactionsService.to_csv(report), headers: true)
    row = table.first

    assert_equal "summary", row["row_type"]
    assert_equal user_without_accounts.id.to_s, row["user_id"]
    assert_nil row["account_id"]
    assert_equal "0", row["total_transactions_count"]
    assert_equal "0.00", row["total_sent_amount"]
    assert_equal "0.00", row["average_ticket_amount"]
  end

  private

  def create_transaction(source_account, destination_account, amount, status: :completed)
    @transaction_sequence ||= 0
    @transaction_sequence += 1

    Transaction.create!(
      source_account: source_account,
      destination_account: destination_account,
      amount: amount,
      status: status,
      end_to_end_id: format("E%031d", @transaction_sequence),
      pix_key_used: "report-test-key"
    )
  end
end
