require "test_helper"

class Api::V1::AccountsTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @account = accounts(:one)
  end

  test "GET /api/v1/users/:user_id/accounts returns all accounts belonging to the user" do
    get "/api/v1/users/#{@user.id}/accounts", as: :json

    assert_response :ok
    assert response.parsed_body["data"].is_a?(Array)
  end

  test "GET /api/v1/accounts/:id returns account details" do
    get "/api/v1/accounts/#{@account.id}", as: :json

    assert_response :ok
    assert_equal @account.id, response.parsed_body.dig("data", "id")
  end

  test "POST /api/v1/accounts creates account" do
    post "/api/v1/accounts", params: {
      account: { user_id: @user.id, balance: 100.0 }
    }, as: :json

    assert_response :created
    assert response.parsed_body.dig("data", "id").present?
  end

  test "DELETE /api/v1/accounts/:id closes account when balance is zero" do
    @account.update!(balance: 0, status: "active")

    delete "/api/v1/accounts/#{@account.id}", as: :json

    assert_response :no_content
  end

  test "DELETE /api/v1/accounts/:id is blocked when balance > 0" do
    @account.update!(balance: 150.0, status: "active")

    delete "/api/v1/accounts/#{@account.id}", as: :json

    assert_response :bad_request
    assert_equal "Cannot close account with existing balance", response.parsed_body["error"]
  end

  test "DELETE /api/v1/accounts/:id returns 422 when account is already closed" do
    @account.update!(status: "closed")

    delete "/api/v1/accounts/#{@account.id}", as: :json

    assert_response :unprocessable_entity
assert_equal "Account is already closed", response.parsed_body["error"]
  end
end
