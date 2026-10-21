require "test_helper"

class Api::V1::AuthenticationTest < ActionDispatch::IntegrationTest
  test "POST /api/v1/auth/token issues a bearer token for valid credentials" do
    user = User.create!(
      name: "Auth User",
      email: "auth@example.com",
      doc_id: "52998224725",
      password: "correct horse battery staple"
    )

    post api_v1_auth_token_path, params: {
      email: user.email,
      password: "correct horse battery staple"
    }, as: :json

    assert_response :created
    assert response.parsed_body.dig("data", "access_token").present?
    assert_equal "no-store", response.headers["Cache-Control"]
    assert_equal "Bearer", response.parsed_body.dig("data", "token_type")
    assert response.parsed_body.dig("data", "expires_at").present?
  end

  test "token endpoint rejects invalid credentials without revealing whether the user exists" do
    post api_v1_auth_token_path, params: { email: "missing@example.com", password: "incorrect" }, as: :json

    assert_response :unauthorized
    assert_equal "invalid_credentials", response.parsed_body.dig("error", "code")
  end

  test "token endpoint rejects oversized passwords safely" do
    post api_v1_auth_token_path,
      params: { email: "missing@example.com", password: "x" * 10_000 }, as: :json

    assert_response :unauthorized
  end

  test "protected endpoint rejects missing and invalid bearer tokens" do
    get api_v1_user_path(users(:one)), as: :json
    assert_response :unauthorized

    get api_v1_user_path(users(:one)), headers: { "Authorization" => "Bearer invalid" }, as: :json
    assert_response :unauthorized
  end

  test "an authenticated token cannot call endpoints outside its scopes" do
    user = create_authenticated_user
    token = ApiAccessToken.issue!(user: user, scopes: [ "profile:read" ]).first

    get api_v1_user_accounts_path(user), headers: bearer_headers(token), as: :json

    assert_response :forbidden
    assert_equal "insufficient_scope", response.parsed_body.dig("error", "code")
  end

  test "an authenticated user cannot access another user's resources" do
    user = create_authenticated_user
    token = ApiAccessToken.issue!(user: user).first

    get api_v1_user_path(users(:one)), headers: bearer_headers(token), as: :json

    assert_response :not_found
  end

  test "a customer token cannot perform operator-only account actions" do
    user = create_authenticated_user
    token = ApiAccessToken.issue!(user: user).first

    patch block_fraud_api_v1_account_path(accounts(:one)),
      params: { reason: "Unauthorized" }, headers: bearer_headers(token), as: :json

    assert_response :forbidden
  end

  test "a customer token cannot provision users" do
    user = create_authenticated_user
    token = ApiAccessToken.issue!(user: user).first

    post api_v1_users_path, params: {
      user: { name: "Unauthorized", email: "unauthorized@example.com", doc_id: "52998224725", password: "correct horse battery staple" }
    }, headers: bearer_headers(token), as: :json

    assert_response :forbidden
  end

  test "access token storage contains only a digest" do
    user = create_authenticated_user
    raw_token, record = ApiAccessToken.issue!(user: user)

    assert_equal Digest::SHA256.hexdigest(raw_token), record.token_digest
    refute_equal raw_token, record.token_digest
  end

  test "password changes revoke previously issued access tokens" do
    user = create_authenticated_user
    token = ApiAccessToken.issue!(user: user).first

    user.update!(password: "a different secure passphrase")

    assert_nil ApiAccessToken.authenticate(token)
  end

  test "DELETE /api/v1/auth/token revokes the current token" do
    user = create_authenticated_user
    token = ApiAccessToken.issue!(user: user).first
    headers = bearer_headers(token)

    delete api_v1_auth_token_path, headers: headers, as: :json
    assert_response :no_content

    get api_v1_user_path(user), headers: headers, as: :json
    assert_response :unauthorized
  end

  private

  def create_authenticated_user
    User.create!(
      name: "Auth User",
      email: "auth-#{SecureRandom.hex(4)}@example.com",
      doc_id: "52998224725",
      password: "correct horse battery staple"
    )
  end

  def bearer_headers(token)
    { "Authorization" => "Bearer #{token}" }
  end
end
