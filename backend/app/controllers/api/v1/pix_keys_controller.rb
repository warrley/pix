module Api
  module V1
    class PixKeysController < ApplicationController
      before_action :set_account, only: [ :create, :index ]
      before_action :set_pix_key, only: [ :destroy, :unblock ]
      before_action -> { require_api_scope!("pix_keys:write") }, only: [ :create, :destroy, :unblock ]
      before_action -> { require_api_scope!("pix_keys:read") }, only: :index
      before_action -> { authorize_account!(@account) }, only: [ :create, :index ]
      before_action -> { authorize_account!(@pix_key.account) }, only: [ :destroy, :unblock ]

      def create
        @pix_key = @account.pix_keys.build(pix_key_params)

        if @pix_key.save
          Observability::Metrics.record_pix_key_operation(operation: "create", key_type: @pix_key.key_type, status: "success")
          render_success(@pix_key, status: :created)
        else
          Observability::Metrics.record_pix_key_operation(operation: "create", key_type: @pix_key.key_type, status: "failure")
          render_error(@pix_key.errors.to_hash)
        end
      rescue ActiveRecord::RecordNotUnique
        Observability::Metrics.record_pix_key_operation(operation: "create", key_type: @pix_key&.key_type, status: "failure")
        render_error("Key already registered", status: :conflict, code: "pix_key.already_registered")
      end

      def index
        active_keys = @account.pix_keys.where(status: "active")
        render_success(active_keys)
      end

      def destroy
        if @pix_key.cancelled!
          Observability::Metrics.record_pix_key_operation(operation: "cancel", key_type: @pix_key.key_type, status: "success")
          head :no_content
        else
          Observability::Metrics.record_pix_key_operation(operation: "cancel", key_type: @pix_key.key_type, status: "failure")
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
    end
  end
end
