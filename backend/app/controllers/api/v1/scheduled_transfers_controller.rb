module Api
  module V1
    class ScheduledTransfersController < ApplicationController
      before_action -> { require_api_scope!(action_name == "index" ? "transfers:read" : "transfers:write") }

      def index
        account = current_user.accounts.find_by(id: params[:account_id])
        return render_error("Account not found", status: :not_found, code: "account.not_found") unless account

        transfers = account.scheduled_transfers.order(:scheduled_for, :id)
        render_success(transfers.as_json)
      end

      def create
        account = current_user.accounts.find_by(id: params[:account_id])
        return render_error("Account not found", status: :not_found, code: "account.not_found") unless account

        input = scheduled_transfer_params
        amount_cents = Pix::Money.parse_cents(input[:amount])
        unless input[:scheduled_for].to_s.match?(/\A\d{4}-\d{2}-\d{2}\z/)
          return render_error("scheduled_for must be a valid YYYY-MM-DD date", status: :unprocessable_entity, code: "scheduled_transfer.invalid_date")
        end
        date = Date.iso8601(input[:scheduled_for])
        key = PixKey.active.find_by(key_value: Pix::TransferService.normalize_key_value(input[:pix_key].to_s.strip))
        return render_error("PIX key not found", status: :unprocessable_entity, code: "pix_key.not_found") unless key
        scheduled_transfer = account.scheduled_transfers.new(
          pix_key_used: input[:pix_key],
          destination_account: key.account,
          amount_cents: amount_cents,
          description: input[:description],
          scheduled_for: date
        )

        if scheduled_transfer.save
          Security::AuditLogger.record(event: "scheduled_transfer_created", actor: current_user, resource: scheduled_transfer, request: request)
          render_success(scheduled_transfer.as_json, status: :created)
        else
          render_error(scheduled_transfer.errors.to_hash)
        end
      rescue Pix::Money::AmountLimitExceeded => error
        render_error(error.message, status: :unprocessable_entity, code: "amount.limit_exceeded")
      rescue Pix::Money::InvalidAmount => error
        render_error(error.message, status: :unprocessable_entity, code: "amount.invalid")
      rescue Date::Error, TypeError
        render_error("scheduled_for must be a valid YYYY-MM-DD date", status: :unprocessable_entity, code: "scheduled_transfer.invalid_date")
      end

      def destroy
        transfer = ScheduledTransfer.joins(:account).where(accounts: { user_id: current_user.id }).find_by(id: params[:id])
        return render_error("Scheduled transfer not found", status: :not_found, code: "scheduled_transfer.not_found") unless transfer

        cancelled = transfer.with_lock do
          next false unless transfer.pending?

          transfer.update!(status: :cancelled)
        end
        return render_error("Scheduled transfer cannot be cancelled", status: :unprocessable_entity, code: "scheduled_transfer.not_cancellable") unless cancelled

        Security::AuditLogger.record(event: "scheduled_transfer_cancelled", actor: current_user, resource: transfer, request: request)
        render_success(transfer.as_json)
      end

      private

      def scheduled_transfer_params
        params.require(:scheduled_transfer).permit(:pix_key, :amount, :description, :scheduled_for)
      end
    end
  end
end
