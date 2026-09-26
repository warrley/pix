class DocumentationController < ActionController::Base
  def index
    send_file Rails.root.join("public", "api-docs", "index.html"),
      type: "text/html",
      disposition: "inline"
  end

  def spec
    render body: Rails.root.join("docs", "openapi.yaml").read,
      content_type: "application/yaml",
      status: :ok
  end
end
