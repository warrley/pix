require "test_helper"

class Api::V1::AccountTransfersTest < ActionDispatch::IntegrationTest
  setup do
    @user = User.new(name: "Alice", email: "alice@example.com", doc_id: "68072551061")
    @user.save!(validate: false)

    @account = Account.create!(user: @user, account_number: "11111", balance: 1000.0)

    receiver_user = User.new(name: "Bob", email: "bob_statement@example.com", doc_id: "33734063071")
    receiver_user.save!(validate: false)
    @receiver_account = Account.create!(user: receiver_user, account_number: "22222", balance: 500.0)

    @completed_transaction = Transaction.create!(
      amount: 100.0,
      source_account: @account,
      destination_account: @receiver_account,
      end_to_end_id: "E01011010202609221000abcde123451",
      pix_key_used: "bob_statement@example.com",
      status: :completed,
      created_at: Time.utc(2026, 9, 20, 12, 0, 0)
    )
    @cancelled_transaction = Transaction.create!(
      amount: 50.0,
      source_account: @receiver_account,
      destination_account: @account,
      end_to_end_id: "E01011010202609221000abcde123452",
      pix_key_used: "alice@example.com",
      status: :cancelled,
      created_at: Time.utc(2026, 9, 21, 12, 0, 0)
    )
    @pending_transaction = Transaction.create!(
      amount: 25.0,
      source_account: @account,
      destination_account: @receiver_account,
      end_to_end_id: "E01011010202609221000abcde123453",
      pix_key_used: "bob_statement@example.com",
      status: :processing,
      created_at: Time.utc(2026, 9, 22, 12, 0, 0)
    )
  end

  test "should return 200 OK with transactions list and pagination metadata" do
    get "/api/v1/accounts/#{@account.id}/transfers?page=1&per_page=20"

    assert_response :success

    json_response = JSON.parse(response.body)
    assert json_response.key?("transactions")
    assert json_response.key?("pagination")
    assert_equal 1, json_response["pagination"]["current_page"]
  end

  test "should return 404 if account does not exist" do
    get "/api/v1/accounts/-1/transfers"

    assert_response :not_found

    json_response = JSON.parse(response.body)
    assert_equal "Account not found", json_response["error"]
  end

  test "should return transactions within an inclusive date range" do
    get "/api/v1/accounts/#{@account.id}/transfers", params: {
      start_date: "2026-09-20",
      end_date: "2026-09-21"
    }

    assert_response :success
    transactions = JSON.parse(response.body)["transactions"]
    assert_equal [ @cancelled_transaction.id, @completed_transaction.id ], transactions.map { |transaction| transaction["id"] }
  end

  test "should return bad request for an invalid date" do
    get "/api/v1/accounts/#{@account.id}/transfers", params: { start_date: "2026-99-99" }

    assert_response :bad_request
    assert_equal "invalid date: 2026-99-99", JSON.parse(response.body)["error"]
  end

  test "should return bad request when start date is after end date" do
    get "/api/v1/accounts/#{@account.id}/transfers", params: {
      start_date: "2026-09-22",
      end_date: "2026-09-21"
    }

    assert_response :bad_request
    assert_equal "start_date must be before or equal to end_date", JSON.parse(response.body)["error"]
  end

  test "should filter by completed, cancelled, and pending statuses" do
    {
      "completed" => @completed_transaction,
      "cancelled" => @cancelled_transaction,
      "pending" => @pending_transaction
    }.each do |status, transaction|
      get "/api/v1/accounts/#{@account.id}/transfers", params: { status: status }

      assert_response :success
      transactions = JSON.parse(response.body)["transactions"]
      assert_equal [ transaction.id ], transactions.map { |item| item["id"] }
    end
  end

  test "should return bad request for an unsupported status" do
    get "/api/v1/accounts/#{@account.id}/transfers", params: { status: "failed" }

    assert_response :bad_request
    assert_equal "invalid status: failed", JSON.parse(response.body)["error"]
  end
end
