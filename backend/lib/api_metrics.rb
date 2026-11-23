require "rack"
require "json"
require "securerandom"
require "openssl"
require_relative "../app/services/observability/metrics" unless defined?(Observability::Metrics)

class ApiMetrics
  METRICS_PATH = "/metrics".freeze
  KNOWN_METHODS = %w[GET POST PUT PATCH DELETE OPTIONS HEAD].freeze

  ROUTE_PATTERNS = [
    [ %r{\A/up\z}, "/up" ],
    [ %r{\A/api-docs\z}, "/api-docs" ],
    [ %r{\A/api-docs/openapi\.yaml\z}, "/api-docs/openapi.yaml" ],
    [ %r{\A/openapi\.yaml\z}, "/openapi.yaml" ],
    [ %r{\A/api/v1/auth/token\z}, "/api/v1/auth/token" ],
    [ %r{\A/api/v1/auth/signup\z}, "/api/v1/auth/signup" ],
    [ %r{\A/api/v1/users\z}, "/api/v1/users" ],
    [ %r{\A/api/v1/users/[^/]+\z}, "/api/v1/users/:id" ],
    [ %r{\A/api/v1/users/[^/]+/accounts\z}, "/api/v1/users/:id/accounts" ],
    [ %r{\A/api/v1/users/[^/]+/reports/transactions(?:\.[a-z0-9]+)?\z}i, "/api/v1/users/:id/reports/transactions" ],
    [ %r{\A/api/v1/accounts\z}, "/api/v1/accounts" ],
    [ %r{\A/api/v1/accounts/[^/]+\z}, "/api/v1/accounts/:id" ],
    [ %r{\A/api/v1/accounts/[^/]+/block_fraud\z}, "/api/v1/accounts/:id/block_fraud" ],
    [ %r{\A/api/v1/accounts/[^/]+/unblock_fraud\z}, "/api/v1/accounts/:id/unblock_fraud" ],
    [ %r{\A/api/v1/accounts/[^/]+/limit\z}, "/api/v1/accounts/:id/limit" ],
    [ %r{\A/api/v1/accounts/[^/]+/pix_keys\z}, "/api/v1/accounts/:id/pix_keys" ],
    [ %r{\A/api/v1/accounts/[^/]+/transfers\z}, "/api/v1/accounts/:id/transfers" ],
    [ %r{\A/api/v1/accounts/[^/]+/scheduled_transfers\z}, "/api/v1/accounts/:id/scheduled_transfers" ],
    [ %r{\A/api/v1/accounts/[^/]+/reports/statement(?:\.[a-z0-9]+)?\z}i, "/api/v1/accounts/:id/reports/statement" ],
    [ %r{\A/api/v1/pix_keys/lookup\z}, "/api/v1/pix_keys/lookup" ],
    [ %r{\A/api/v1/pix_keys/[^/]+\z}, "/api/v1/pix_keys/:id" ],
    [ %r{\A/api/v1/pix_keys/[^/]+/unblock\z}, "/api/v1/pix_keys/:id/unblock" ],
    [ %r{\A/api/v1/pix_keys/[^/]+/reassociate\z}, "/api/v1/pix_keys/:id/reassociate" ],
    [ %r{\A/api/v1/transfers\z}, "/api/v1/transfers" ],
    [ %r{\A/api/v1/transfers/[^/]+\z}, "/api/v1/transfers/:id" ],
    [ %r{\A/api/v1/transfers/[^/]+/cancel\z}, "/api/v1/transfers/:id/cancel" ],
    [ %r{\A/api/v1/transfers/[^/]+/refund\z}, "/api/v1/transfers/:id/refund" ],
    [ %r{\A/api/v1/scheduled_transfers/[^/]+\z}, "/api/v1/scheduled_transfers/:id" ],
    [ %r{\A/api/v1/fraud_reports\z}, "/api/v1/fraud_reports" ],
    [ %r{\A/api/v1/admin/overview\z}, "/api/v1/admin/overview" ],
    [ %r{\A/api/v1/admin/users\z}, "/api/v1/admin/users" ],
    [ %r{\A/api/v1/admin/users/[^/]+\z}, "/api/v1/admin/users/:id" ],
    [ %r{\A/api/v1/admin/users/[^/]+/revoke_sessions\z}, "/api/v1/admin/users/:id/revoke_sessions" ],
    [ %r{\A/api/v1/admin/accounts/[^/]+/(?:block_fraud|unblock_fraud|limit|close|reopen)\z}, "/api/v1/admin/accounts/:id/:action" ],
    [ %r{\A/api/v1/admin/transactions\z}, "/api/v1/admin/transactions" ],
    [ %r{\A/api/v1/admin/audit_logs\z}, "/api/v1/admin/audit_logs" ],
    [ %r{\A/api/v1/admin/refund_requests\z}, "/api/v1/admin/refund_requests" ],
    [ %r{\A/api/v1/admin/refund_requests/[^/]+/(?:approve|reject)\z}, "/api/v1/admin/refund_requests/:id/:action" ],
    [ %r{\A/api/v1/admin/fraud_reports\z}, "/api/v1/admin/fraud_reports" ],
    [ %r{\A/api/v1/admin/fraud_reports/[^/]+/resolve\z}, "/api/v1/admin/fraud_reports/:id/resolve" ],
    [ %r{\A/api/v1/admin/pix_keys\z}, "/api/v1/admin/pix_keys" ],
    [ %r{\A/api/v1/admin/pix_keys/[^/]+(?:/(?:unblock|suspend))?\z}, "/api/v1/admin/pix_keys/:id" ]
  ].freeze

  def initialize(app, metrics_token: nil)
    @app = app
    @configured_token = metrics_token
  end

  def call(env)
    path = env["PATH_INFO"].to_s
    return handle_metrics_scrape(env) if path == METRICS_PATH

    start_time = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    correlation_id = extract_correlation_id(env)
    env["pix.correlation_id"] = correlation_id

    Observability::Metrics.with_request_buffering do
      status, headers, body = @app.call(env)
      duration = Process.clock_gettime(Process::CLOCK_MONOTONIC) - start_time

      headers["X-Correlation-Id"] ||= correlation_id
      Observability::Metrics.record_http_request(
        method: self.class.normalize_method(env["REQUEST_METHOD"]),
        route: self.class.normalize_route(path),
        status_code: status,
        duration_seconds: duration
      )

      [ status, headers, body ]
    end
  end

  def self.normalize_method(method)
    normalized = method.to_s.upcase
    KNOWN_METHODS.include?(normalized) ? normalized : "OTHER"
  end

  def self.normalize_route(path)
    cleaned = path.to_s.sub(%r{/+\z}, "")
    return "OTHER" if cleaned.empty?

    ROUTE_PATTERNS.each do |pattern, normalized|
      return normalized if pattern.match?(cleaned)
    end

    "OTHER"
  end

  private

  def handle_metrics_scrape(env)
    method = env["REQUEST_METHOD"].to_s.upcase
    unless %w[GET HEAD].include?(method)
      return [ 405, { "Content-Type" => "application/json", "Allow" => "GET, HEAD" }, [ JSON.generate(
        data: nil,
        error: { code: "method_not_allowed", message: "Only GET and HEAD are allowed on /metrics" }
      ) ] ]
    end

    unless authorized_metrics_request?(env)
      record_rejected_metrics_auth
      return [ 401, {
        "Content-Type" => "application/json",
        "Cache-Control" => "no-store",
        "WWW-Authenticate" => 'Bearer realm="metrics"'
      }, [ JSON.generate(
        data: nil,
        error: { code: "authentication_required", message: "Valid metrics bearer token required" }
      ) ] ]
    end

    payload = method == "HEAD" ? "" : Observability::Metrics.render_prometheus_text
    [ 200, {
      "Content-Type" => Observability::Metrics::CONTENT_TYPE,
      "Cache-Control" => "no-store"
    }, [ payload ] ]
  end

  def authorized_metrics_request?(env)
    expected = expected_metrics_token
    return false if expected.nil? || expected.empty?

    auth_header = env["HTTP_AUTHORIZATION"].to_s
    scheme, provided = auth_header.split(" ", 2)
    return false unless scheme&.casecmp("Bearer")&.zero? && provided && !provided.empty?
    return false unless provided.bytesize == expected.bytesize

    OpenSSL.fixed_length_secure_compare(provided, expected)
  end

  def expected_metrics_token
    return @configured_token if @configured_token && !@configured_token.empty?

    if defined?(Rails) && Rails.respond_to?(:application) && Rails.application
      configured = Rails.application.config.x.metrics.bearer_token.to_s.strip
      return configured unless configured.empty?
    end

    ENV["METRICS_BEARER_TOKEN"].to_s.strip
  end

  def record_rejected_metrics_auth
    if defined?(Security::AuditLogger)
      Security::AuditLogger.record(event: "metrics_authentication_rejected")
    else
      Observability::Metrics.record_security_event("metrics_authentication_rejected")
    end
  end

  def extract_correlation_id(env)
    candidate = env["HTTP_X_CORRELATION_ID"].to_s.strip
    return candidate if candidate.match?(/\A[A-Za-z0-9._-]{1,128}\z/)

    request_id = env["action_dispatch.request_id"].to_s.strip
    return request_id unless request_id.empty?

    SecureRandom.uuid
  end
end
