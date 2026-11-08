module Pix
  DomainError = Struct.new(:code, :message, :http_status, :details, keyword_init: true)

  class DomainFailure < StandardError
    attr_reader :error

    def initialize(error)
      @error = error
      super(error.message)
    end
  end
end
