module Api
  module V1
    class PixKeysController < ApplicationController
      before_action :set_account, only: [:create, :index]
      before_action :set_pix_key, only: [:destroy]

      def create
        @pix_key = @account.pix_keys.build(pix_key_params)

        if @pix_key.save
          render_success(@pix_key, status: :created)
        else
          render_error(@pix_key.errors.to_hash)
        end
      rescue ActiveRecord::RecordNotUnique
        render_error("Key already registered", status: :unprocessable_entity)
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

      private

      def set_account
        @account = Account.find(params[:account_id])
      end

      def set_pix_key
        @pix_key = PixKey.find(params[:id])
      end

      def pix_key_params
        if params[:pix_key].present?
          params.require(:pix_key).permit(:key_type, :key_value)
        else
          params.permit(:key_type, :key_value)
        end
      end
    end
  end
end
