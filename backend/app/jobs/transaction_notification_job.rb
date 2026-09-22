class TransactionNotificationJob < ApplicationJob
  queue_as :default

  SUPPORTED_EVENTS = %w[completed failed cancelled].freeze
  NOTIFIERS = [
    Notifications::LoggerNotifier,
    Notifications::WebhookNotifier,
    Notifications::FcmNotifier
  ].freeze

  discard_on ActiveRecord::RecordNotFound
  retry_on Notifications::DeliveryError, attempts: 3, wait: :exponentially_longer

  class << self
    def notifiers
      NOTIFIERS
    end
  end

  def perform(transaction_id, event_type)
    event_type = event_type.to_s
    validate_event!(event_type)

    transaction = Transaction.includes(
      source_account: :user,
      destination_account: :user
    ).find(transaction_id)

    failures = []
    recipients_for(transaction, event_type).each do |user, counterparty|
      payload = build_payload(transaction, event_type, counterparty)

      self.class.notifiers.each do |notifier|
        begin
          delivered = notifier.notify(user, payload)
          raise Notifications::DeliveryError if delivered == false
        rescue StandardError => e
          failures << notifier_name(notifier)
          Rails.logger.warn(
            "Transaction notification delivery failed " \
            "event=#{event_type} notifier=#{notifier_name(notifier)} " \
            "error=#{e.class.name}"
          )
        end
      end
    end

    return if failures.empty?

    raise Notifications::DeliveryError, "#{failures.size} notification deliveries failed"
  end

  private

  def validate_event!(event_type)
    return if SUPPORTED_EVENTS.include?(event_type)

    raise ArgumentError, "Unsupported transaction notification event: #{event_type}"
  end

  def recipients_for(transaction, event_type)
    source_user = transaction.source_account.user
    destination_user = transaction.destination_account.user

    if event_type == "completed"
      [
        [ source_user, destination_user ],
        [ destination_user, source_user ]
      ]
    else
      [ [ source_user, destination_user ] ]
    end
  end

  def build_payload(transaction, event_type, counterparty)
    timestamp = event_type == "cancelled" ? transaction.cancelled_at : transaction.created_at
    payload = {
      event_type: event_type,
      amount: format("%.2f", transaction.amount),
      end_to_end_id: transaction.end_to_end_id,
      counterparty_name: counterparty.name,
      timestamp: timestamp.utc.iso8601
    }
    payload[:failure_reason] = transaction.failure_reason if event_type == "failed" && transaction.failure_reason.present?
    payload
  end

  def notifier_name(notifier)
    notifier.respond_to?(:name) ? notifier.name : notifier.class.name
  end
end
