module Notifications
  class FcmNotifier
    class << self
      def notify(user, payload)
        Rails.logger.info(
          "Simulando push via FCM " \
          "event=#{payload[:event_type]} email=#{user.email}"
        )
        true
      end
    end
  end
end
