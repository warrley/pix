module Api
  module V1
    class AccountsController < ApplicationController
      before_action :set_account, only: [:show, :destroy]

      def create
        account = Account.new(account_params)

        if account.save
          render_success(account, status: :created)
        else
          render_error(account.errors.to_hash)
        end
      end

      def index
        user = User.find(params[:user_id])
        render_success(user.accounts)
      end

      def show
        render_success(@account)
      end

      def destroy
        if @account.status == "closed"
          return render_error("Account is already closed", status: :unprocessable_entity)
        end

        if @account.balance > 0
          return render_error("Cannot close account with existing balance", status: :bad_request)
        end

        if @account.update(status: "closed")
          head :no_content
        else
          render_error(@account.errors.to_hash)
        end
      end

      private

      def set_account
        @account = Account.find(params[:id])
      end

      def account_params
        params.require(:account).permit(:user_id, :agency_number)
      end
    end
  end
end
