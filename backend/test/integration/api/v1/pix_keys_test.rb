require "test_helper"

class Api::V1::PixKeysTest < ActionDispatch::IntegrationTest
  setup do
    @account = accounts(:one)
    @user = users(:one)
    @user.update!(doc_id: "52998224725") # Ensure valid CPF for testing
    @existing_key = pix_keys(:default_email)
    authenticate_requests_as(@user)
  end

  test "POST /api/v1/accounts/:account_id/pix_keys creates a PIX key" do
    post api_v1_account_pix_keys_path(account_id: @account.id), params: {
      pix_key: { key_type: "email", key_value: "new_unique_email@example.com" }
    }, as: :json

    assert_response :created
    assert_equal "new_unique_email@example.com", response.parsed_body.dig("data", "key_value")
    assert_nil response.parsed_body["error"]
  end

  test "POST /api/v1/accounts/:account_id/pix_keys returns 422 for invalid format" do
    post api_v1_account_pix_keys_path(account_id: @account.id), params: {
      pix_key: { key_type: "cpf", key_value: "00000000000" }
    }, as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "POST /api/v1/accounts/:account_id/pix_keys returns 422 for duplicate active key" do
    post api_v1_account_pix_keys_path(account_id: @account.id), params: {
      pix_key: { key_type: "email", key_value: @existing_key.key_value }
    }, as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "POST /api/v1/accounts/:account_id/pix_keys returns 404 for unknown account" do
    post api_v1_account_pix_keys_path(account_id: 999_999), params: {
      pix_key: { key_type: "email", key_value: "test@example.com" }
    }, as: :json

    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "POST /api/v1/accounts/:account_id/pix_keys returns 422 when account is not active" do
    @account.update!(status: "closed")

    post api_v1_account_pix_keys_path(account_id: @account.id), params: {
      pix_key: { key_type: "email", key_value: "test_inactive@example.com" }
    }, as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "GET /api/v1/accounts/:account_id/pix_keys returns active keys" do
    get api_v1_account_pix_keys_path(account_id: @account.id), as: :json

    assert_response :ok
    assert_kind_of Array, response.parsed_body["data"]
    assert_nil response.parsed_body["error"]
  end

  test "GET /api/v1/accounts/:account_id/pix_keys returns 404 for unknown account" do
    get api_v1_account_pix_keys_path(account_id: 999_999), as: :json

    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "DELETE /api/v1/pix_keys/:id cancels key (soft-delete)" do
    delete api_v1_pix_key_path(id: @existing_key.id), as: :json

    assert_response :no_content
    assert_equal "cancelled", @existing_key.reload.status
  end

  test "DELETE /api/v1/pix_keys/:id is idempotent when key is already cancelled" do
    @existing_key.cancelled!

    delete api_v1_pix_key_path(id: @existing_key.id), as: :json

    assert_response :no_content
    assert_equal "cancelled", @existing_key.reload.status
  end

  test "DELETE /api/v1/pix_keys/:id returns 404 for unknown key" do
    delete api_v1_pix_key_path(id: 999_999), as: :json

    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/unblock reactivates suspended key and returns 200" do
    suspended_key = PixKey.create!(
      account: @account,
      key_type: "email",
      key_value: "suspended_api@example.com",
      status: :suspended
    )

    patch unblock_api_v1_pix_key_path(id: suspended_key.id), as: :json

    assert_response :ok
    assert_equal "active", response.parsed_body.dig("data", "status")
    assert_nil response.parsed_body["error"]
    assert_equal "active", suspended_key.reload.status
  end

  test "PATCH /api/v1/pix_keys/:id/unblock returns 422 if key is active" do
    patch unblock_api_v1_pix_key_path(id: @existing_key.id), as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
    assert_nil response.parsed_body["data"]
  end

  test "PATCH /api/v1/pix_keys/:id/unblock returns 422 if key is cancelled" do
    @existing_key.cancelled!

    patch unblock_api_v1_pix_key_path(id: @existing_key.id), as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
    assert_nil response.parsed_body["data"]
  end

  test "PATCH /api/v1/pix_keys/:id/unblock returns 422 if owning account is fraud-blocked" do
    suspended_key = PixKey.create!(
      account: @account,
      key_type: "email",
      key_value: "suspended_fraud_blocked_api@example.com",
      status: :suspended
    )
    @account.update!(fraud_blocked: true)

    patch unblock_api_v1_pix_key_path(id: suspended_key.id), as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/unblock returns 422 if owning account is blocked" do
    suspended_key = PixKey.create!(
      account: @account,
      key_type: "email",
      key_value: "suspended_blocked_api@example.com",
      status: :suspended
    )
    @account.update!(status: "blocked")

    patch unblock_api_v1_pix_key_path(id: suspended_key.id), as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/unblock returns 422 if owning account is closed" do
    suspended_key = PixKey.create!(
      account: @account,
      key_type: "email",
      key_value: "suspended_closed_api@example.com",
      status: :suspended
    )
    @account.update!(status: "closed")

    patch unblock_api_v1_pix_key_path(id: suspended_key.id), as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/unblock returns 422 if account exceeds 5 active keys" do
    @account.pix_keys.destroy_all
    5.times do |i|
      PixKey.create!(account: @account, key_type: "email", key_value: "active_limit_#{i}@example.com", status: :active)
    end
    suspended_key = PixKey.create!(account: @account, key_type: "email", key_value: "sixth_suspended@example.com", status: :suspended)

    patch unblock_api_v1_pix_key_path(id: suspended_key.id), as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/unblock returns 422 if active key duplicate exists" do
    suspended_key = PixKey.create!(
      account: @account,
      key_type: "email",
      key_value: "duplicate_candidate@example.com",
      status: :suspended
    )

    other_user = User.create!(name: "Other", email: "other_dup@example.com", doc_id: "04252011000110")
    other_acc = Account.create!(user: other_user, balance: 50.0, status: "active")
    PixKey.create!(account: other_acc, key_type: "email", key_value: "duplicate_candidate@example.com", status: :active)

    patch unblock_api_v1_pix_key_path(id: suspended_key.id), as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/unblock returns 404 for unknown key" do
    patch unblock_api_v1_pix_key_path(id: 999_999), as: :json

    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/reassociate moves key to another account of same owner and returns 200" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "move_api@example.com", status: :active)

    patch reassociate_api_v1_pix_key_path(id: active_key.id), params: { account_id: target_account.id }, as: :json

    assert_response :ok
    assert_equal target_account.id, response.parsed_body.dig("data", "account_id")
    assert_equal target_account.id, active_key.reload.account_id
    assert_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/reassociate returns 422 when target account belongs to another user" do
    other_user = User.create!(name: "Other User", email: "other_api@example.com", doc_id: "04252011000110")
    other_account = Account.create!(user: other_user, balance: 100.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "other_owner_api@example.com", status: :active)

    patch reassociate_api_v1_pix_key_path(id: active_key.id), params: { account_id: other_account.id }, as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/reassociate returns 422 when target account has 5 active keys" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    5.times do |i|
      PixKey.create!(account: target_account, key_type: "email", key_value: "target_limit_api_#{i}@example.com", status: :active)
    end
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "sixth_move_api@example.com", status: :active)

    patch reassociate_api_v1_pix_key_path(id: active_key.id), params: { account_id: target_account.id }, as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/reassociate returns 422 when target account is not active" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "closed")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "closed_target_api@example.com", status: :active)

    patch reassociate_api_v1_pix_key_path(id: active_key.id), params: { account_id: target_account.id }, as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/reassociate returns 422 when key is not active" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    suspended_key = PixKey.create!(account: @account, key_type: "email", key_value: "suspended_move_api@example.com", status: :suspended)

    patch reassociate_api_v1_pix_key_path(id: suspended_key.id), params: { account_id: target_account.id }, as: :json

    assert_response :unprocessable_entity
    assert_not_nil response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/reassociate returns 404 when key not found" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")

    patch reassociate_api_v1_pix_key_path(id: 999_999), params: { account_id: target_account.id }, as: :json

    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "PATCH /api/v1/pix_keys/:id/reassociate returns 404 when target account not found" do
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "no_acc_api@example.com", status: :active)

    patch reassociate_api_v1_pix_key_path(id: active_key.id), params: { account_id: 999_999 }, as: :json

    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end
end
