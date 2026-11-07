module Api
  module V1
    class TransfersController < ApplicationController
      before_action -> { require_api_scope!("transfers:write") }, only: :create
      before_action -> { require_api_scope!("transfers:read") }, only: :show
      before_action -> { require_api_scope!("transfers:write") }, only: :cancel

      def create
        source_account = current_user.accounts.find_by(id: transfer_params[:source_account_id])
        return render_not_found unless source_account

        e2e_id = request.headers["X-Idempotency-Key"].presence || transfer_params[:end_to_end_id].presence

        if e2e_id.present?
          existing_tx = Transaction.find_by(end_to_end_id: e2e_id)
          return render_not_found if existing_tx && !owns_transaction?(existing_tx)
          return render_success(receipt_data(existing_tx), status: :ok) if existing_tx
        end

        result = Pix::TransferService.call(
          source_account_id: transfer_params[:source_account_id],
          pix_key: transfer_params[:pix_key],
          amount: transfer_params[:amount],
          description: transfer_params[:description],
          end_to_end_id: e2e_id
        )

        if result.success?
          Security::AuditLogger.record(event: "transfer_created", actor: current_user, resource: result.transaction, request: request)
          render_success(receipt_data(result.transaction), status: :created)
        else
          render_error(result.error, status: :unprocessable_entity)
        end
      end

      def show
        transaction = if params[:id].to_s.start_with?("E")
                        Transaction.find_by(end_to_end_id: params[:id])
        else
                        Transaction.find_by(id: params[:id])
        end

        if transaction && owns_transaction?(transaction)
          render_success(receipt_data(transaction))
        else
          render_not_found
        end
      end

      def cancel
        transaction = Transaction.find_by(id: params[:id])
        return render_not_found unless transaction && owns_transaction?(transaction)

        result = Pix::CancelTransferService.call(transaction_id: params[:id], reason: params[:reason])

        if result.success?
          Security::AuditLogger.record(event: "transfer_cancelled", actor: current_user, resource: result.transaction, request: request)
          render_success(receipt_data(result.transaction))
        elsif result.error == "Transaction not found"
          render_error(result.error, status: :not_found)
        else
          render_error(result.error, status: :unprocessable_entity)
        end
      end

      private

      def owns_transaction?(transaction)
        transaction.source_account.user_id == current_user.id || transaction.destination_account.user_id == current_user.id
      end

      def masked_document(value)
        digits = value.to_s.gsub(/\D/, "")
        return "***" if digits.length < 5

        "#{"*" * (digits.length - 4)}#{digits.last(4)}"
      end

      def transfer_params
        params.fetch(:transfer, params).permit(
          :source_account_id,
          :pix_key,
          :amount,
          :description,
          :end_to_end_id
        )
      end

      def receipt_data(tx)
        {
          id: tx.id,
          end_to_end_id: tx.end_to_end_id,
          amount: tx.amount.to_f,
          description: tx.description,
          status: tx.status,
          cancelled_at: tx.cancelled_at,
          pix_key_used: tx.pix_key_used,
          created_at: tx.created_at,
          sender: {
            account_id: tx.source_account_id,
            account_number: tx.source_account.account_number,
            agency: tx.source_account.agency_number,
            user_name: tx.source_account.user.name,
            doc_id: masked_document(tx.source_account.user.doc_id)
          },
          receiver: {
            account_id: tx.destination_account_id,
            account_number: tx.destination_account.account_number,
            agency: tx.destination_account.agency_number,
            user_name: tx.destination_account.user.name,
            doc_id: masked_document(tx.destination_account.user.doc_id)
          }
        }
      end
    end
  end
end
