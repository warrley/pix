module Api
  module V1
    class AccountLimitsController < ApplicationController
      before_action :set_account
      before_action -> { require_api_scope!("accounts:read") }, only: :show
      before_action -> { require_api_scope!("accounts:write") }, only: :update
      before_action -> { authorize_account!(@account) }

      def show
        render_success(limit_data)
      end

      def update
        daily_limit = parsed_daily_limit
        return render_invalid_daily_limit unless daily_limit

        if @account.update(daily_limit: daily_limit)
          render_success(limit_data)
        else
          render_error(@account.errors.to_hash)
        end
      end

      private

      def set_account
        @account = Account.find(params[:id])
      end

      def parsed_daily_limit
        value = params.require(:daily_limit).to_s
        return unless value.match?(/\A(?:0|[1-9]\d*)(?:\.\d{1,2})?\z/)

        BigDecimal(value)
      end

      def render_invalid_daily_limit
        render_error(
          { daily_limit: [ "must be a non-negative amount with up to 2 decimal places" ] },
          status: :unprocessable_entity
        )
      end

      def limit_data
        used_cents = Pix::DailyTransferLimit.used_cents(account: @account)

        {
          daily_limit: Pix::Money.format_cents(Pix::DailyTransferLimit.limit_cents(account: @account)),
          used_today: Pix::Money.format_cents(used_cents),
          remaining: Pix::Money.format_cents([ Pix::DailyTransferLimit.limit_cents(account: @account) - used_cents, 0 ].max)
        }
      end
    end
  end
end
