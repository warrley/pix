require "test_helper"

class Api::V1::PixKeysTest < ActionDispatch::IntegrationTest
  setup do
    @account = accounts(:one)
    @user = users(:one)
    @user.update!(doc_id: "52998224725") # Ensure valid CPF for testing
    @existing_key = pix_keys(:default_email)
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
end
