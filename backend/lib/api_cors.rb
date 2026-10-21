require "rack"

class ApiCors
  ALLOWED_METHODS = %w[GET POST PUT PATCH DELETE OPTIONS].freeze
  ALLOWED_HEADERS = %w[Authorization Content-Type X-Idempotency-Key].freeze

  def initialize(app, allowed_origins:)
    @app = app
    @allowed_origins = allowed_origins
  end

  def call(env)
    request = Rack::Request.new(env)
    origin = request.get_header("HTTP_ORIGIN")
    return @app.call(env) unless origin
    return forbidden_origin unless @allowed_origins.include?(origin)

    if request.options? && request.get_header("HTTP_ACCESS_CONTROL_REQUEST_METHOD")
      return preflight_response(request, origin)
    end

    status, headers, body = @app.call(env)
    headers["Access-Control-Allow-Origin"] = origin
    headers["Access-Control-Allow-Methods"] = ALLOWED_METHODS.join(", ")
    headers["Access-Control-Allow-Headers"] = ALLOWED_HEADERS.join(", ")
    headers["Vary"] = [ headers["Vary"], "Origin" ].compact.join(", ")
    [ status, headers, body ]
  end

  private

  def preflight_response(request, origin)
    method = request.get_header("HTTP_ACCESS_CONTROL_REQUEST_METHOD")
    requested_headers = request.get_header("HTTP_ACCESS_CONTROL_REQUEST_HEADERS").to_s.downcase.split(",").map(&:strip)
    allowed_headers = ALLOWED_HEADERS.map(&:downcase)
    return forbidden_origin unless ALLOWED_METHODS.include?(method) && (requested_headers - allowed_headers).empty?

    [ 204, {
      "Access-Control-Allow-Origin" => origin,
      "Access-Control-Allow-Methods" => ALLOWED_METHODS.join(", "),
      "Access-Control-Allow-Headers" => ALLOWED_HEADERS.join(", "),
      "Access-Control-Max-Age" => "600",
      "Vary" => "Origin"
    }, [] ]
  end

  def forbidden_origin
    [ 403, { "Content-Type" => "text/plain; charset=utf-8", "Vary" => "Origin" }, [ "Origin not allowed" ] ]
  end
end
