require "test_helper"

class Api::V1::TransfersCancelTest < ActionDispatch::IntegrationTest
  setup do
    @user1 = User.new(name: "Sender", email: "sender_api@example.com", doc_id: "68072551061")
    @user1.save!(validate: false)
    
    @user2 = User.new(name: "Receiver", email: "receiver_api@example.com", doc_id: "33734063071")
    @user2.save!(validate: false)
    
    @source = Account.create!(user: @user1, account_number: "00011", balance: 500.0)
    @destination = Account.create!(user: @user2, account_number: "00022", balance: 100.0)
    
    @transaction = Transaction.create!(
      amount: 100.0,
      source_account: @source,
      destination_account: @destination,
      end_to_end_id: "E01011010202609211000abcde123456",
      pix_key_used: "receiver@example.com",
      status: :processing
    )
  end

  test "should cancel a pending transaction and return 200 OK" do
    post "/api/v1/transfers/#{@transaction.id}/cancel"
    
    assert_response :success
    
    json_response = JSON.parse(response.body)
    assert_equal "cancelled", json_response["status"]
    assert_not_nil json_response["cancelled_at"]
  end

  test "should return 404 if transaction not found" do
    post "/api/v1/transfers/-1/cancel"
    
    assert_response :not_found
    
    json_response = JSON.parse(response.body)
    assert_equal "Transaction not found", json_response["error"]
  end

  test "should return 422 if transaction cannot be cancelled" do
    @transaction.update!(status: :completed)
    
    post "/api/v1/transfers/#{@transaction.id}/cancel"
    
    assert_response :unprocessable_entity
    
    json_response = JSON.parse(response.body)
    assert_equal "Cannot cancel a completed transaction", json_response["error"]
  end
end
