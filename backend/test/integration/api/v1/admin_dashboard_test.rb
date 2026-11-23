require "test_helper"

class Api::V1::AdminDashboardTest < ActionDispatch::IntegrationTest
  setup do
    @operator = users(:one)
    @operator.update!(role: "operator")
    @token = ApiAccessToken.issue!(user: @operator).first
    @headers = { "Authorization" => "Bearer #{@token}" }

    @customer = users(:two)
    @customer_account = accounts(:two)
  end

  test "operator can load admin overview with queues and insights" do
    get "/api/v1/admin/overview", headers: @headers, as: :json

    assert_response :ok
    data = response.parsed_body["data"]
    assert data.key?("total_users")
    assert data.key?("total_moved_cents")
    assert data.key?("total_balance_cents")
    assert data.key?("average_ticket_cents")
    assert data.key?("success_rate")
    assert data.key?("pix_keys")
    assert data.key?("scheduled_transfers")
    assert data.key?("queues")
    assert data.key?("insights")
    assert data.key?("top_failure_reasons")
    assert data.key?("top_accounts")
  end

  test "operator can list users, transactions, and audit logs" do
    get "/api/v1/admin/users", headers: @headers, as: :json
    assert_response :ok
    assert response.parsed_body.dig("data", "items").is_a?(Array)

    get "/api/v1/admin/transactions", headers: @headers, as: :json
    assert_response :ok
    assert response.parsed_body.dig("data", "items").is_a?(Array)

    get "/api/v1/admin/audit_logs", headers: @headers, as: :json
    assert_response :ok
    assert response.parsed_body.dig("data", "items").is_a?(Array)
  end

  test "operator can manage user, revoke sessions, and manage accounts" do
    ApiAccessToken.issue!(user: @customer)

    get "/api/v1/admin/users/#{@customer.id}", headers: @headers, as: :json
    assert_response :ok
    assert_equal @customer.id, response.parsed_body.dig("data", "id")

    patch "/api/v1/admin/users/#{@customer.id}",
      params: { name: "Updated Customer" },
      headers: @headers,
      as: :json
    assert_response :ok
    assert_equal "Updated Customer", @customer.reload.name

    post "/api/v1/admin/users/#{@customer.id}/revoke_sessions", headers: @headers, as: :json
    assert_response :ok
    assert_equal 0, @customer.api_access_tokens.active.count

    patch "/api/v1/admin/accounts/#{@customer_account.id}/block_fraud",
      params: { reason: "Suspected fraud" },
      headers: @headers,
      as: :json
    assert_response :ok
    assert @customer_account.reload.fraud_blocked?

    patch "/api/v1/admin/accounts/#{@customer_account.id}/unblock_fraud",
      headers: @headers,
      as: :json
    assert_response :ok
    assert_not @customer_account.reload.fraud_blocked?

    patch "/api/v1/admin/accounts/#{@customer_account.id}/limit",
      params: { daily_limit: "9500.00" },
      headers: @headers,
      as: :json
    assert_response :ok
    assert_equal 9500.0, @customer_account.reload.daily_limit.to_f
  end

  test "customer can submit fraud report and operator can resolve and block account" do
    customer_token = ApiAccessToken.issue!(user: @customer).first
    target_account = accounts(:one)

    post "/api/v1/fraud_reports",
      params: { reported_account_id: target_account.id, category: "scam", description: "Phishing attempt" },
      headers: { "Authorization" => "Bearer #{customer_token}" },
      as: :json
    assert_response :created
    report_id = response.parsed_body.dig("data", "id")

    get "/api/v1/admin/fraud_reports", headers: @headers, as: :json
    assert_response :ok
    assert response.parsed_body.dig("data", "items").any? { |r| r["id"] == report_id }

    patch "/api/v1/admin/fraud_reports/#{report_id}/resolve",
      params: { status: "resolved", resolution_note: "Confirmed scam account", block_account: true },
      headers: @headers,
      as: :json
    assert_response :ok
    assert_equal "resolved", response.parsed_body.dig("data", "status")
    assert target_account.reload.fraud_blocked?
  end

  test "operator can list, suspend, unblock, and cancel pix keys" do
    key = pix_keys(:default_email)

    get "/api/v1/admin/pix_keys", headers: @headers, as: :json
    assert_response :ok

    patch "/api/v1/admin/pix_keys/#{key.id}/suspend", headers: @headers, as: :json
    assert_response :ok
    assert_equal "suspended", key.reload.status

    patch "/api/v1/admin/pix_keys/#{key.id}/unblock", headers: @headers, as: :json
    assert_response :ok
    assert_equal "active", key.reload.status

    delete "/api/v1/admin/pix_keys/#{key.id}", headers: @headers, as: :json
    assert_response :ok
    assert_equal "cancelled", key.reload.status
  end

  test "customer cannot access admin dashboard endpoints" do
    token = ApiAccessToken.issue!(user: @customer).first
    headers = { "Authorization" => "Bearer #{token}" }

    get "/api/v1/admin/overview", headers: headers, as: :json
    assert_response :forbidden

    get "/api/v1/admin/refund_requests", headers: headers, as: :json
    assert_response :forbidden

    get "/api/v1/admin/fraud_reports", headers: headers, as: :json
    assert_response :forbidden
  end
end
