module Api
  module V1
    class AccountSecurityController < ApplicationController
      before_action :set_account

      def block_fraud
        reason = params[:reason].presence || params.dig(:account, :reason).presence

        unless reason.present?
          return render_error("Reason is required", status: :unprocessable_entity)
        end

        if @account.update(fraud_blocked: true, fraud_blocked_at: Time.current, fraud_block_reason: reason)
          render_success(@account)
        else
          render_error(@account.errors.to_hash)
        end
      end

      def unblock_fraud
        if @account.update(fraud_blocked: false, fraud_blocked_at: nil, fraud_block_reason: nil)
          render_success(@account)
        else
          render_error(@account.errors.to_hash)
        end
      end

      private

      def set_account
        @account = Account.find(params[:id])
      end
    end
  end
end
