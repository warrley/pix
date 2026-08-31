require "test_helper"

class Api::V1::AccountsTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @account = accounts(:one)
  end

  test "GET /api/v1/users/:user_id/accounts returns all accounts belonging to the user" do
    get api_v1_user_accounts_path(@user), as: :json

    assert_response :ok
    assert response.parsed_body["data"].is_a?(Array)
  end

  test "GET /api/v1/users/:user_id/accounts returns 404 when user does not exist" do
    get api_v1_user_accounts_path(user_id: 999999), as: :json

    assert_response :not_found
    assert_nil response.parsed_body["data"]
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "GET /api/v1/accounts/:id returns account details" do
    get api_v1_account_path(@account), as: :json

    assert_response :ok
    assert_equal @account.id, response.parsed_body.dig("data", "id")
  end

  test "GET /api/v1/accounts/:id returns 404 when account does not exist" do
    get api_v1_account_path(id: 999999), as: :json

    assert_response :not_found
    assert_nil response.parsed_body["data"]
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "POST /api/v1/accounts creates account" do
    post api_v1_accounts_path, params: {
      account: { user_id: @user.id }
    }, as: :json

    assert_response :created
    assert response.parsed_body.dig("data", "id").present?
    assert_nil response.parsed_body["error"]
  end

  test "DELETE /api/v1/accounts/:id closes account when balance is zero" do
    @account.update!(balance: 0, status: "active")

    delete api_v1_account_path(@account), as: :json

    assert_response :no_content
  end

  test "DELETE /api/v1/accounts/:id is blocked when balance > 0" do
    @account.update!(balance: 150.0, status: "active")

    delete api_v1_account_path(@account), as: :json

    assert_response :bad_request
    assert_equal "Cannot close account with existing balance", response.parsed_body["error"]
  end

  test "DELETE /api/v1/accounts/:id returns 422 when account is already closed" do
    @account.update!(status: "closed")

    delete api_v1_account_path(@account), as: :json

    assert_response :unprocessable_entity
    assert_equal "Account is already closed", response.parsed_body["error"]
  end
end
