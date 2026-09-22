require "json"
require "net/http"
require "uri"

module Notifications
  class WebhookNotifier
    DEFAULT_OPEN_TIMEOUT = 5
    DEFAULT_READ_TIMEOUT = 5

    class << self
      def notify(user, payload)
        configured_endpoint = endpoint
        unless configured_endpoint.present?
          Rails.logger.warn("Transaction notification webhook disabled")
          return true
        end

        uri = URI.parse(configured_endpoint)
        response = Net::HTTP.start(
          uri.host,
          uri.port,
          use_ssl: uri.scheme == "https",
          open_timeout: open_timeout,
          read_timeout: read_timeout
        ) do |http|
          http.post(
            uri.request_uri,
            JSON.generate(recipient: recipient(user), notification: payload),
            "Content-Type" => "application/json"
          )
        end

        return true if response.code.to_i.between?(200, 299)

        warn_delivery_failure("non-2xx response")
        false
      rescue StandardError => e
        warn_delivery_failure(e.class.name)
        false
      end

      def endpoint
        return @endpoint if defined?(@endpoint)

        configured_endpoint = Rails.application.config.x.notifications.webhook_url
        configured_endpoint.presence || ENV["NOTIFICATIONS_WEBHOOK_URL"]
      end

      def endpoint=(value)
        @endpoint = value
      end

      def reset_endpoint!
        remove_instance_variable(:@endpoint) if defined?(@endpoint)
      end

      private

      def recipient(user)
        { email: user.email, phone: user.phone }
      end

      def open_timeout
        configured_timeout(:webhook_open_timeout, DEFAULT_OPEN_TIMEOUT)
      end

      def read_timeout
        configured_timeout(:webhook_read_timeout, DEFAULT_READ_TIMEOUT)
      end

      def configured_timeout(name, default)
        configured = Rails.application.config.x.notifications.public_send(name)
        configured.present? ? configured.to_i : default
      end

      def warn_delivery_failure(reason)
        Rails.logger.warn("Transaction notification webhook failed reason=#{reason}")
      end
    end
  end
end
