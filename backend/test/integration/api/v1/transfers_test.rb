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
    assert_equal "50.00", body.dig("data", "amount")
    assert_equal "Payment for dinner", body.dig("data", "description")
    assert_equal @source_account.id, body.dig("data", "sender", "account_id")
    assert_equal @dest_account.id, body.dig("data", "receiver", "account_id")
    assert_equal 450.00, @source_account.reload.balance
    assert_equal 150.00, @dest_account.reload.balance

    transaction = Transaction.find(body.dig("data", "id"))
    events = transaction.transaction_events.order(:id)
    assert_equal [ "created", "completed" ], events.map(&:current_status)
    assert_equal [ nil, "created" ], events.map(&:previous_status)
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
    assert_equal "transfer.insufficient_funds", body["code"]
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
      amount_cents: 7_500,
      description: "Book purchase"
    )

    get api_v1_transfer_path(result.transaction.id), as: :json

    assert_response :ok
    body = response.parsed_body
    assert_nil body["error"]
    assert_equal result.transaction.id, body.dig("data", "id")
    assert_equal result.transaction.end_to_end_id, body.dig("data", "end_to_end_id")
    assert_equal "75.00", body.dig("data", "amount")
  end

  test "GET /api/v1/transfers/:id returns receipt by end_to_end_id" do
    result = Pix::TransferService.call(
      source_account_id: @source_account.id,
      pix_key: "receiver_api@example.com",
      amount_cents: 3_000
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
    assert_equal "resource.not_found", body["code"]
  end

  test "POST /api/v1/transfers rejects numeric amounts instead of parsing binary floats" do
    post api_v1_transfers_path, params: {
      transfer: { source_account_id: @source_account.id, pix_key: "receiver_api@example.com", amount: 50.25 }
    }, as: :json

    assert_response :unprocessable_entity
    assert_equal "amount.invalid", response.parsed_body["code"]
  end

  test "POST /api/v1/transfers rejects amounts above the system ceiling" do
    post api_v1_transfers_path, params: {
      transfer: { source_account_id: @source_account.id, pix_key: "receiver_api@example.com", amount: "10000000.00" }
    }, as: :json

    assert_response :unprocessable_entity
    assert_equal "amount.limit_exceeded", response.parsed_body["code"]
  end

  test "POST /api/v1/transfers requires the canonical transfer wrapper" do
    post api_v1_transfers_path, params: {
      source_account_id: @source_account.id, pix_key: "receiver_api@example.com", amount: "1.00"
    }, as: :json

    assert_response :bad_request
    assert_equal "request.parameter_missing", response.parsed_body["code"]
  end

  test "POST /api/v1/transfers rejects a non-JSON content type" do
    post api_v1_transfers_path,
      params: "transfer[source_account_id]=#{@source_account.id}",
      headers: { "CONTENT_TYPE" => "application/x-www-form-urlencoded", "ACCEPT" => "application/json" }

    assert_response :unsupported_media_type
    assert_equal "request.unsupported_media_type", response.parsed_body["code"]
  end
end
