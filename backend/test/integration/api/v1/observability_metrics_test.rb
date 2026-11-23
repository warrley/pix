require "test_helper"

class Api::V1::ObservabilityMetricsTest < ActionDispatch::IntegrationTest
  setup do
    Observability::Metrics.reset!
    @metrics_token = "integration-metrics-bearer-token"
    Rails.application.config.x.metrics.bearer_token = @metrics_token

    @sender = users(:one)
    @receiver = User.create!(
      name: "Metrics Receiver",
      email: "metrics.receiver@example.com",
      doc_id: "11444777000161",
      phone: "+5585988887777"
    )
    @source_account = accounts(:one)
    @source_account.update!(balance: 500.00, status: "active", fraud_blocked: false)
    @dest_account = Account.create!(user: @receiver, balance: 100.00, status: "active")
    @pix_key = PixKey.create!(
      account: @dest_account,
      key_type: "email",
      key_value: "metrics.receiver@example.com",
      status: "active"
    )
  end

  teardown do
    Rails.application.config.x.metrics.bearer_token = nil
  end

  test "GET /metrics requires a valid metrics bearer token" do
    get "/metrics"
    assert_response :unauthorized

    get "/metrics", headers: { "Authorization" => "Bearer invalid-token" }
    assert_response :unauthorized

    get "/metrics", headers: { "Authorization" => "Bearer #{@metrics_token}" }
    assert_response :ok
    assert_equal Observability::Metrics::CONTENT_TYPE, response.headers["Content-Type"]
  end

  test "records HTTP RED, SQL, PIX business, key lifecycle, security, and notification metrics" do
    authenticate_requests_as(@sender)

    # 1. Create a PIX key
    post api_v1_account_pix_keys_path(@source_account),
      params: { pix_key: { key_type: "random" } },
      as: :json
    assert_response :created

    # 2. Execute a completed PIX transfer of R$ 75.50 (7550 cents)
    post api_v1_transfers_path,
      params: {
        transfer: {
          source_account_id: @source_account.id,
          pix_key: @pix_key.key_value,
          amount: "75.50",
          description: "Observability test transfer"
        }
      },
      as: :json
    assert_response :created
    tx_id = response.parsed_body.dig("data", "id")

    # 3. Execute a failed PIX transfer (insufficient funds)
    post api_v1_transfers_path,
      params: {
        transfer: {
          source_account_id: @source_account.id,
          pix_key: @pix_key.key_value,
          amount: "999.00"
        }
      },
      as: :json
    assert_response :unprocessable_entity

    # 4. Run notification job for the completed transfer
    TransactionNotificationJob.perform_now(tx_id, "completed")

    # 5. Scrape /metrics with the configured metrics bearer token
    get "/metrics", headers: { "Authorization" => "Bearer #{@metrics_token}" }
    assert_response :ok

    body = response.body

    # Verify HTTP RED metrics
    assert_includes body, 'http_requests_total{method="POST",route="/api/v1/transfers",status_code="201"} 1'
    assert_includes body, 'http_requests_total{method="POST",route="/api/v1/transfers",status_code="422"} 1'
    assert_includes body, 'http_request_duration_seconds_count{method="POST",route="/api/v1/transfers",status_code="201"} 1'

    # Verify PIX business metrics
    assert_includes body, 'pix_transfers_total{status="completed",error_code="none"} 1'
    assert_includes body, 'pix_transfers_total{status="failed",error_code="transfer.insufficient_funds"} 1'
    assert_includes body, "pix_transfer_amount_cents_total 7550"
    assert_includes body, "pix_amount_reais_total 75.5"
    assert_includes body, "pix_transfer_amount_cents_count 1"

    # Verify PIX key lifecycle metrics
    assert_includes body, 'pix_keys_operations_total{operation="create",key_type="random",status="success"} 1'

    # Verify Security Audit metrics
    assert_includes body, 'pix_security_events_total{event="transfer_created"} 1'

    # Verify Notification delivery metrics
    assert_includes body, 'pix_notifications_delivery_total{notifier="LoggerNotifier",event_type="completed",status="success"} 2'

    # Verify SQL and Runtime / DB gauges
    assert_includes body, "# TYPE rails_db_query_duration_seconds histogram"
    assert_includes body, "# TYPE rails_db_connection_pool gauge"
    assert_includes body, "# TYPE pix_accounts_total gauge"
    assert_includes body, "# TYPE pix_active_keys_total gauge"
    assert_includes body, "# TYPE ruby_process_uptime_seconds gauge"
  end
end
