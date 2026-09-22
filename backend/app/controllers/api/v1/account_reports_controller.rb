module Api
  module V1
    class AccountReportsController < ApplicationController
      before_action :set_account

      def statement
        result = Reports::AccountPeriodService.call(
          account_id: @account.id,
          start_date: params[:start_date],
          end_date: params[:end_date],
          month: params[:month],
          year: params[:year]
        )

        unless result.success?
          return render_error(result.error, status: :unprocessable_entity)
        end

        if params[:format].to_s.downcase == "csv" || request.format.csv?
          csv_data = Reports::AccountPeriodService.generate_csv(result.data)
          filename = "statement_#{@account.account_number}_#{result.data.dig(:period, :start_date)}_#{result.data.dig(:period, :end_date)}.csv"
          send_data csv_data, type: "text/csv; charset=utf-8", disposition: "attachment; filename=#{filename}"
        else
          render_success(result.data)
        end
      end

      private

      def set_account
        @account = Account.find(params[:account_id])
      end
    end
  end
end
