module Api
  module V1
    class PixKeysController < ApplicationController
      before_action :set_account, only: [ :create, :index ]
      before_action :set_pix_key, only: [ :destroy, :unblock ]
      before_action -> { require_api_scope!("pix_keys:write") }, only: [ :create, :destroy, :unblock ]
      before_action -> { require_api_scope!("pix_keys:read") }, only: [ :index, :lookup ]
      before_action -> { authorize_account!(@account) }, only: [ :create, :index ]
      before_action -> { authorize_account!(@pix_key.account) }, only: [ :destroy, :unblock ]

      def lookup
        raw_key = params[:key].to_s.strip
        if raw_key.blank?
          return render_error("Informe a chave Pix", status: :bad_request, code: "pix_key.blank")
        end

        normalized = normalize_lookup_key(raw_key)
        pix_key = PixKey.includes(account: :user).find_by(key_value: normalized, status: "active")
        unless pix_key && pix_key.account&.active? && !pix_key.account.fraud_blocked?
          return render_error("Chave Pix não encontrada ou conta de destino inativa.", status: :not_found, code: "pix_key.not_found")
        end

        source_account_id = params[:source_account_id].to_i
        if source_account_id.positive? && pix_key.account_id == source_account_id
          return render_error("Você não pode transferir para uma chave Pix da mesma conta.", status: :unprocessable_entity, code: "transfer.self_transfer")
        end

        if pix_key.account.user_id == current_user&.id
          return render_error("Você não pode transferir para uma chave Pix do próprio usuário.", status: :unprocessable_entity, code: "transfer.self_user_transfer")
        end

        render_success({
          key_type: pix_key.key_type,
          key_value: pix_key.key_value,
          account_id: pix_key.account.id,
          account_number: pix_key.account.account_number,
          agency_number: pix_key.account.agency_number,
          recipient_name: pix_key.account.user.name
        })
      end

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

      def normalize_lookup_key(value)
        digits = value.gsub(/\D/, "")
        return "+55#{digits}" if digits.length.in?([ 10, 11 ]) && value.start_with?("(")
        return digits if [ 11, 14 ].include?(digits.length) && !value.include?("@") && !value.start_with?("+")

        value.downcase
      end
    end
  end
end
