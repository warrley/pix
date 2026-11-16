require "minitest/autorun"
require "rack/mock"
require_relative "../../lib/api_metrics"

class ApiMetricsTest < Minitest::Test
  def setup
    Observability::Metrics.reset!
    @inner_app = ->(_env) { [ 200, { "Content-Type" => "application/json" }, [ '{"data":"ok"}' ] ] }
    @app = ApiMetrics.new(@inner_app, metrics_token: "test-metrics-secret-token")
  end

  def test_rejects_metrics_request_without_bearer_token
    response = Rack::MockRequest.new(@app).get("/metrics")

    assert_equal 401, response.status
    assert_equal 'Bearer realm="metrics"', response["WWW-Authenticate"]
    assert_equal 1, Observability::Metrics.counter_value(:pix_security_events_total, { event: "metrics_authentication_rejected" })
  end

  def test_rejects_metrics_request_with_invalid_token
    response = Rack::MockRequest.new(@app).get("/metrics", "HTTP_AUTHORIZATION" => "Bearer wrong-token")

    assert_equal 401, response.status
  end

  def test_fails_closed_when_metrics_token_is_not_configured
    unconfigured_app = ApiMetrics.new(@inner_app, metrics_token: "")
    response = Rack::MockRequest.new(unconfigured_app).get("/metrics", "HTTP_AUTHORIZATION" => "Bearer any-token")

    assert_equal 401, response.status
  end

  def test_rejects_non_get_methods_on_metrics_endpoint
    response = Rack::MockRequest.new(@app).post("/metrics", "HTTP_AUTHORIZATION" => "Bearer test-metrics-secret-token")

    assert_equal 405, response.status
    assert_equal "GET, HEAD", response["Allow"]
  end

  def test_exposes_prometheus_metrics_with_valid_bearer_token
    Rack::MockRequest.new(@app).get("/api/v1/accounts/42", "HTTP_X_CORRELATION_ID" => "corr-123")

    response = Rack::MockRequest.new(@app).get("/metrics", "HTTP_AUTHORIZATION" => "Bearer test-metrics-secret-token")

    assert_equal 200, response.status
    assert_equal "text/plain; version=0.0.4; charset=utf-8", response["Content-Type"]
    assert_includes response.body, 'http_requests_total{method="GET",route="/api/v1/accounts/:id",status_code="200"} 1'
    assert_includes response.body, 'http_request_duration_seconds_count{method="GET",route="/api/v1/accounts/:id",status_code="200"} 1'
  end

  def test_normalizes_dynamic_path_segments_and_caps_unknown_api_routes
    assert_equal "/api/v1/accounts/:id", ApiMetrics.normalize_route("/api/v1/accounts/98765")
    assert_equal "/api/v1/transfers/:id", ApiMetrics.normalize_route("/api/v1/transfers/E01011010202609211000abcde123456")
    assert_equal "/api/v1/users/:id/reports/transactions", ApiMetrics.normalize_route("/api/v1/users/15/reports/transactions")
    assert_equal "/api/v1/users/:id/reports/transactions", ApiMetrics.normalize_route("/api/v1/users/15/reports/transactions.csv")
    assert_equal "OTHER", ApiMetrics.normalize_route("/wp-admin/setup.php")
    assert_equal "OTHER", ApiMetrics.normalize_route("/api/v1/unknown-endpoint/12345")
    assert_equal "OTHER", ApiMetrics.normalize_route("/api/v1/accounts/42/nonexistent-action")
  end

  def test_aggregates_metrics_across_forked_puma_worker_processes
    pids = 2.times.map do |worker_idx|
      fork do
        Observability::Metrics.record_transfer(status: "completed", amount_cents: 1_000 * (worker_idx + 1))
        Rack::MockRequest.new(@app).get("/api/v1/accounts/#{worker_idx + 1}")
      end
    end
    pids.each { |pid| Process.wait(pid) }

    response = Rack::MockRequest.new(@app).get("/metrics", "HTTP_AUTHORIZATION" => "Bearer test-metrics-secret-token")

    assert_equal 200, response.status
    assert_includes response.body, 'pix_transfers_total{status="completed",error_code="none"} 2'
    assert_includes response.body, "pix_transfer_amount_cents_total 3000"
    assert_includes response.body, "pix_amount_reais_total 30"
    assert_includes response.body, 'http_requests_total{method="GET",route="/api/v1/accounts/:id",status_code="200"} 2'
  end

  def test_buffers_sql_query_metrics_in_memory_without_rewriting_file_per_query
    mtime_before = File.mtime(Observability::Metrics.store_path)
    sleep 0.02

    5.times do
      Observability::Metrics.record_db_query(sql: "SELECT * FROM accounts WHERE id = 1", duration_seconds: 0.003)
    end

    assert_equal mtime_before, File.mtime(Observability::Metrics.store_path)

    snapshot = Observability::Metrics.histogram_snapshot(:rails_db_query_duration_seconds, { operation: "SELECT" })
    assert_equal 5, snapshot[:count]
  end
end
