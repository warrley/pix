require "json"
require "net/http"
require "uri"
require "ipaddr"
require "resolv"

module Notifications
  class WebhookNotifier
    DEFAULT_OPEN_TIMEOUT = 5
    DEFAULT_READ_TIMEOUT = 5
    MAX_RESPONSE_BYTES = 65_536

    class << self
      def notify(user, payload)
        configured_endpoint = endpoint
        unless configured_endpoint.present?
          Rails.logger.warn("Transaction notification webhook disabled")
          return true
        end

        uri = validated_endpoint(configured_endpoint)
        return false unless uri

        secret = webhook_secret
        if secret.blank?
          warn_delivery_failure("missing webhook signing secret")
          return false
        end

        body = JSON.generate(event_id: payload.fetch(:event_id), recipient: recipient(user), notification: payload)
        signature = WebhookSigner.sign(body: body, secret: secret)
        response = post(uri, body, signature)

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

      def webhook_secret
        configured = Rails.application.config.x.notifications.webhook_secret
        configured.presence || ENV["NOTIFICATIONS_WEBHOOK_SECRET"]
      end

      def reset_endpoint!
        remove_instance_variable(:@endpoint) if defined?(@endpoint)
      end

      private

      def recipient(user)
        { id: user.id }
      end

      def validated_endpoint(value)
        uri = URI.parse(value)
        allowed_hosts = webhook_allowed_hosts
        normalized_host = uri.host.to_s.downcase
        unless uri.is_a?(URI::HTTPS) && uri.host.present? && uri.port == 443 && uri.userinfo.nil? && uri.fragment.nil? &&
            allowed_hosts.map(&:downcase).include?(normalized_host)
          warn_delivery_failure("destination is not an allowed HTTPS host")
          return
        end

        addresses = Resolv.getaddresses(uri.host)
        if addresses.empty? || addresses.any? { |address| private_address?(address) }
          warn_delivery_failure("destination resolves to a prohibited address")
          return
        end

        uri.instance_variable_set(:@resolved_ip, addresses.first)

        uri
      rescue URI::InvalidURIError, Resolv::ResolvError
        warn_delivery_failure("invalid destination")
        nil
      end

      def private_address?(value)
        address = IPAddr.new(value)
        address = address.native if address.respond_to?(:ipv4_mapped?) && address.ipv4_mapped?
        address.private? || address.loopback? || address.link_local? || address.multicast? ||
          address.to_s == "0.0.0.0" || reserved_address?(address)
      end

      def reserved_address?(address)
        blocked_ranges = if address.ipv4?
          %w[0.0.0.0/8 100.64.0.0/10 192.0.0.0/24 192.0.2.0/24 198.18.0.0/15 198.51.100.0/24 203.0.113.0/24 240.0.0.0/4]
        else
          %w[::/128 ::1/128 64:ff9b::/96 2001:db8::/32]
        end
        blocked_ranges.any? { |range| IPAddr.new(range).include?(address) }
      end

      def post(uri, body, signature)
        http = Net::HTTP.new(uri.host, uri.port, nil)
        http.ipaddr = uri.instance_variable_get(:@resolved_ip) if http.respond_to?(:ipaddr=)
        http.use_ssl = true
        http.verify_mode = OpenSSL::SSL::VERIFY_PEER
        http.open_timeout = open_timeout
        http.read_timeout = read_timeout
        request = Net::HTTP::Post.new(uri.request_uri)
        request["Content-Type"] = "application/json"
        request["X-Webhook-Timestamp"] = signature.fetch(:timestamp)
        request["X-Webhook-Signature"] = signature.fetch(:signature)
        request["X-Webhook-Event-Id"] = JSON.parse(body).fetch("event_id")
        response_body = +""
        http.request(request, body) do |response|
          response.read_body do |chunk|
            raise IOError, "webhook response too large" if response_body.bytesize + chunk.bytesize > MAX_RESPONSE_BYTES

            response_body << chunk
          end
        end
      end

      def webhook_allowed_hosts
        ENV.fetch("NOTIFICATIONS_WEBHOOK_ALLOWED_HOSTS", "").split(",").map(&:strip).reject(&:blank?)
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
