require "test_helper"

class Api::V1::AccountSecurityTest < ActionDispatch::IntegrationTest
  setup do
    @account = accounts(:one)
  end

  test "PATCH /api/v1/accounts/:id/block_fraud updates account status and reason" do
    patch block_fraud_api_v1_account_path(@account), params: { reason: "Suspected phishing attack" }, as: :json

    assert_response :ok
    assert_nil response.parsed_body["error"]
    assert response.parsed_body.dig("data", "fraud_blocked")
    assert_equal "Suspected phishing attack", response.parsed_body.dig("data", "fraud_block_reason")

    @account.reload
    assert @account.fraud_blocked
    assert_equal "Suspected phishing attack", @account.fraud_block_reason
    assert_not_nil @account.fraud_blocked_at
  end

  test "PATCH /api/v1/accounts/:id/block_fraud returns 422 when reason is missing" do
    patch block_fraud_api_v1_account_path(@account), params: {}, as: :json

    assert_response :unprocessable_entity
    assert_equal "Reason is required", response.parsed_body["error"]

    @account.reload
    assert_not @account.fraud_blocked
  end

  test "PATCH /api/v1/accounts/:id/unblock_fraud restores account to unblocked state" do
    @account.update!(fraud_blocked: true, fraud_blocked_at: Time.current, fraud_block_reason: "Investigating")

    patch unblock_fraud_api_v1_account_path(@account), as: :json

    assert_response :ok
    assert_nil response.parsed_body["error"]
    assert_equal false, response.parsed_body.dig("data", "fraud_blocked")
    assert_nil response.parsed_body.dig("data", "fraud_block_reason")

    @account.reload
    assert_not @account.fraud_blocked
    assert_nil @account.fraud_blocked_at
    assert_nil @account.fraud_block_reason
  end

  test "PATCH /api/v1/accounts/:id/block_fraud returns 404 when account does not exist" do
    patch block_fraud_api_v1_account_path(id: 999999), params: { reason: "Test" }, as: :json

    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "PATCH /api/v1/accounts/:id/unblock_fraud returns 404 when account does not exist" do
    patch unblock_fraud_api_v1_account_path(id: 999999), as: :json

    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end
end
