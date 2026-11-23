module Api
  module V1
    module Admin
      class UsersController < BaseController
        before_action :set_user

        def show
          accounts = @user.accounts.includes(:pix_keys).order(:id)
          account_ids = accounts.map(&:id)
          reports_count = FraudReport.where(reported_account_id: account_ids).count

          render_success({
            id: @user.id,
            name: @user.name,
            email: @user.email,
            phone: @user.phone,
            role: @user.role,
            active_sessions_count: @user.api_access_tokens.active.count,
            fraud_reports_count: reports_count,
            created_at: @user.created_at.iso8601,
            accounts: accounts.map do |account|
              {
                id: account.id,
                account_number: account.account_number,
                agency_number: account.agency_number,
                status: account.status,
                fraud_blocked: account.fraud_blocked,
                fraud_block_reason: account.fraud_block_reason,
                balance_cents: account.balance_cents,
                daily_limit: account.daily_limit.to_s,
                pix_keys: account.pix_keys.map do |key|
                  {
                    id: key.id,
                    key_type: key.key_type,
                    key_value: key.key_value,
                    status: key.status
                  }
                end
              }
            end
          })
        end

        def update
          permitted = params.permit(:name, :phone)
          new_role = params[:role].to_s.presence
          if new_role.present?
            if @user.id == current_user.id && new_role != current_user.role
              return render_error("Cannot change your own administrative role", status: :unprocessable_entity)
            end
            unless %w[customer operator].include?(new_role)
              return render_error("Invalid role", status: :unprocessable_entity)
            end

            @user.role = new_role
          end

          if @user.update(permitted)
            log_admin_action!(
              action: "user_updated",
              target: @user,
              details: permitted.to_h.merge(role: @user.role)
            )
            render_success({
              id: @user.id,
              name: @user.name,
              email: @user.email,
              phone: @user.phone,
              role: @user.role
            })
          else
            render_error(@user.errors.full_messages.to_sentence, status: :unprocessable_entity)
          end
        end

        def revoke_sessions
          revoked_count = @user.api_access_tokens.active.update_all(revoked_at: Time.current)
          Security::AuditLogger.record(event: "user_sessions_revoked", actor: current_user, resource: @user, request: request)
          log_admin_action!(
            action: "user_sessions_revoked",
            target: @user,
            details: { revoked_count: revoked_count }
          )

          render_success({ user_id: @user.id, revoked_count: revoked_count })
        end

        private

        def set_user
          @user = User.find(params[:id])
        end
      end
    end
  end
end
