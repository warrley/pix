require "test_helper"

class Notifications::LoggerNotifierTest < ActiveSupport::TestCase
  test "logs the notification context and returns success" do
    user = users(:one)
    payload = {
      event_type: "completed",
      end_to_end_id: "E01011010202609211000abcde123456"
    }
    messages = []

    logger = Object.new
    logger.define_singleton_method(:info) { |message| messages << message; true }
    previous_logger = Rails.logger
    Rails.logger = logger
    begin
      assert Notifications::LoggerNotifier.notify(user, payload)
    ensure
      Rails.logger = previous_logger
    end

    assert_equal 1, messages.size
    assert_includes messages.first, "completed"
    assert_includes messages.first, payload[:end_to_end_id]
    assert_includes messages.first, user.email
    assert_includes messages.first, user.phone
    refute_includes messages.first, user.doc_id
  end
end
