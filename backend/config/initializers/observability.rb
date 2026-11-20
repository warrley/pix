Rails.application.config.after_initialize do
  ActiveSupport::Notifications.subscribe("sql.active_record") do |event|
    payload = event.payload
    next if payload[:name] == "SCHEMA" || payload[:cached]

    Observability::Metrics.record_db_query(
      sql: payload[:sql],
      duration_seconds: event.duration / 1000.0
    )
  end
end
