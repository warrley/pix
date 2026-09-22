module Notifications
  class Enqueuer
    class << self
      def enqueue(transaction_id, event_type)
        ActiveRecord.after_all_transactions_commit do
          begin
            TransactionNotificationJob.perform_later(transaction_id, event_type)
          rescue StandardError => e
            log_enqueue_failure(event_type, e)
          end
        end
      rescue StandardError => e
        log_enqueue_failure(event_type, e)
      end

      private

      def log_enqueue_failure(event_type, error)
        Rails.logger.error(
          "Failed to enqueue transaction notification " \
          "event=#{event_type} error=#{error.class.name}"
        )
      end
    end
  end
end
