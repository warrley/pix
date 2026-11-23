module Api
  module V1
    class FraudReportsController < ApplicationController
      before_action -> { require_api_scope!("accounts:read") }

      def index
        reports = FraudReport.where(reporter: current_user).order(created_at: :desc).limit(50)
        render_success(reports.map { |report| serialize_report(report) })
      end

      def create
        reported_account = resolve_reported_account
        return render_error("Reported account not found", status: :not_found, code: "fraud_report.account_not_found") unless reported_account

        report = FraudReport.new(
          reporter: current_user,
          reported_account: reported_account,
          transaction_id: report_params[:transaction_id],
          category: report_params[:category].presence || "scam",
          description: report_params[:description].to_s.strip
        )

        if report.save
          Security::AuditLogger.record(event: "fraud_report_created", actor: current_user, resource: report, request: request)
          render_success(serialize_report(report), status: :created)
        else
          render_error(report.errors.full_messages.to_sentence, status: :unprocessable_entity, code: "fraud_report.invalid")
        end
      end

      private

      def report_params
        params.permit(:reported_account_id, :pix_key, :transaction_id, :category, :description)
      end

      def resolve_reported_account
        if report_params[:reported_account_id].present?
          Account.find_by(id: report_params[:reported_account_id])
        elsif report_params[:pix_key].present?
          PixKey.find_by(key_value: report_params[:pix_key].to_s.strip.downcase, status: "active")&.account
        elsif report_params[:transaction_id].present?
          tx = Transaction.find_by(id: report_params[:transaction_id])
          return nil unless tx && (tx.source_account.user_id == current_user.id || tx.destination_account.user_id == current_user.id)

          tx.source_account.user_id == current_user.id ? tx.destination_account : tx.source_account
        end
      end

      def serialize_report(report)
        {
          id: report.id,
          reported_account_id: report.reported_account_id,
          transaction_id: report.transaction_id,
          category: report.category,
          description: report.description,
          status: report.status,
          resolution_note: report.resolution_note,
          reviewed_at: report.reviewed_at,
          created_at: report.created_at
        }
      end
    end
  end
end
