require "test_helper"

class Api::V1::UsersTest < ActionDispatch::IntegrationTest
  test "POST /api/v1/users creates a user with valid data" do
    post api_v1_users_path, params: {
      user: {
        name: "John Doe",
        email: "john@example.com",
        doc_id: "52998224725",
        phone: "+5511999999999"
      }
    }, as: :json

    assert_response :created
    assert response.parsed_body.dig("data", "id").present?
    assert_nil response.parsed_body["error"]
  end

  test "POST /api/v1/users returns 400 when user param is missing" do
    post api_v1_users_path, params: {}, as: :json

    assert_response :bad_request
    assert_nil response.parsed_body["data"]
    assert response.parsed_body["error"].present?
  end

  test "POST /api/v1/users returns 422 with validation errors" do
    post api_v1_users_path, params: {
      user: {
        name: "",
        email: "invalid-email",
        doc_id: "123",
        phone: "999"
      }
    }, as: :json

    assert_response :unprocessable_entity
    assert_nil response.parsed_body["data"]
    assert response.parsed_body["error"].is_a?(Hash)
    assert response.parsed_body["error"].key?("name")
  end

  test "GET /api/v1/users/:id returns the user when it exists" do
    user = users(:one)

    get api_v1_user_path(user), as: :json

    assert_response :ok
    assert_equal user.name, response.parsed_body.dig("data", "name")
    assert_nil response.parsed_body["error"]
  end

  test "GET /api/v1/users/:id returns 404 when the user does not exist" do
    get api_v1_user_path(id: 999999), as: :json

    assert_response :not_found
    assert_nil response.parsed_body["data"]
    assert_equal "Record not found", response.parsed_body["error"]
  end

  test "PUT /api/v1/users/:id updates a user with valid data" do
    user = users(:one)

    put api_v1_user_path(user), params: {
      user: {
        name: "Updated Name",
        email: "new@example.com",
        doc_id: user.doc_id,
        phone: "+5511888888888"
      }
    }, as: :json

    assert_response :ok
    assert_equal "Updated Name", response.parsed_body.dig("data", "name")
    assert_equal "Updated Name", user.reload.name
    assert_nil response.parsed_body["error"]
  end

  test "PUT /api/v1/users/:id returns 422 when validation fails" do
    user = users(:one)

    put api_v1_user_path(user), params: {
      user: {
        name: "",
        email: "invalid-email",
        doc_id: "123"
      }
    }, as: :json

    assert_response :unprocessable_entity
    assert_nil response.parsed_body["data"]
    assert response.parsed_body["error"].is_a?(Hash)
  end

  test "DELETE /api/v1/users/:id deletes an existing user" do
    user = users(:one)

    delete api_v1_user_path(user), as: :json

    assert_response :no_content
    assert_equal "", response.body
    assert_nil User.find_by(id: user.id)
  end
end
