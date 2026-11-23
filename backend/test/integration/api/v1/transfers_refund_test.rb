require "test_helper"

class Api::V1::TransfersRefundTest < ActionDispatch::IntegrationTest
  setup do
    @user1 = User.new(name: "Sender", email: "sender_api_refund@example.com", doc_id: "68072551061")
    @user1.save!(validate: false)

    @user2 = User.new(name: "Receiver", email: "receiver_api_refund@example.com", doc_id: "33734063071")
    @user2.save!(validate: false)

    @operator = User.new(name: "Admin Operator", email: "operator_api_refund@example.com", doc_id: "52998224725", role: "operator")
    @operator.save!(validate: false)

    @source = Account.create!(user: @user1, account_number: "55011", balance: 400.0)
    @destination = Account.create!(user: @user2, account_number: "55022", balance: 300.0)

    @transaction = Transaction.create!(
      amount: 100.0,
      source_account: @source,
      destination_account: @destination,
      end_to_end_id: "E01011010202611082000abcde123456",
      pix_key_used: "receiver_api_refund@example.com",
      status: :completed
    )
    authenticate_requests_as(@user1)
  end

  test "customer refund creates a pending refund request and does not move balances immediately" do
    post "/api/v1/transfers/#{@transaction.id}/refund",
      params: { reason: "Fraudulent charge detected", category: "fraud" },
      as: :json

    assert_response :accepted

    data = response.parsed_body["data"]
    assert_equal "pending", data.dig("refund_request", "status")
    assert_equal "fraud", data.dig("refund_request", "category")
    assert_equal "completed", data.dig("transaction", "status")

    @source.reload
    @destination.reload
    assert_equal 400.0, @source.balance
    assert_equal 300.0, @destination.balance
  end

  test "duplicate pending refund request returns 422 Unprocessable Entity" do
    post "/api/v1/transfers/#{@transaction.id}/refund",
      params: { reason: "First request" },
      as: :json
    assert_response :accepted

    post "/api/v1/transfers/#{@transaction.id}/refund",
      params: { reason: "Second request" },
      as: :json
    assert_response :unprocessable_entity
    assert_equal "refund.invalid_request", response.parsed_body["code"]
  end

  test "should return 422 Unprocessable Entity if reason is missing" do
    post "/api/v1/transfers/#{@transaction.id}/refund",
      params: { reason: "" },
      as: :json

    assert_response :unprocessable_entity

    json_response = response.parsed_body
    assert_equal "refund.missing_reason", json_response["code"]
  end

  test "should return 404 if transaction not found" do
    post "/api/v1/transfers/-1/refund",
      params: { reason: "Some reason" },
      as: :json

    assert_response :not_found

    json_response = response.parsed_body
    assert_equal "transaction.not_found", json_response["code"]
  end

  test "operator approving refund request refunds transaction and updates balances" do
    post "/api/v1/transfers/#{@transaction.id}/refund",
      params: { reason: "Fraudulent charge detected" },
      as: :json
    assert_response :accepted
    request_id = response.parsed_body.dig("data", "refund_request", "id")

    authenticate_requests_as(@operator)
    post "/api/v1/admin/refund_requests/#{request_id}/approve",
      params: { decision_note: "Confirmed fraud" },
      as: :json

    assert_response :ok
    assert_equal "approved", response.parsed_body.dig("data", "status")
    assert_equal "refunded", @transaction.reload.status

    @source.reload
    @destination.reload
    assert_equal 500.0, @source.balance
    assert_equal 200.0, @destination.balance
  end

  test "operator approving refund request returns 422 if destination has insufficient balance" do
    @destination.update!(balance: 20.0)

    post "/api/v1/transfers/#{@transaction.id}/refund",
      params: { reason: "Insufficient balance at destination" },
      as: :json
    assert_response :accepted
    request_id = response.parsed_body.dig("data", "refund_request", "id")

    authenticate_requests_as(@operator)
    post "/api/v1/admin/refund_requests/#{request_id}/approve", as: :json

    assert_response :unprocessable_entity
    assert_equal "refund.insufficient_funds", response.parsed_body["code"]
    assert_equal "pending", RefundRequest.find(request_id).status
  end
end
