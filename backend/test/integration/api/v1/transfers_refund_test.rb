require "test_helper"

class Api::V1::TransfersRefundTest < ActionDispatch::IntegrationTest
  setup do
    @user1 = User.new(name: "Sender", email: "sender_api_refund@example.com", doc_id: "68072551061")
    @user1.save!(validate: false)

    @user2 = User.new(name: "Receiver", email: "receiver_api_refund@example.com", doc_id: "33734063071")
    @user2.save!(validate: false)

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

  test "should refund a completed transaction and return 200 OK with receipt" do
    post "/api/v1/transfers/#{@transaction.id}/refund",
      params: { reason: "Fraudulent charge detected" },
      as: :json

    assert_response :success

    data = response.parsed_body["data"]
    assert_equal "refunded", data["status"]
    assert_not_nil data["refunded_at"]
    assert_equal "100.00", data["amount"]

    event = @transaction.transaction_events.order(:id).last
    assert_equal "completed", event.previous_status
    assert_equal "refunded", event.current_status
    assert_equal "Fraudulent charge detected", event.reason

    @source.reload
    @destination.reload
    assert_equal 500.0, @source.balance
    assert_equal 200.0, @destination.balance
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

  test "should return 422 if destination has insufficient balance" do
    @destination.update!(balance: 20.0)

    post "/api/v1/transfers/#{@transaction.id}/refund",
      params: { reason: "Insufficient balance at destination" },
      as: :json

    assert_response :unprocessable_entity

    json_response = response.parsed_body
    assert_equal "refund.insufficient_funds", json_response["code"]
  end
end
