module Api
  module V1
    module Admin
      class PixKeysController < BaseController
        before_action :set_pix_key, only: [ :unblock, :suspend, :destroy ]

        def index
          page, per_page = pagination_params
          scope = PixKey.includes(account: :user).order(created_at: :desc)
          scope = scope.where(status: params[:status]) if PixKey.statuses.key?(params[:status])
          if params[:search].present?
            q = "%#{params[:search].to_s.strip}%"
            scope = scope.where("key_value ILIKE :q", q: q)
          end

          total = scope.count
          items = scope.offset((page - 1) * per_page).limit(per_page)

          render_success({
            items: items.map { |key| serialize_key(key) },
            pagination: {
              page: page,
              per_page: per_page,
              total: total,
              total_pages: (total.to_f / per_page).ceil
            }
          })
        end

        def unblock
          result = Pix::KeyLifecycleService.unblock(@pix_key)
          unless result.success?
            return render_error(result.error)
          end

          log_admin_action!(action: "pix_key_unblocked", target: @pix_key, details: { key_value: @pix_key.key_value })
          render_success(serialize_key(result.pix_key))
        end

        def suspend
          unless @pix_key.active?
            return render_error("Only active keys can be suspended", status: :unprocessable_entity)
          end

          @pix_key.suspended!
          log_admin_action!(action: "pix_key_suspended", target: @pix_key, details: { key_value: @pix_key.key_value })
          render_success(serialize_key(@pix_key))
        end

        def destroy
          @pix_key.cancelled!
          log_admin_action!(action: "pix_key_cancelled", target: @pix_key, details: { key_value: @pix_key.key_value })
          render_success(serialize_key(@pix_key))
        end

        private

        def set_pix_key
          @pix_key = PixKey.find(params[:id])
        end

        def serialize_key(key)
          {
            id: key.id,
            key_type: key.key_type,
            key_value: key.key_value,
            status: key.status,
            account_id: key.account_id,
            account_number: key.account.account_number,
            owner_name: key.account.user.name,
            owner_email: key.account.user.email,
            created_at: key.created_at.iso8601
          }
        end
      end
    end
  end
end
