require "test_helper"

class Notifications::FcmNotifierTest < ActiveSupport::TestCase
  test "exposes a successful FCM stub" do
    assert_respond_to Notifications::FcmNotifier, :notify

    assert Notifications::FcmNotifier.notify(
      users(:one),
      event_type: "completed"
    )
  end
end
