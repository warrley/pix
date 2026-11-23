module Api
  module V1
    module Admin
      class BaseController < ApplicationController
        before_action :authorize_operator!
        before_action -> { require_api_scope!("users:admin") }
        before_action -> { require_api_scope!("accounts:admin") }
        before_action -> { require_api_scope!("reports:read") }

        private

        def log_admin_action!(action:, target:, details: {})
          AdminAuditLog.record!(
            operator: current_user,
            action: action,
            target: target,
            details: details,
            request: request
          )
        end

        def pagination_params
          per_page = [ [ params.fetch(:per_page, 20).to_i, 100 ].min, 1 ].max
          page = [ params.fetch(:page, 1).to_i, 1 ].max
          [ page, per_page ]
        end
      end
    end
  end
end
