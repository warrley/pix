require "test_helper"
require "json"

class Notifications::WebhookNotifierTest < ActiveSupport::TestCase
  FakeResponse = Struct.new(:code)

  setup do
    @user = users(:one)
    @payload = {
      event_id: "transaction-42-completed",
      event_type: "completed",
      amount: "100.00",
      end_to_end_id: "E01011010202609211000abcde123456",
      counterparty_name: "Receiver User",
      timestamp: "2026-09-21T12:00:00Z"
    }
    @http = Object.new
    %i[use_ssl= verify_mode= open_timeout= read_timeout= ipaddr=].each do |setter|
      @http.define_singleton_method(setter) { |_value| }
    end
  end

  test "posts a signed JSON envelope to an allowlisted HTTPS endpoint" do
    request_error = nil
    test_case = self
    user = @user
    http = @http
    http.define_singleton_method(:request) do |request, body|
      begin
        parsed = JSON.parse(body)
        timestamp = request["X-Webhook-Timestamp"]
        signature = request["X-Webhook-Signature"]
        test_case.assert_equal "application/json", request["Content-Type"]
        test_case.assert_equal "transaction-42-completed", request["X-Webhook-Event-Id"]
        test_case.assert Notifications::WebhookSigner.valid?(
          body: body,
          secret: "test-secret",
          timestamp: timestamp,
          signature: signature
        )
        test_case.assert_equal user.id, parsed.dig("recipient", "id")
        test_case.assert_equal "completed", parsed.dig("notification", "event_type")
        test_case.assert_equal "100.00", parsed.dig("notification", "amount")
        test_case.refute parsed["recipient"].key?("email")
        FakeResponse.new("202")
      rescue StandardError => e
        request_error = e
        raise
      end
    end

    with_webhook_configuration do
      with_method_stub(Net::HTTP, :new, ->(*) { http }) do
        assert Notifications::WebhookNotifier.notify(@user, @payload),
          "#{request_error&.class}: #{request_error&.message}"
      end
    end
  end

  test "rejects non-HTTPS and non-allowlisted destinations before making a request" do
    with_method_stub(Notifications::WebhookNotifier, :endpoint, -> { "http://notifications.example/notifications" }) do
      with_method_stub(Net::HTTP, :new, ->(*) { flunk "network request should not be made" }) do
        refute Notifications::WebhookNotifier.notify(@user, @payload)
      end
    end
  end

  test "rejects destinations resolving to private addresses" do
    with_webhook_configuration do
      with_method_stub(Resolv, :getaddresses, ->(*) { [ "127.0.0.1" ] }) do
        with_method_stub(Net::HTTP, :new, ->(*) { flunk "network request should not be made" }) do
          refute Notifications::WebhookNotifier.notify(@user, @payload)
        end
      end
    end
  end

  test "returns failure when endpoint responds with an error" do
    @http.define_singleton_method(:request) { |*| FakeResponse.new("503") }

    with_webhook_configuration do
      with_method_stub(Net::HTTP, :new, ->(*) { @http }) do
        refute Notifications::WebhookNotifier.notify(@user, @payload)
      end
    end
  end

  test "fails closed when the signing secret is missing" do
    with_webhook_configuration(secret: nil) do
      with_method_stub(Net::HTTP, :new, ->(*) { flunk "network request should not be made" }) do
        refute Notifications::WebhookNotifier.notify(@user, @payload)
      end
    end
  end

  test "treats a missing endpoint as a disabled successful adapter" do
    with_method_stub(Notifications::WebhookNotifier, :endpoint, -> { nil }) do
      with_method_stub(Net::HTTP, :new, ->(*) { flunk "network request should not be made" }) do
        assert Notifications::WebhookNotifier.notify(@user, @payload)
      end
    end
  end

  private

  def with_webhook_configuration(secret: "test-secret")
    with_method_stub(Notifications::WebhookNotifier, :endpoint, -> { "https://notifications.example/notifications" }) do
      with_method_stub(Notifications::WebhookNotifier, :webhook_secret, -> { secret }) do
        with_method_stub(Notifications::WebhookNotifier, :webhook_allowed_hosts, -> { [ "notifications.example" ] }) do
          with_method_stub(Resolv, :getaddresses, ->(*) { [ "8.8.8.8" ] }) { yield }
        end
      end
    end
  end

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
end
