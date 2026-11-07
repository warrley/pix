class ApplicationController < ActionController::API
  before_action :authenticate_api_user!

  rescue_from ActiveRecord::RecordNotFound, with: :render_not_found

  rescue_from ActionController::ParameterMissing do |e|
    render_error(e.message, status: :bad_request)
  end

  private

  attr_reader :current_user, :current_api_token

  def authenticate_api_user!
    scheme, raw_token = request.authorization.to_s.split(" ", 2)
    token = ApiAccessToken.authenticate(raw_token) if scheme&.casecmp("Bearer")&.zero?
    unless token
      Security::AuditLogger.record(event: "authentication_rejected", request: request)
      return render_authentication_required
    end

    @current_api_token = token
    @current_user = token.user
  end

  def require_api_scope!(scope)
    return if current_api_token&.scope?(scope)

    Security::AuditLogger.record(event: "authorization_scope_rejected", actor: current_user, request: request)
    render json: { data: nil, error: { code: "insufficient_scope", message: "Insufficient permission" } },
      status: :forbidden
  end

  def authorize_user!(user)
    return if current_user&.id == user.id

    Security::AuditLogger.record(event: "authorization_user_rejected", actor: current_user, resource: user, request: request)
    render_not_found
  end

  def authorize_account!(account)
    return if current_user&.id == account.user_id

    Security::AuditLogger.record(event: "authorization_account_rejected", actor: current_user, resource: account, request: request)
    render_not_found
  end

  def authorize_operator!
    return if current_user&.operator?

    Security::AuditLogger.record(event: "authorization_operator_rejected", actor: current_user, request: request)
    render json: { data: nil, error: { code: "forbidden", message: "Operator access required" } },
      status: :forbidden
  end

  def render_authentication_required
    render json: { data: nil, error: { code: "authentication_required", message: "Bearer token required" } },
      status: :unauthorized
  end

  def render_success(data, status: :ok)
    render json: { data: data, error: nil }, status: status
  end

  def render_error(error, status: :unprocessable_entity)
    render json: { data: nil, error: error }, status: status
  end

  def render_not_found
    render json: { data: nil, error: "Record not found" }, status: :not_found
  end
end
