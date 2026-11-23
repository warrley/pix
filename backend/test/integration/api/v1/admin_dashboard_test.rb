require "test_helper"

class Api::V1::AdminDashboardTest < ActionDispatch::IntegrationTest
  setup do
    @operator = users(:one)
    @operator.update!(role: "operator")
    @token = ApiAccessToken.issue!(user: @operator).first
    @headers = { "Authorization" => "Bearer #{@token}" }
  end

  test "operator can load admin overview" do
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
    assert data.key?("insights")
    assert data.key?("top_failure_reasons")
    assert data.key?("top_accounts")
  end

  test "operator can list users and transactions" do
    get "/api/v1/admin/users", headers: @headers, as: :json
    assert_response :ok
    assert response.parsed_body.dig("data", "items").is_a?(Array)

    get "/api/v1/admin/transactions", headers: @headers, as: :json
    assert_response :ok
    assert response.parsed_body.dig("data", "items").is_a?(Array)
  end

  test "customer cannot access admin dashboard" do
    customer = users(:two)
    token = ApiAccessToken.issue!(user: customer).first

    get "/api/v1/admin/overview", headers: { "Authorization" => "Bearer #{token}" }, as: :json

    assert_response :forbidden
  end
end
