module Api
  module V1
    module Admin
      class RefundRequestsController < BaseController
        before_action :set_refund_request, only: [ :approve, :reject ]

        def index
          page, per_page = pagination_params
          scope = RefundRequest.includes(
            :requested_by,
            :reviewed_by,
            pix_transaction: [ { source_account: :user }, { destination_account: :user } ]
          ).order(created_at: :desc)

          scope = scope.where(status: params[:status]) if RefundRequest.statuses.key?(params[:status])

          total = scope.count
          items = scope.offset((page - 1) * per_page).limit(per_page)

          render_success({
            items: items.map { |req| serialize_request(req) },
            pagination: {
              page: page,
              per_page: per_page,
              total: total,
              total_pages: (total.to_f / per_page).ceil
            }
          })
        end

        def approve
          unless @refund_request.pending?
            return render_error("Refund request was already #{@refund_request.status}", status: :unprocessable_entity)
          end

          result = Pix::RefundService.call(
            transaction_id: @refund_request.transaction_id,
            reason: @refund_request.reason
          )

          unless result.success?
            return render_error(result.error)
          end

          @refund_request.update!(
            status: :approved,
            reviewed_by: current_user,
            decision_note: params[:decision_note].to_s.strip.presence,
            reviewed_at: Time.current
          )

          Security::AuditLogger.record(event: "transfer_refunded", actor: current_user, resource: result.transaction, request: request)
          log_admin_action!(
            action: "refund_approved",
            target: @refund_request,
            details: {
              transaction_id: @refund_request.transaction_id,
              amount_cents: result.transaction.amount_cents,
              decision_note: @refund_request.decision_note
            }
          )

          render_success(serialize_request(@refund_request))
        end

        def reject
          unless @refund_request.pending?
            return render_error("Refund request was already #{@refund_request.status}", status: :unprocessable_entity)
          end

          note = params[:decision_note].to_s.strip
          if note.blank?
            return render_error("Decision note is required when rejecting a refund request", status: :unprocessable_entity)
          end

          @refund_request.update!(
            status: :rejected,
            reviewed_by: current_user,
            decision_note: note,
            reviewed_at: Time.current
          )

          Security::AuditLogger.record(event: "refund_rejected", actor: current_user, resource: @refund_request, request: request)
          log_admin_action!(
            action: "refund_rejected",
            target: @refund_request,
            details: {
              transaction_id: @refund_request.transaction_id,
              decision_note: note
            }
          )

          render_success(serialize_request(@refund_request))
        end

        private

        def set_refund_request
          @refund_request = RefundRequest.find(params[:id])
        end

        def serialize_request(req)
          tx = req.pix_transaction
          {
            id: req.id,
            status: req.status,
            category: req.category,
            reason: req.reason,
            decision_note: req.decision_note,
            reviewed_at: req.reviewed_at&.iso8601,
            created_at: req.created_at.iso8601,
            requested_by: {
              id: req.requested_by.id,
              name: req.requested_by.name,
              email: req.requested_by.email
            },
            reviewed_by: req.reviewed_by ? { id: req.reviewed_by.id, name: req.reviewed_by.name } : nil,
            transaction: {
              id: tx.id,
              end_to_end_id: tx.end_to_end_id,
              amount_cents: tx.amount_cents,
              status: tx.status,
              source_owner: tx.source_account.user.name,
              source_account: tx.source_account.account_number,
              destination_owner: tx.destination_account.user.name,
              destination_account: tx.destination_account.account_number,
              destination_balance_cents: tx.destination_account.balance_cents
            }
          }
        end
      end
    end
  end
end
