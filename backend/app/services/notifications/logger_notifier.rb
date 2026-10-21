module Notifications
  class LoggerNotifier
    class << self
      def notify(user, payload)
        Rails.logger.info(
          "Transaction notification dispatched " \
          "event=#{payload[:event_type]} " \
          "event_id=#{payload[:event_id]} " \
          "recipient_id=#{user.id}"
        )
        true
      end
    end
  end
end
