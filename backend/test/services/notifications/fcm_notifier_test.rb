require "test_helper"

class Notifications::FcmNotifierTest < ActiveSupport::TestCase
  test "exposes a successful FCM stub" do
    assert_respond_to Notifications::FcmNotifier, :notify

    assert Notifications::FcmNotifier.notify(
      users(:one),
      event_type: "completed"
    )
  end

  test "does not include email addresses in notification logs" do
    user = users(:one)
    messages = []
    logger = Object.new
    logger.define_singleton_method(:info) { |message| messages << message; true }
    previous_logger = Rails.logger
    Rails.logger = logger

    Notifications::FcmNotifier.notify(user, event_type: "completed")

    refute_includes messages.first, user.email
  ensure
    Rails.logger = previous_logger
  end
end
