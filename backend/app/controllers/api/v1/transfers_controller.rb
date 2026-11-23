module Api
  module V1
    class TransfersController < ApplicationController
      wrap_parameters false
      before_action -> { require_api_scope!("transfers:write") }, only: [ :create, :cancel, :refund ]
      before_action -> { require_api_scope!("transfers:read") }, only: :show

      def create
        source_account = current_user.accounts.find_by(id: transfer_params[:source_account_id])
        return render_not_found unless source_account

        e2e_id = request.headers["X-Idempotency-Key"].presence || transfer_params[:end_to_end_id].presence

        if e2e_id.present?
          existing_tx = Transaction.find_by(end_to_end_id: e2e_id)
          return render_not_found if existing_tx && !owns_transaction?(existing_tx)
          return render_success(receipt_data(existing_tx), status: :ok) if existing_tx
        end

        amount_cents = Pix::Money.parse_cents(transfer_params[:amount])
        result = Pix::TransferService.call(
          source_account_id: transfer_params[:source_account_id],
          pix_key: transfer_params[:pix_key],
          amount_cents: amount_cents,
          description: transfer_params[:description],
          end_to_end_id: e2e_id,
          latitude: transfer_params[:latitude],
          longitude: transfer_params[:longitude]
        )

        if result.success?
          Security::AuditLogger.record(event: "transfer_created", actor: current_user, resource: result.transaction, request: request)
          render_success(receipt_data(result.transaction), status: :created)
        else
          render_error(result.error)
        end
      rescue Pix::Money::AmountLimitExceeded => error
        render_error(error.message, status: :unprocessable_entity, code: "amount.limit_exceeded")
      rescue Pix::Money::InvalidAmount => error
        render_error(error.message, status: :unprocessable_entity, code: "amount.invalid")
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
        return render_error("Transaction not found", status: :not_found, code: "transaction.not_found") unless transaction && owns_transaction?(transaction)

        result = Pix::CancelTransferService.call(transaction_id: params[:id], reason: params[:reason])

        if result.success?
          Security::AuditLogger.record(event: "transfer_cancelled", actor: current_user, resource: result.transaction, request: request)
          render_success(receipt_data(result.transaction))
        else
          render_error(result.error)
        end
      end

      def refund
        transaction = Transaction.find_by(id: params[:id])
        return render_error("Transaction not found", status: :not_found, code: "transaction.not_found") unless transaction && owns_transaction?(transaction)

        if params[:reason].blank?
          return render_error("Reason is required to refund a transaction", status: :unprocessable_entity, code: "refund.missing_reason")
        end

        refund_request = RefundRequest.new(
          pix_transaction: transaction,
          requested_by: current_user,
          category: params[:category].presence || "disagreement",
          reason: params[:reason].to_s.strip
        )

        if refund_request.save
          Security::AuditLogger.record(event: "refund_requested", actor: current_user, resource: refund_request, request: request)
          render_success({
            refund_request: {
              id: refund_request.id,
              transaction_id: refund_request.transaction_id,
              status: refund_request.status,
              category: refund_request.category,
              reason: refund_request.reason,
              created_at: refund_request.created_at
            },
            transaction: receipt_data(transaction),
            message: "Refund request submitted for administrative review"
          }, status: :accepted)
        else
          render_error(refund_request.errors.full_messages.to_sentence, status: :unprocessable_entity, code: "refund.invalid_request")
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

      def format_coordinate(value)
        value&.then { |coordinate| format("%.8f", coordinate) }
      end

      def transfer_params
        params.require(:transfer).permit(
          :source_account_id,
          :pix_key,
          :amount,
          :description,
          :end_to_end_id,
          :latitude,
          :longitude
        )
      end

      def receipt_data(tx)
        {
          id: tx.id,
          end_to_end_id: tx.end_to_end_id,
          amount: Pix::Money.format_cents(tx.amount_cents),
          description: tx.description,
          status: tx.status,
          cancelled_at: tx.cancelled_at,
          refunded_at: tx.refunded_at,
          pix_key_used: tx.pix_key_used,
          latitude: format_coordinate(tx.latitude),
          longitude: format_coordinate(tx.longitude),
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
