require "test_helper"

class Api::V1::TransfersTest < ActionDispatch::IntegrationTest
  setup do
    @sender_user = User.create!(name: "Sender User", email: "sender_api@example.com", doc_id: "52998224725")
    @receiver_user = User.create!(name: "Receiver User", email: "receiver_api@example.com", doc_id: "11444777000161")

    @source_account = Account.create!(user: @sender_user, balance: 500.00, status: "active")
    @dest_account = Account.create!(user: @receiver_user, balance: 100.00, status: "active")

    @pix_key = PixKey.create!(account: @dest_account, key_type: "email", key_value: "receiver_api@example.com", status: "active")
  end

  test "POST /api/v1/transfers successfully creates transfer and returns receipt" do
    post api_v1_transfers_path, params: {
      transfer: {
        source_account_id: @source_account.id,
        pix_key: "receiver_api@example.com",
        amount: "50.00",
        description: "Payment for dinner"
      }
    }, as: :json

    assert_response :created
    body = response.parsed_body
    assert_nil body["error"]
    assert_not_nil body["data"]
    assert_equal "completed", body.dig("data", "status")
    assert_equal 50.0, body.dig("data", "amount")
    assert_equal "Payment for dinner", body.dig("data", "description")
    assert_equal @source_account.id, body.dig("data", "sender", "account_id")
    assert_equal @dest_account.id, body.dig("data", "receiver", "account_id")
    assert_equal 450.00, @source_account.reload.balance
    assert_equal 150.00, @dest_account.reload.balance
  end

  test "POST /api/v1/transfers returns 422 when transfer fails" do
    post api_v1_transfers_path, params: {
      transfer: {
        source_account_id: @source_account.id,
        pix_key: "receiver_api@example.com",
        amount: "1000.00"
      }
    }, as: :json

    assert_response :unprocessable_entity
    body = response.parsed_body
    assert_nil body["data"]
    assert_equal "insufficient funds", body["error"]
    assert_equal 500.00, @source_account.reload.balance
    assert_equal 100.00, @dest_account.reload.balance
  end

  test "POST /api/v1/transfers is idempotent using X-Idempotency-Key header" do
    e2e_id = "E01011010202608311500abcdefghijk"

    # First request
    post api_v1_transfers_path, params: {
      transfer: {
        source_account_id: @source_account.id,
        pix_key: "receiver_api@example.com",
        amount: "50.00"
      }
    }, headers: { "X-Idempotency-Key" => e2e_id }, as: :json

    assert_response :created
    first_body = response.parsed_body

    # Duplicate request with same idempotency key
    post api_v1_transfers_path, params: {
      transfer: {
        source_account_id: @source_account.id,
        pix_key: "receiver_api@example.com",
        amount: "50.00"
      }
    }, headers: { "X-Idempotency-Key" => e2e_id }, as: :json

    assert_response :ok
    second_body = response.parsed_body

    assert_equal first_body.dig("data", "id"), second_body.dig("data", "id")
    assert_equal e2e_id, second_body.dig("data", "end_to_end_id")
    assert_equal 450.00, @source_account.reload.balance # Balance deducted only once
    assert_equal 150.00, @dest_account.reload.balance
  end

  test "GET /api/v1/transfers/:id returns receipt by database ID" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_api@example.com",
      amount: 75.00,
      description: "Book purchase"
    )

    get api_v1_transfer_path(result.transaction.id), as: :json

    assert_response :ok
    body = response.parsed_body
    assert_nil body["error"]
    assert_equal result.transaction.id, body.dig("data", "id")
    assert_equal result.transaction.end_to_end_id, body.dig("data", "end_to_end_id")
    assert_equal 75.0, body.dig("data", "amount")
  end

  test "GET /api/v1/transfers/:id returns receipt by end_to_end_id" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_api@example.com",
      amount: 30.00
    )

    get api_v1_transfer_path(result.transaction.end_to_end_id), as: :json

    assert_response :ok
    body = response.parsed_body
    assert_nil body["error"]
    assert_equal result.transaction.id, body.dig("data", "id")
    assert_equal result.transaction.end_to_end_id, body.dig("data", "end_to_end_id")
  end

  test "GET /api/v1/transfers/:id returns 404 when transaction is not found" do
    get api_v1_transfer_path(id: 999999), as: :json

    assert_response :not_found
    body = response.parsed_body
    assert_nil body["data"]
    assert_equal "Record not found", body["error"]
  end
end
