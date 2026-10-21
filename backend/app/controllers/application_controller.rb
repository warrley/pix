class ApplicationController < ActionController::API
  before_action :validate_request_content_type
  before_action :validate_accept_header

  rescue_from ActiveRecord::RecordNotFound, with: :render_not_found

  rescue_from ActionController::ParameterMissing do |error|
    render_error(error.message, status: :bad_request, code: "request.parameter_missing")
  end

  rescue_from ActionDispatch::Http::Parameters::ParseError do
    render_error("Request body is not valid JSON", status: :bad_request, code: "request.invalid_json")
  end

  private

  def validate_request_content_type
    return unless request.post? || request.patch? || request.put?
    return if request.raw_post.blank? || request.media_type == "application/json"

    render_error("Content-Type must be application/json", status: :unsupported_media_type, code: "request.unsupported_media_type")
  end

  def validate_accept_header
    accept = request.headers["Accept"].to_s
    return if accept.blank? || accept.include?("*/*") || accept.include?("application/json")
    return if report_request? && accept.include?("text/csv")

    render_error("The requested response format is not supported", status: :not_acceptable, code: "request.not_acceptable")
  end

  def report_request?
    request.path.include?("/reports/")
  end

  def render_success(data, status: :ok)
    render json: { data: data, error: nil }, status: status
  end

  def render_error(error, status: nil, code: nil)
    if error.is_a?(Pix::DomainError)
      status ||= error.http_status
      code ||= error.code
      detail = error.message
      errors = error.details
    else
      status ||= :unprocessable_entity
      code ||= "request.validation_failed"
      detail = error.is_a?(Hash) ? "One or more fields are invalid" : error.to_s
      errors = error.is_a?(Hash) ? error : nil
    end

    status_code = Rack::Utils.status_code(status)
    render json: {
      type: "about:blank",
      title: Rack::Utils::HTTP_STATUS_CODES[status_code],
      status: status_code,
      detail: detail,
      code: code,
      request_id: request.request_id,
      errors: errors,
      data: nil,
      error: detail
    }, status: status
  end

  def render_not_found
    render_error("Record not found", status: :not_found, code: "resource.not_found")
  end
end
