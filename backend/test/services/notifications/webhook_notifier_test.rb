require "test_helper"
require "json"

class Notifications::WebhookNotifierTest < ActiveSupport::TestCase
  FakeResponse = Struct.new(:code)

  setup do
    @user = users(:one)
    @payload = {
      event_type: "completed",
      amount: "100.00",
      end_to_end_id: "E01011010202609211000abcde123456",
      counterparty_name: "Receiver User",
      timestamp: "2026-09-21T12:00:00Z"
    }
  end

  test "posts a JSON envelope to the configured endpoint" do
    request_error = nil
    test_case = self
    user = @user
    request = Object.new
    request.define_singleton_method(:post) do |path, body, headers|
      begin
        parsed = JSON.parse(body)
        test_case.assert_equal "/notifications", path
        test_case.assert_equal "application/json", headers["Content-Type"]
        test_case.assert_equal user.email, parsed.dig("recipient", "email")
        test_case.assert_equal user.phone, parsed.dig("recipient", "phone")
        test_case.assert_equal "completed", parsed.dig("notification", "event_type")
        test_case.assert_equal "100.00", parsed.dig("notification", "amount")
        FakeResponse.new("202")
      rescue StandardError => e
        request_error = e
        raise
      end
    end

    start_http = ->(*, **, &block) { block.call(request) }
    with_method_stub(Notifications::WebhookNotifier, :endpoint, -> { "https://notifications.example/notifications" }) do
      with_method_stub(Net::HTTP, :start, start_http) do
        assert Notifications::WebhookNotifier.notify(@user, @payload),
          "#{request_error&.class}: #{request_error&.message}"
      end
    end
  end

  test "returns failure and logs when the endpoint responds with an error" do
    messages = []
    request = Object.new
    request.define_singleton_method(:post) { |_path, _body, _headers| FakeResponse.new("503") }

    with_method_stub(Notifications::WebhookNotifier, :endpoint, -> { "https://notifications.example/notifications" }) do
      with_method_stub(Net::HTTP, :start, ->(*, **, &block) { block.call(request) }) do
        with_logger_method(:warn, ->(message) { messages << message; true }) do
          refute Notifications::WebhookNotifier.notify(@user, @payload)
        end
      end
    end
    assert_equal 1, messages.size
  end

  test "returns failure and logs when the request raises a network error" do
    messages = []

    with_method_stub(Notifications::WebhookNotifier, :endpoint, -> { "https://notifications.example/notifications" }) do
      with_method_stub(Net::HTTP, :start, ->(*) { raise Timeout::Error, "timed out" }) do
        with_logger_method(:warn, ->(message) { messages << message; true }) do
          refute Notifications::WebhookNotifier.notify(@user, @payload)
        end
      end
    end

    assert_equal 1, messages.size
  end

  test "treats a missing endpoint as a disabled successful adapter" do
    messages = []

    with_method_stub(Notifications::WebhookNotifier, :endpoint, -> { nil }) do
      with_method_stub(Net::HTTP, :start, ->(*) { flunk "network request should not be made" }) do
        with_logger_method(:warn, ->(message) { messages << message; true }) do
          assert Notifications::WebhookNotifier.notify(@user, @payload)
        end
      end
    end

    assert_equal 1, messages.size
  end

  private

  def with_method_stub(object, method_name, callable)
    original = object.method(method_name)
    object.define_singleton_method(method_name) do |*args, **kwargs, &block|
      callable.call(*args, **kwargs, &block)
    end
    yield
  ensure
    object.define_singleton_method(method_name) do |*args, **kwargs, &block|
      original.call(*args, **kwargs, &block)
    end
  end

  def with_logger_method(method_name, callable)
    logger = Object.new
    logger.define_singleton_method(method_name, &callable)
    previous_logger = Rails.logger
    Rails.logger = logger
    yield
  ensure
    Rails.logger = previous_logger
  end
end
