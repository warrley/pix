module Api
  module V1
    class AccountsController < ApplicationController
      before_action :set_account, only: [:show]

      # GET /api/v1/users/:user_id/accounts
      def index
        user = User.find(params[:user_id])
        render_success(user.accounts)
      end

      # GET /api/v1/accounts/:id
      def show
        render_success(@account)
      end

      private

      def set_account
        @account = Account.find(params[:id])
      end
    end
  end
end
