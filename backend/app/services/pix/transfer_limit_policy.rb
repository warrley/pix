module Pix
  class TransferLimitPolicy
    NIGHT_START_HOUR = 20
    NIGHT_END_HOUR = 6
    DEFAULT_DAILY_LIMIT_CENTS = 100_000
    DEFAULT_NIGHTLY_LIMIT_CENTS = 100_000

    def self.daily_limit_cents
      configured_limit("PIX_DAILY_TRANSFER_LIMIT_CENTS", DEFAULT_DAILY_LIMIT_CENTS)
    end

    def self.nightly_limit_cents
      configured_limit("PIX_NIGHTLY_TRANSFER_LIMIT_CENTS", DEFAULT_NIGHTLY_LIMIT_CENTS)
    end

    def self.configured_limit(name, default)
      value = ENV.fetch(name, default.to_s)
      raise ArgumentError, "#{name} must be a positive integer number of cents" unless value.match?(/\A[1-9]\d*\z/)

      value.to_i
    end
    private_class_method :configured_limit

    def initialize(source_account:, destination_account:, amount_cents:, now: Time.current)
      @source_account = source_account
      @destination_account = destination_account
      @amount_cents = amount_cents
      @now = now.in_time_zone("America/Sao_Paulo")
    end

    def error
      return daily_limit_error if daily_limit_exceeded?
      return nightly_limit_error if nightly_limit_exceeded?

      nil
    end

    private

    def daily_limit_exceeded?
      used_cents = completed_transfers_since(@now.beginning_of_day)
      used_cents + @amount_cents > self.class.daily_limit_cents
    end

    def nightly_limit_exceeded?
      return false unless natural_person?(@source_account.user) && natural_person?(@destination_account.user)
      return false unless night_period?

      start_time = @now.hour < NIGHT_END_HOUR ? (@now - 1.day).change(hour: NIGHT_START_HOUR) : @now.change(hour: NIGHT_START_HOUR)
      used_cents = completed_transfers_since(start_time)
      used_cents + @amount_cents > self.class.nightly_limit_cents
    end

    def completed_transfers_since(start_time)
      Transaction.where(source_account_id: @source_account.id, status: :completed)
        .where(created_at: start_time.utc..@now.utc)
        .sum(:amount_cents)
    end

    def natural_person?(user)
      user.doc_id.to_s.gsub(/\D/, "").length == 11
    end

    def night_period?
      @now.hour >= NIGHT_START_HOUR || @now.hour < NIGHT_END_HOUR
    end

    def daily_limit_error
      DomainError.new(code: "transfer.daily_limit_exceeded", message: "Daily transfer limit exceeded", http_status: :unprocessable_entity)
    end

    def nightly_limit_error
      DomainError.new(code: "transfer.nightly_limit_exceeded", message: "Nightly transfer limit exceeded", http_status: :unprocessable_entity)
    end
  end
end
