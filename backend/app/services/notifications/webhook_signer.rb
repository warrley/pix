require "openssl"

module Notifications
  class WebhookSigner
    VERSION = "v1"
    TOLERANCE = 5.minutes

    def self.sign(body:, secret:, timestamp: Time.current.to_i)
      signed_content = "#{timestamp}.#{body}"
      signature = OpenSSL::HMAC.hexdigest("SHA256", secret, signed_content)
      { timestamp: timestamp.to_s, signature: "#{VERSION}=#{signature}" }
    end

    def self.valid?(body:, secret:, timestamp:, signature:, now: Time.current)
      parsed_timestamp = Integer(timestamp)
      return false if (now.to_i - parsed_timestamp).abs > TOLERANCE.to_i

      expected = sign(body: body, secret: secret, timestamp: parsed_timestamp).fetch(:signature)
      ActiveSupport::SecurityUtils.secure_compare(expected, signature.to_s)
    rescue ArgumentError
      false
    end
  end
end
