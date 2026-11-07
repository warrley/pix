module Api
  module V1
    class AccountsController < ApplicationController
      before_action :set_account, only: [ :show, :destroy ]
      before_action -> { require_api_scope!("accounts:read") }, only: [ :index, :show ]
      before_action -> { require_api_scope!("accounts:write") }, only: [ :create, :destroy ]
      before_action -> { authorize_account!(@account) }, only: [ :show, :destroy ]

      def create
        account = current_user.accounts.build(account_params)

        if account.save
          render_success(account, status: :created)
        else
          render_error(account.errors.to_hash)
        end
      end

      def index
        return render_not_found unless params[:user_id].to_i == current_user.id

        render_success(current_user.accounts)
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
        params.require(:account).permit(:agency_number)
      end
    end
  end
end
