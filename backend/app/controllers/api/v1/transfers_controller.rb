module Api
  module V1
    class TransfersController < ApplicationController
      def create
        e2e_id = request.headers["X-Idempotency-Key"].presence || transfer_params[:end_to_end_id].presence

        if e2e_id.present?
          existing_tx = Transaction.find_by(end_to_end_id: e2e_id)
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

        if transaction
          render_success(receipt_data(transaction))
        else
          render_not_found
        end
      end

      private

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
          pix_key_used: tx.pix_key_used,
          created_at: tx.created_at,
          sender: {
            account_id: tx.source_account_id,
            account_number: tx.source_account.account_number,
            agency: tx.source_account.agency_number,
            user_name: tx.source_account.user.name,
            doc_id: tx.source_account.user.doc_id
          },
          receiver: {
            account_id: tx.destination_account_id,
            account_number: tx.destination_account.account_number,
            agency: tx.destination_account.agency_number,
            user_name: tx.destination_account.user.name,
            doc_id: tx.destination_account.user.doc_id
          }
        }
      end
    end
  end
end
