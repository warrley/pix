module Api
  module V1
    class PixKeysController < ApplicationController
      before_action :set_account, only: [ :create, :index ]
      before_action :set_pix_key, only: [ :destroy, :unblock, :reassociate ]
      before_action -> { require_api_scope!("pix_keys:write") }, only: [ :create, :destroy, :unblock, :reassociate ]
      before_action -> { require_api_scope!("pix_keys:read") }, only: :index
      before_action -> { authorize_account!(@account) }, only: [ :create, :index ]
      before_action -> { authorize_account!(@pix_key.account) }, only: [ :destroy, :unblock, :reassociate ]

      def create
        @pix_key = @account.pix_keys.build(pix_key_params)

        if @pix_key.save
          render_success(@pix_key, status: :created)
        else
          render_error(@pix_key.errors.to_hash)
        end
      rescue ActiveRecord::RecordNotUnique
        render_error("Key already registered", status: :conflict, code: "pix_key.already_registered")
      end

      def index
        active_keys = @account.pix_keys.where(status: "active")
        render_success(active_keys)
      end

      def destroy
        if @pix_key.cancelled!
          head :no_content
        else
          render_error(@pix_key.errors.to_hash)
        end
      end

      def unblock
        result = Pix::KeyLifecycleService.unblock(@pix_key)

        if result.success?
          render_success(result.pix_key)
        else
          render_error(result.error)
        end
      end

      def reassociate
        target_account = Account.find(reassociate_account_id)
        result = Pix::KeyLifecycleService.reassociate(@pix_key, target_account)

        if result.success?
          render_success(result.pix_key)
        else
          render_error(result.error)
        end
      end

      private

      def set_account
        @account = Account.find(params[:account_id])
      end

      def set_pix_key
        @pix_key = PixKey.find(params[:id])
      end

      def pix_key_params
        params.require(:pix_key).permit(:key_type, :key_value)
      end

      def reassociate_account_id
        params[:account_id].presence || params.dig(:pix_key, :account_id)
      end
    end
  end
end
