require "test_helper"

class Reports::AccountPeriodServiceTest < ActiveSupport::TestCase
  setup do
    @user1 = User.new(name: "Alice", email: "alice.report@example.com", doc_id: "68072551061")
    @user1.save!(validate: false)

    @user2 = User.new(name: "Bob", email: "bob.report@example.com", doc_id: "33734063071")
    @user2.save!(validate: false)

    @account1 = Account.create!(user: @user1, account_number: "99001", balance: 1000.0)
    @account2 = Account.create!(user: @user2, account_number: "99002", balance: 500.0)
  end

  test "returns error if account does not exist" do
    result = Reports::AccountPeriodService.call(account_id: 999_999)
    assert_not result.success?
    assert_equal "Account not found", result.error
  end

  test "returns error for invalid month" do
    result = Reports::AccountPeriodService.call(account_id: @account1.id, month: 13, year: 2026)
    assert_not result.success?
    assert_equal "Invalid month or year", result.error
  end

  test "returns error when start_date is after end_date" do
    result = Reports::AccountPeriodService.call(
      account_id: @account1.id,
      start_date: "2026-09-30",
      end_date: "2026-09-01"
    )
    assert_not result.success?
    assert_equal "start_date cannot be after end_date", result.error
  end

  test "correctly handles periods with zero activity" do
    result = Reports::AccountPeriodService.call(
      account_id: @account1.id,
      month: 1,
      year: 2026
    )

    assert result.success?
    data = result.data

    assert_equal "1000.0", data[:opening_balance]
    assert_equal "0.0", data[:total_inflows]
    assert_equal "0.0", data[:total_outflows]
    assert_equal "1000.0", data[:closing_balance]
    assert data[:reconciliation_valid]
    assert_equal 0, data[:transactions_count]
    assert_empty data[:transactions]
  end

  test "calculates opening balance, inflows, outflows, and closing balance with reconciliation" do
    # Suponha que no dia 2026-09-10 Alice enviou 200 para Bob (debit Alice)
    Transaction.create!(
      amount: 200.0,
      source_account: @account1,
      destination_account: @account2,
      end_to_end_id: "E01011010202609101200abcde123451",
      pix_key_used: "bob.report@example.com",
      status: :completed,
      created_at: DateTime.new(2026, 9, 10, 12, 0, 0)
    )

    # No dia 2026-09-15 Bob enviou 350 para Alice (credit Alice)
    Transaction.create!(
      amount: 350.0,
      source_account: @account2,
      destination_account: @account1,
      end_to_end_id: "E01011010202609151500abcde123452",
      pix_key_used: "alice.report@example.com",
      status: :completed,
      created_at: DateTime.new(2026, 9, 15, 15, 0, 0)
    )

    # Transação fora do período (em outubro)
    Transaction.create!(
      amount: 100.0,
      source_account: @account1,
      destination_account: @account2,
      end_to_end_id: "E01011010202610051000abcde123453",
      pix_key_used: "bob.report@example.com",
      status: :completed,
      created_at: DateTime.new(2026, 10, 5, 10, 0, 0)
    )

    result = Reports::AccountPeriodService.call(
      account_id: @account1.id,
      month: 9,
      year: 2026
    )

    assert result.success?
    data = result.data

    # Current balance é 1000.0.
    # Outflows desde 01/09: 200 + 100 = 300.
    # Inflows desde 01/09: 350.
    # Opening balance em 01/09: 1000.0 + 300 - 350 = 950.0
    assert_equal "950.0", data[:opening_balance]
    assert_equal "350.0", data[:total_inflows]
    assert_equal "200.0", data[:total_outflows]
    # Closing balance em 30/09: 950.0 + 350.0 - 200.0 = 1100.0
    assert_equal "1100.0", data[:closing_balance]
    assert data[:reconciliation_valid]
    assert_equal 2, data[:transactions_count]
    assert_equal 2, data[:transactions].size

    # Ordem cronológica
    assert_equal "debit", data[:transactions][0][:operation_type]
    assert_equal "credit", data[:transactions][1][:operation_type]
  end

  test "generates valid CSV format" do
    result = Reports::AccountPeriodService.call(
      account_id: @account1.id,
      month: 9,
      year: 2026
    )

    assert result.success?
    csv_content = Reports::AccountPeriodService.generate_csv(result.data)

    assert_includes csv_content, "PIX STATEMENT REPORT"
    assert_includes csv_content, "Opening Balance"
    assert_includes csv_content, "Closing Balance"
    assert_includes csv_content, "End-to-End ID"
  end
end
