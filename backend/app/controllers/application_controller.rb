class ApplicationController < ActionController::API
  rescue_from ActiveRecord::RecordNotFound, with: :render_not_found

  private

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
