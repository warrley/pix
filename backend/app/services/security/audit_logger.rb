require "json"

module Security
  class AuditLogger
    def self.record(event:, actor: nil, resource: nil, request: nil)
      Observability::Metrics.record_security_event(event) if defined?(Observability::Metrics)
      details = {
        event: event,
        actor_id: actor&.id,
        resource_type: resource&.class&.name,
        resource_id: resource&.id,
        request_id: request&.request_id,
        timestamp: Time.current.utc.iso8601
      }.compact
      Rails.logger.info("security_audit #{JSON.generate(details)}")
    end
  end
end
