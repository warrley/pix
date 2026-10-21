module Api
  module V1
    class AuthController < ApplicationController
      DUMMY_PASSWORD_DIGEST = Security::PasswordHasher.create(SecureRandom.hex(32))

      skip_before_action :authenticate_api_user!, only: :create
      rate_limit to: 5, within: 15.minutes, name: "login_by_identity",
        by: -> { [ request.remote_ip, params[:email].to_s.downcase.first(254) ] },
        with: -> { render json: { data: nil, error: { code: "rate_limited", message: "Too many attempts" } }, status: :too_many_requests },
        only: :create
      rate_limit to: 20, within: 1.minute, name: "login_by_ip",
        with: -> { render json: { data: nil, error: { code: "rate_limited", message: "Too many attempts" } }, status: :too_many_requests },
        only: :create

      def create
        if params.key?(:scopes) && !params[:scopes].is_a?(Array)
          return render json: {
            data: nil,
            error: { code: "invalid_scope", message: "Scopes must be an array" }
          }, status: :bad_request
        end

        email = params[:email].to_s.strip.downcase
        user = User.find_by(email: email) if email.bytesize <= 254
        password = params[:password].to_s
        if user&.password_digest.present? && password.bytesize <= 128
          authenticated = user.authenticate_password(password)
        else
          Security::PasswordHasher.valid?(password.byteslice(0, 128), DUMMY_PASSWORD_DIGEST)
          authenticated = false
        end
        unless authenticated
          Security::AuditLogger.record(event: "authentication_login_rejected", request: request)
          return render_invalid_credentials
        end

        requested_scopes = params[:scopes] if params[:scopes].is_a?(Array)
        if requested_scopes&.empty?
          return render json: {
            data: nil,
            error: { code: "invalid_scope", message: "At least one scope is required" }
          }, status: :bad_request
        end
        allowed_scopes = user.operator? ? ApiAccessToken::OPERATOR_SCOPES : ApiAccessToken::DEFAULT_SCOPES
        if requested_scopes && !(requested_scopes - allowed_scopes).empty?
          return render json: {
            data: nil,
            error: { code: "invalid_scope", message: "Requested scope is not available" }
          }, status: :bad_request
        end

        raw_token, access_token = ApiAccessToken.issue!(user: user, scopes: requested_scopes)
        Security::AuditLogger.record(event: "authentication_succeeded", actor: user, request: request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["Pragma"] = "no-cache"
        render_success({
          access_token: raw_token,
          token_type: "Bearer",
          expires_at: access_token.expires_at.iso8601,
          scopes: access_token.scopes
        }, status: :created)
      end

      def destroy
        current_api_token.revoke!
        head :no_content
      end

      private

      def render_invalid_credentials
        render json: {
          data: nil,
          error: { code: "invalid_credentials", message: "Invalid email or password" }
        }, status: :unauthorized
      end
    end
  end
end
