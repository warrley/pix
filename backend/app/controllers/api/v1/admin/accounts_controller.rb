module Api
  module V1
    module Admin
      class AccountsController < BaseController
        before_action :set_account

        def block_fraud
          reason = params[:reason].to_s.strip
          return render_error("Reason is required", status: :unprocessable_entity) if reason.blank?

          if @account.update(fraud_blocked: true, fraud_blocked_at: Time.current, fraud_block_reason: reason)
            Security::AuditLogger.record(event: "account_fraud_blocked", actor: current_user, resource: @account, request: request)
            log_admin_action!(action: "account_fraud_blocked", target: @account, details: { reason: reason })
            render_success(serialize_account(@account))
          else
            render_error(@account.errors.full_messages.to_sentence, status: :unprocessable_entity)
          end
        end

        def unblock_fraud
          if @account.update(fraud_blocked: false, fraud_blocked_at: nil, fraud_block_reason: nil)
            Security::AuditLogger.record(event: "account_fraud_unblocked", actor: current_user, resource: @account, request: request)
            log_admin_action!(action: "account_fraud_unblocked", target: @account)
            render_success(serialize_account(@account))
          else
            render_error(@account.errors.full_messages.to_sentence, status: :unprocessable_entity)
          end
        end

        def update_limit
          limit = params[:daily_limit]
          if limit.blank? || limit.to_d <= 0
            return render_error("daily_limit must be greater than zero", status: :unprocessable_entity)
          end

          old_limit = @account.daily_limit.to_s
          if @account.update(daily_limit: limit.to_d)
            log_admin_action!(
              action: "account_limit_updated",
              target: @account,
              details: { previous_limit: old_limit, new_limit: @account.daily_limit.to_s }
            )
            render_success(serialize_account(@account))
          else
            render_error(@account.errors.full_messages.to_sentence, status: :unprocessable_entity)
          end
        end

        def close
          unless @account.balance_cents.zero?
            return render_error("Cannot close account with existing balance", status: :conflict)
          end

          ActiveRecord::Base.transaction do
            @account.pix_keys.where.not(status: "cancelled").find_each(&:cancelled!)
            @account.update!(status: "closed")
          end

          Security::AuditLogger.record(event: "account_closed", actor: current_user, resource: @account, request: request)
          log_admin_action!(action: "account_closed", target: @account)
          render_success(serialize_account(@account))
        end

        def reopen
          if @account.update(status: "active")
            log_admin_action!(action: "account_reopened", target: @account)
            render_success(serialize_account(@account))
          else
            render_error(@account.errors.full_messages.to_sentence, status: :unprocessable_entity)
          end
        end

        private

        def set_account
          @account = Account.find(params[:id])
        end

        def serialize_account(account)
          {
            id: account.id,
            account_number: account.account_number,
            status: account.status,
            fraud_blocked: account.fraud_blocked,
            fraud_block_reason: account.fraud_block_reason,
            balance_cents: account.balance_cents,
            daily_limit: account.daily_limit.to_s
          }
        end
      end
    end
  end
end
