module Notifications
  class FcmNotifier
    class << self
      def notify(user, payload)
        Rails.logger.info(
          "Simulando push via FCM " \
          "event=#{payload[:event_type]} recipient_id=#{user.id}"
        )
        true
      end
    end
  end
end
