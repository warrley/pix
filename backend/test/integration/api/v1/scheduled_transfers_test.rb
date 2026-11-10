require "test_helper"

class Api::V1::ScheduledTransfersTest < ActionDispatch::IntegrationTest
  setup do
    @user = User.new(name: "Alice", email: "scheduled-api@example.com", doc_id: "68072551061")
    @user.save!(validate: false)
    @account = Account.create!(user: @user, account_number: "schedapi1", balance: 100)
    receiver = User.new(name: "Bob", email: "bob@example.com", doc_id: "33734063071")
    receiver.save!(validate: false)
    @destination = Account.create!(user: receiver, account_number: "schedapi3", balance: 0)
    PixKey.create!(account: @destination, key_type: :email, key_value: receiver.email)
    authenticate_requests_as(@user)
  end

  test "creates and lists a scheduled transfer for an owned account" do
    post "/api/v1/accounts/#{@account.id}/scheduled_transfers", params: {
      scheduled_transfer: { pix_key: "bob@example.com", amount: "12.50", scheduled_for: Date.tomorrow.to_s }
    }, as: :json
    assert_response :created
    created_id = response.parsed_body.dig("data", "id")
    assert_equal "pending", response.parsed_body.dig("data", "status")

    get "/api/v1/accounts/#{@account.id}/scheduled_transfers"
    assert_response :success
    assert_equal [ created_id ], response.parsed_body.dig("data").map { |item| item["id"] }
  end

  test "cancels only pending scheduled transfers" do
    transfer = @account.scheduled_transfers.create!(destination_account: @destination, pix_key_used: "bob@example.com", amount_cents: 100, scheduled_for: Date.tomorrow)
    delete "/api/v1/scheduled_transfers/#{transfer.id}"
    assert_response :success
    assert_equal "cancelled", transfer.reload.status
  end

  test "does not expose another user's account" do
    other = User.new(name: "Carol", email: "scheduled-other@example.com", doc_id: "52998224725")
    other.save!(validate: false)
    account = Account.create!(user: other, account_number: "schedapi2", balance: 100)
    get "/api/v1/accounts/#{account.id}/scheduled_transfers"
    assert_response :not_found
  end
end
