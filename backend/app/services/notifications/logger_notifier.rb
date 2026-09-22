module Notifications
  class LoggerNotifier
    class << self
      def notify(user, payload)
        Rails.logger.info(
          "Transaction notification dispatched " \
          "event=#{payload[:event_type]} " \
          "end_to_end_id=#{payload[:end_to_end_id]} " \
          "email=#{user.email} phone=#{user.phone}"
        )
        true
      end
    end
  end
end
