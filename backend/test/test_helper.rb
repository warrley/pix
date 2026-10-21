ENV["RAILS_ENV"] ||= "test"
require_relative "../config/environment"
require "rails/test_help"

module ApiAuthenticationTestSupport
  def authenticate_requests_as(user)
    raw_token, = ApiAccessToken.issue!(user: user)
    @api_auth_headers = { "Authorization" => "Bearer #{raw_token}" }
  end

  def process(method, path, headers: nil, **options)
    merged_headers = (@api_auth_headers || {}).merge(headers || {})
    super(method, path, headers: merged_headers, **options)
  end
end

ActionDispatch::IntegrationTest.prepend(ApiAuthenticationTestSupport)

module ActiveSupport
  class TestCase
    # Run tests in parallel with specified workers
    parallelize(workers: :number_of_processors)

    # Setup all fixtures in test/fixtures/*.yml for all tests in alphabetical order.
    fixtures :all

    # Add more helper methods to be used by all tests here...
  end
end
