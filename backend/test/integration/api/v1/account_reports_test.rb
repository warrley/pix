require "test_helper"

class Api::V1::AccountReportsTest < ActionDispatch::IntegrationTest
  setup do
    @user1 = User.new(name: "Alice", email: "alice.report.api@example.com", doc_id: "68072551061")
    @user1.save!(validate: false)

    @user2 = User.new(name: "Bob", email: "bob.report.api@example.com", doc_id: "33734063071")
    @user2.save!(validate: false)

    @account1 = Account.create!(user: @user1, account_number: "88001", balance: 1000.0)
    @account2 = Account.create!(user: @user2, account_number: "88002", balance: 500.0)

    @tx1 = Transaction.create!(
      amount: 150.0,
      source_account: @account1,
      destination_account: @account2,
      end_to_end_id: "E01011010202609101200abcde123451",
      pix_key_used: "bob.report.api@example.com",
      status: :completed,
      created_at: DateTime.new(2026, 9, 10, 12, 0, 0)
    )

    @tx2 = Transaction.create!(
      amount: 250.0,
      source_account: @account2,
      destination_account: @account1,
      end_to_end_id: "E01011010202609151500abcde123452",
      pix_key_used: "alice.report.api@example.com",
      status: :completed,
      created_at: DateTime.new(2026, 9, 15, 15, 0, 0)
    )
  end

  test "GET /api/v1/accounts/:account_id/reports/statement returns statement closing report JSON" do
    get "/api/v1/accounts/#{@account1.id}/reports/statement?month=9&year=2026", as: :json

    assert_response :ok
    data = response.parsed_body["data"]
    assert_not_nil data
    assert_nil response.parsed_body["error"]

    assert_equal @account1.id, data["account_id"]
    assert_equal "88001", data["account_number"]
    assert_equal "900.0", data["opening_balance"]
    assert_equal "250.0", data["total_inflows"]
    assert_equal "150.0", data["total_outflows"]
    assert_equal "1000.0", data["closing_balance"]
    assert_equal true, data["reconciliation_valid"]
    assert_equal 2, data["transactions_count"]
    assert_equal 2, data["transactions"].size
  end

  test "GET /api/v1/accounts/:account_id/reports/statement with format=csv returns CSV file" do
    get "/api/v1/accounts/#{@account1.id}/reports/statement?month=9&year=2026&format=csv"

    assert_response :ok
    assert_match(/text\/csv/, response.headers["Content-Type"])
    assert_includes response.body, "PIX STATEMENT REPORT"
    assert_includes response.body, "Opening Balance,900.0"
    assert_includes response.body, "Closing Balance,1000.0"
    assert_includes response.body, "E01011010202609101200abcde123451"
  end

  test "GET /api/v1/accounts/:account_id/reports/statement returns 404 when account does not exist" do
    get "/api/v1/accounts/999999/reports/statement", as: :json

    assert_response :not_found
    assert_nil response.parsed_body["data"]
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "GET /api/v1/accounts/:account_id/reports/statement returns 422 when parameters are invalid" do
    get "/api/v1/accounts/#{@account1.id}/reports/statement?month=15", as: :json

    assert_response :unprocessable_entity
    assert_nil response.parsed_body["data"]
    assert_equal "Invalid month or year", response.parsed_body["error"]
  end
end
