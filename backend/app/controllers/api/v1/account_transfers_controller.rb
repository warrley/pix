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
          per_page: params[:per_page]
        )

        render json: result, status: :ok
      end
    end
  end
end
