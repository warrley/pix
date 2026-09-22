module Api
  module V1
    class AccountTransfersController < ApplicationController
      def index
        account = Account.find_by(id: params[:account_id])

        unless account
          render json: { error: "Account not found" }, status: :not_found
          return
        end

        result = Pix::AccountStatementQuery.call(
          account_id: account.id,
          page: params[:page],
          per_page: params[:per_page],
          start_date: params[:start_date],
          end_date: params[:end_date],
          status: params[:status]
        )

        render json: result, status: :ok
      rescue ArgumentError => e
        render json: { error: e.message }, status: :bad_request
      end
    end
  end
end
