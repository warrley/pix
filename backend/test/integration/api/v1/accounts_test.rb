require "test_helper"

class Api::V1::AccountsTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @account = accounts(:one)
    authenticate_requests_as(@user)
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

  test "GET /api/v1/accounts/:id/limit returns limit usage and remaining amount" do
    get limit_api_v1_account_path(@account), as: :json

    assert_response :ok
    assert_equal "5000.00", response.parsed_body.dig("data", "daily_limit")
    assert_equal "0.00", response.parsed_body.dig("data", "used_today")
    assert_equal "5000.00", response.parsed_body.dig("data", "remaining")
  end

  test "GET /api/v1/accounts/:id/limit counts today's eligible outgoing transfers" do
    receiver = User.create!(name: "Limit Receiver", email: "limit_receiver@example.com", doc_id: "11444777000161")
    receiver_account = Account.create!(user: receiver)
    Transaction.create!(
      source_account: @account,
      destination_account: receiver_account,
      end_to_end_id: "E01011010#{SecureRandom.hex(6)}#{SecureRandom.alphanumeric(11).downcase}",
      pix_key_used: "limit_receiver@example.com",
      amount_cents: 2_500,
      status: :completed
    )

    get limit_api_v1_account_path(@account), as: :json

    assert_response :ok
    assert_equal "25.00", response.parsed_body.dig("data", "used_today")
    assert_equal "4975.00", response.parsed_body.dig("data", "remaining")
  end

  test "PATCH /api/v1/accounts/:id/limit updates the daily limit" do
    patch limit_api_v1_account_path(@account), params: { daily_limit: "1250.50" }, as: :json

    assert_response :ok
    assert_equal "1250.50", response.parsed_body.dig("data", "daily_limit")
    assert_equal BigDecimal("1250.50"), @account.reload.daily_limit
  end

  test "PATCH /api/v1/accounts/:id/limit rejects negative and invalid values" do
    [ "-1.00", "not-a-number", "10.999" ].each do |daily_limit|
      patch limit_api_v1_account_path(@account), params: { daily_limit: daily_limit }, as: :json

      assert_response :unprocessable_entity
      assert_equal "request.validation_failed", response.parsed_body["code"]
    end
  end

  test "GET /api/v1/accounts/:id/limit returns 404 when account does not exist" do
    get limit_api_v1_account_path(id: 999999), as: :json

    assert_response :not_found
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

    assert_response :conflict
    assert_equal "Cannot close account with existing balance", response.parsed_body["error"]
  end

  test "DELETE /api/v1/accounts/:id returns 409 when account is already closed" do
    @account.update!(status: "closed")

    delete api_v1_account_path(@account), as: :json

    assert_response :conflict
    assert_equal "account.already_closed", response.parsed_body["code"]
  end
end
