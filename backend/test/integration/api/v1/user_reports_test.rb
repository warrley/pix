require "test_helper"
require "csv"

class Api::V1::UserReportsTest < ActionDispatch::IntegrationTest
  setup do
    @user = User.create!(name: "Report API Owner", email: "report.api.owner@example.com", doc_id: "52998224725")
    @account_one = Account.create!(user: @user, status: :active)
    @account_two = Account.create!(user: @user, status: :active)

    @external_user = User.create!(name: "Report API External", email: "report.api.external@example.com", doc_id: "11444777000161")
    @external_account = Account.create!(user: @external_user, status: :active)
  end

  test "GET user transaction report returns consolidated JSON by default" do
    create_transaction(@account_one, @external_account, "100.00")
    create_transaction(@external_account, @account_two, "40.00")
    create_transaction(@account_two, @external_account, "60.00")
    create_transaction(@account_one, @account_two, "25.00")

    get api_v1_user_transactions_report_path(user_id: @user.id)

    assert_response :ok
    assert_equal "application/json", response.media_type
    body = response.parsed_body
    assert_nil body["error"]
    assert_equal @user.id, body.dig("data", "user_id")
    assert_equal 4, body.dig("data", "summary", "total_transactions_count")
    assert_equal 185.0, body.dig("data", "summary", "total_sent_amount")
    assert_equal 65.0, body.dig("data", "summary", "total_received_amount")
    assert_equal(-120.0, body.dig("data", "summary", "net_balance_change"))
    assert_equal 56.25, body.dig("data", "summary", "average_ticket_amount")
    assert_equal [ @account_one.id, @account_two.id ], body.dig("data", "accounts").pluck("account_id")
    assert_equal @account_one.account_number, body.dig("data", "accounts", 0, "account_number")
    assert body.dig("data", "accounts").none? { |account| account["account_id"] == @external_account.id }
  end

  test "GET user transaction report accepts explicit JSON format" do
    get api_v1_user_transactions_report_path(user_id: @user.id, format: :json)

    assert_response :ok
    assert_equal "application/json", response.media_type
    assert_equal @user.id, response.parsed_body.dig("data", "user_id")
  end

  test "GET user transaction report returns CSV as an attachment" do
    create_transaction(@account_one, @external_account, "100.00")
    create_transaction(@external_account, @account_two, "40.00")

    get api_v1_user_transactions_report_path(user_id: @user.id, format: :csv)

    assert_response :ok
    assert_equal "text/csv", response.media_type
    assert_match(/attachment/, response.headers["Content-Disposition"])
    assert_match(/user_#{@user.id}_transactions_report\.csv/, response.headers["Content-Disposition"])

    table = CSV.parse(response.body, headers: true)
    assert_equal [ "account", "account", "summary" ], table.map { |row| row["row_type"] }
    summary_row = table[table.length - 1]
    assert_equal "2", summary_row["total_transactions_count"]
    assert_equal "100.00", summary_row["total_sent_amount"]
    assert_equal "40.00", summary_row["total_received_amount"]
  end

  test "GET user transaction report returns the standard 404 for an unknown user" do
    get api_v1_user_transactions_report_path(user_id: -1)

    assert_response :not_found
    body = response.parsed_body
    assert_nil body["data"]
    assert_equal "Record not found", body["error"]
  end

  test "GET user transaction report returns zero metrics for a user without completed transactions" do
    get api_v1_user_transactions_report_path(user_id: @user.id)

    assert_response :ok
    body = response.parsed_body
    assert_equal 0, body.dig("data", "summary", "total_transactions_count")
    assert_equal 0.0, body.dig("data", "summary", "total_sent_amount")
    assert_equal 0.0, body.dig("data", "summary", "total_received_amount")
    assert_equal 0.0, body.dig("data", "summary", "net_balance_change")
    assert_equal 0.0, body.dig("data", "summary", "average_ticket_amount")
    assert_equal 2, body.dig("data", "accounts").size
  end

  test "GET user transaction report rejects unsupported formats" do
    get api_v1_user_transactions_report_path(user_id: @user.id, format: :xml)

    assert_response :not_acceptable
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
      pix_key_used: "report-api-test-key"
    )
  end
end
