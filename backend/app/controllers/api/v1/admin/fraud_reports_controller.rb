module Api
  module V1
    module Admin
      class FraudReportsController < BaseController
        before_action :set_fraud_report, only: [ :resolve ]

        def index
          page, per_page = pagination_params
          scope = FraudReport.includes(
            :reporter,
            :reviewed_by,
            :pix_transaction,
            reported_account: :user
          ).order(created_at: :desc)

          scope = scope.where(status: params[:status]) if FraudReport.statuses.key?(params[:status])

          total = scope.count
          items = scope.offset((page - 1) * per_page).limit(per_page)
          account_ids = items.map(&:reported_account_id).uniq
          report_counts = FraudReport.where(reported_account_id: account_ids).group(:reported_account_id).count

          render_success({
            items: items.map { |report| serialize_report(report, report_counts[report.reported_account_id].to_i) },
            pagination: {
              page: page,
              per_page: per_page,
              total: total,
              total_pages: (total.to_f / per_page).ceil
            }
          })
        end

        def resolve
          target_status = params[:status].to_s
          unless %w[resolved dismissed under_review].include?(target_status)
            return render_error("Status must be resolved, dismissed, or under_review", status: :unprocessable_entity)
          end

          note = params[:resolution_note].to_s.strip
          if %w[resolved dismissed].include?(target_status) && note.blank?
            return render_error("Resolution note is required", status: :unprocessable_entity)
          end

          ActiveRecord::Base.transaction do
            @fraud_report.update!(
              status: target_status,
              reviewed_by: current_user,
              resolution_note: note.presence,
              reviewed_at: Time.current
            )

            if ActiveModel::Type::Boolean.new.cast(params[:block_account]) && !@fraud_report.reported_account.fraud_blocked?
              @fraud_report.reported_account.update!(
                fraud_blocked: true,
                fraud_blocked_at: Time.current,
                fraud_block_reason: note.presence || "Blocked via fraud report ##{@fraud_report.id}"
              )
              Security::AuditLogger.record(event: "account_fraud_blocked", actor: current_user, resource: @fraud_report.reported_account, request: request)
              log_admin_action!(
                action: "account_fraud_blocked",
                target: @fraud_report.reported_account,
                details: { reason: @fraud_report.reported_account.fraud_block_reason, fraud_report_id: @fraud_report.id }
              )
            end

            log_admin_action!(
              action: "fraud_report_#{target_status}",
              target: @fraud_report,
              details: {
                reported_account_id: @fraud_report.reported_account_id,
                resolution_note: note,
                block_account: ActiveModel::Type::Boolean.new.cast(params[:block_account])
              }
            )
          end

          total_reports = FraudReport.where(reported_account_id: @fraud_report.reported_account_id).count
          render_success(serialize_report(@fraud_report, total_reports))
        end

        private

        def set_fraud_report
          @fraud_report = FraudReport.find(params[:id])
        end

        def serialize_report(report, account_reports_count)
          acc = report.reported_account
          {
            id: report.id,
            status: report.status,
            category: report.category,
            description: report.description,
            resolution_note: report.resolution_note,
            reviewed_at: report.reviewed_at&.iso8601,
            created_at: report.created_at.iso8601,
            reporter: {
              id: report.reporter.id,
              name: report.reporter.name,
              email: report.reporter.email
            },
            reported_account: {
              id: acc.id,
              account_number: acc.account_number,
              status: acc.status,
              fraud_blocked: acc.fraud_blocked,
              fraud_block_reason: acc.fraud_block_reason,
              balance_cents: acc.balance_cents,
              owner_id: acc.user_id,
              owner_name: acc.user.name,
              owner_email: acc.user.email,
              total_reports_count: account_reports_count
            },
            transaction: report.pix_transaction ? {
              id: report.pix_transaction.id,
              end_to_end_id: report.pix_transaction.end_to_end_id,
              amount_cents: report.pix_transaction.amount_cents,
              status: report.pix_transaction.status
            } : nil
          }
        end
      end
    end
  end
end
