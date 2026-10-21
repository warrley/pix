require "bigdecimal"

module Pix
  module Money
    MAX_TRANSFER_CENTS = 999_999_999
    DECIMAL_AMOUNT = /\A(?:0|[1-9]\d*)(?:\.\d{1,2})?\z/

    class InvalidAmount < ArgumentError; end
    class AmountLimitExceeded < ArgumentError; end

    def self.parse_cents(value)
      unless value.is_a?(String) && DECIMAL_AMOUNT.match?(value)
        raise InvalidAmount, "amount must be a decimal string with at most two fractional digits"
      end

      whole, fraction = value.split(".", 2)
      cents = whole.to_i * 100 + fraction.to_s.ljust(2, "0").to_i
      raise InvalidAmount, "amount must be greater than zero" unless cents.positive?
      raise AmountLimitExceeded, "amount exceeds the system transfer limit" if cents > MAX_TRANSFER_CENTS

      cents
    end

    def self.cents_from_decimal(value)
      decimal = BigDecimal(value.to_s)
      raise InvalidAmount, "amount must be finite" unless decimal.finite?
      raise InvalidAmount, "amount must have at most two fractional digits" unless (decimal * 100).frac.zero?

      (decimal * 100).to_i
    rescue ArgumentError
      raise InvalidAmount, "amount must be a valid decimal"
    end

    def self.decimal_from_cents(cents)
      BigDecimal(cents.to_i.to_s) / 100
    end

    def self.format_cents(cents)
      value = cents.to_i
      sign = value.negative? ? "-" : ""
      absolute_value = value.abs
      format("%s%d.%02d", sign, absolute_value / 100, absolute_value % 100)
    end
  end
end
