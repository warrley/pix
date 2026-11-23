require "test_helper"

class Api::V1::AdminDashboardTest < ActionDispatch::IntegrationTest
  setup do
    @operator = users(:one)
    @operator.update!(role: "operator")
    @token = ApiAccessToken.issue!(user: @operator).first
    @headers = bearer_headers(@token)
  end

  test "operator can load admin overview" do
    get "/api/v1/admin/overview", headers: @headers, as: :json

    assert_response :ok
    assert response.parsed_body["data"].key?("total_users")
    assert response.parsed_body["data"].key?("total_moved_cents")
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

    get "/api/v1/admin/overview", headers: bearer_headers(token), as: :json

    assert_response :forbidden
  end
end
