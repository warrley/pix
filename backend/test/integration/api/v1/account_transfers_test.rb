require "test_helper"

class Api::V1::AccountTransfersTest < ActionDispatch::IntegrationTest
  setup do
    @user = User.new(name: "Alice", email: "alice@example.com", doc_id: "68072551061")
    @user.save!(validate: false)

    @account = Account.create!(user: @user, account_number: "11111", balance: 1000.0)
  end

  test "should return 200 OK with transactions list and pagination metadata" do
    get "/api/v1/accounts/#{@account.id}/transfers?page=1&per_page=20"

    assert_response :success

    json_response = JSON.parse(response.body)
    assert json_response.key?("transactions")
    assert json_response.key?("pagination")
    assert_equal 1, json_response["pagination"]["current_page"]
  end

  test "should return 404 if account does not exist" do
    get "/api/v1/accounts/-1/transfers"

    assert_response :not_found

    json_response = JSON.parse(response.body)
    assert_equal "Account not found", json_response["error"]
  end
end
