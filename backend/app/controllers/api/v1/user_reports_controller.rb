module Api
  module V1
    class UserReportsController < ApplicationController
      include ActionController::MimeResponds

      def transactions
        report = Reports::UserTransactionsService.call(user_id: params[:user_id])

        respond_to do |format|
          format.json { render_success(report) }
          format.csv do
            send_data Reports::UserTransactionsService.to_csv(report),
              type: "text/csv",
              disposition: "attachment",
              filename: "user_#{report[:user_id]}_transactions_report.csv"
          end
          format.any do
            render_error("The requested response format is not supported", status: :not_acceptable, code: "request.not_acceptable")
          end
        end
      end
    end
  end
end
