module Pix
  class DailyTransferLimit
    def self.used_cents(account:, now: Time.current)
      start_time = now.in_time_zone.beginning_of_day
      end_time = now.in_time_zone.end_of_day

      Transaction.where(source_account_id: account.id, cancelled_at: nil)
        .where.not(status: [ :cancelled, :failed ])
        .where(created_at: start_time.utc..end_time.utc)
        .sum(:amount_cents)
    end

    def self.limit_cents(account:)
      (account.daily_limit.to_d * 100).to_i
    end

    def self.remaining_cents(account:, now: Time.current)
      [ limit_cents(account: account) - used_cents(account: account, now: now), 0 ].max
    end
  end
end
