require "minitest/autorun"
require "rack/mock"
require_relative "../../lib/api_cors"

class ApiCorsTest < Minitest::Test
  def setup
    @app = ApiCors.new(->(_env) { [ 200, { "Content-Type" => "application/json" }, [ "{}" ] ] }, allowed_origins: [ "https://client.example" ])
  end

  def test_allows_preflight_for_permitted_origin_and_headers
    response = Rack::MockRequest.new(@app).options("/api/v1/accounts", "HTTP_ORIGIN" => "https://client.example",
      "HTTP_ACCESS_CONTROL_REQUEST_METHOD" => "POST", "HTTP_ACCESS_CONTROL_REQUEST_HEADERS" => "authorization,content-type")

    assert_equal 204, response.status
    assert_equal "https://client.example", response["Access-Control-Allow-Origin"]
  end

  def test_rejects_unlisted_origins_and_unneeded_headers
    origin_response = Rack::MockRequest.new(@app).get("/api/v1/accounts", "HTTP_ORIGIN" => "https://attacker.example")
    header_response = Rack::MockRequest.new(@app).options("/api/v1/accounts", "HTTP_ORIGIN" => "https://client.example",
      "HTTP_ACCESS_CONTROL_REQUEST_METHOD" => "POST", "HTTP_ACCESS_CONTROL_REQUEST_HEADERS" => "x-admin")

    assert_equal 403, origin_response.status
    assert_equal 403, header_response.status
  end
end
