require "forwardable"
require "fileutils"
require "json"
require "tmpdir"

module Observability
  class Metrics
    CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8".freeze
    BUFFER_FLUSH_THRESHOLD = 50
    BUFFER_KEY = :pix_observability_buffering

    HTTP_DURATION_BUCKETS = [ 0.005, 0.01, 0.025, 0.05, 0.1, 0.3, 0.5, 1.0, 1.5, 2.0, 5.0 ].freeze
    DB_DURATION_BUCKETS = [ 0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0 ].freeze
    TRANSFER_AMOUNT_BUCKETS = [ 1_000, 5_000, 10_000, 25_000, 50_000, 100_000, 500_000, 1_000_000 ].freeze

    COUNTER_DEFINITIONS = {
      http_requests_total: {
        help: "Total de requisicoes HTTP recebidas",
        labels: %i[method route status_code]
      },
      pix_transfers_total: {
        help: "Total de transferencias PIX processadas por status e codigo de erro",
        labels: %i[status error_code]
      },
      pix_transfer_amount_cents_total: {
        help: "Volume financeiro total transferido via PIX em centavos",
        labels: []
      },
      pix_amount_reais_total: {
        help: "Volume financeiro total transferido via PIX em Reais",
        labels: []
      },
      pix_keys_operations_total: {
        help: "Total de operacoes no ciclo de vida de chaves PIX",
        labels: %i[operation key_type status]
      },
      pix_security_events_total: {
        help: "Total de eventos de auditoria de seguranca e antifraude",
        labels: %i[event]
      },
      pix_auth_rate_limited_total: {
        help: "Total de tentativas de autenticacao bloqueadas por rate limit",
        labels: %i[limiter]
      },
      pix_notifications_delivery_total: {
        help: "Total de entregas de notificacoes de transacao por canal e status",
        labels: %i[notifier event_type status]
      }
    }.freeze

    HISTOGRAM_DEFINITIONS = {
      http_request_duration_seconds: {
        help: "Duracao das requisicoes HTTP em segundos",
        labels: %i[method route status_code],
        buckets: HTTP_DURATION_BUCKETS
      },
      rails_db_query_duration_seconds: {
        help: "Duracao das consultas SQL no banco de dados em segundos",
        labels: %i[operation],
        buckets: DB_DURATION_BUCKETS
      },
      pix_transfer_amount_cents: {
        help: "Distribuicao dos valores de transferencia PIX concluidas em centavos",
        labels: [],
        buckets: TRANSFER_AMOUNT_BUCKETS
      }
    }.freeze

    attr_reader :store_path

    class << self
      extend Forwardable

      def instance
        @instance ||= new
      end

      def_delegators :instance,
                     :store_path,
                     :use_store_path!,
                     :with_request_buffering,
                     :flush!,
                     :reset!,
                     :increment_counter,
                     :observe_histogram,
                     :counter_value,
                     :histogram_snapshot,
                     :record_http_request,
                     :record_db_query,
                     :record_transfer,
                     :record_pix_key_operation,
                     :record_security_event,
                     :record_rate_limit,
                     :record_notification,
                     :render_prometheus_text
    end

    def initialize(store_path: nil)
      @mutex = Mutex.new
      @boot_time = Process.clock_gettime(Process::CLOCK_MONOTONIC)
      @pending_counters = {}
      @pending_histograms = {}
      @pending_ops = 0
      use_store_path!(store_path || default_store_path)
    end

    def use_store_path!(path)
      @mutex.synchronize do
        @store_path = path
        @lock_path = "#{@store_path}.lock"
        clear_pending_buffers_locked!
      end
      ensure_store_initialized!
    rescue StandardError
      nil
    end

    def with_request_buffering
      previous = Thread.current[BUFFER_KEY]
      Thread.current[BUFFER_KEY] = true
      yield
    ensure
      Thread.current[BUFFER_KEY] = previous
      flush! unless previous
    end

    def flush!
      counters_delta = nil
      histograms_delta = nil

      @mutex.synchronize do
        return if @pending_ops.zero?

        counters_delta = @pending_counters
        histograms_delta = @pending_histograms
        clear_pending_buffers_locked!
      end

      with_locked_store(File::LOCK_EX) do
        state = read_raw_state
        apply_deltas_to_state!(state, counters_delta, histograms_delta)
        write_raw_state(state)
      end
    rescue StandardError
      nil
    end

    def reset!
      @mutex.synchronize { clear_pending_buffers_locked! }
      with_locked_store(File::LOCK_EX) do
        write_raw_state(initial_raw_state)
      end
    rescue StandardError
      nil
    end

    def increment_counter(name, labels = {}, by: 1, flush: true)
      metric_key = name.to_sym
      definition = COUNTER_DEFINITIONS.fetch(metric_key)
      normalized_labels = normalize_labels(definition[:labels], labels)
      series_key = JSON.generate(normalized_labels)
      should_flush = false

      @mutex.synchronize do
        series = (@pending_counters[metric_key.to_s] ||= {})
        current = series[series_key] || 0
        series[series_key] = by.is_a?(Float) || current.is_a?(Float) ? (current.to_f + by.to_f).round(6) : current + by
        @pending_ops += 1
        should_flush = should_flush_now?(flush)
      end

      flush! if should_flush
    rescue StandardError
      nil
    end

    def observe_histogram(name, value, labels = {}, flush: true)
      metric_key = name.to_sym
      definition = HISTOGRAM_DEFINITIONS.fetch(metric_key)
      normalized_labels = normalize_labels(definition[:labels], labels)
      series_key = JSON.generate(normalized_labels)
      numeric_value = value.to_f
      should_flush = false

      @mutex.synchronize do
        series_map = (@pending_histograms[metric_key.to_s] ||= {})
        entry = (series_map[series_key] ||= {
          "bucket_counts" => Array.new(definition[:buckets].size, 0),
          "count" => 0,
          "sum" => 0.0
        })

        definition[:buckets].each_with_index do |bound, index|
          entry["bucket_counts"][index] += 1 if numeric_value <= bound
        end
        entry["count"] += 1
        entry["sum"] = (entry["sum"].to_f + numeric_value).round(6)
        @pending_ops += 1
        should_flush = should_flush_now?(flush)
      end

      flush! if should_flush
    rescue StandardError
      nil
    end

    def counter_value(name, labels = {})
      flush!
      metric_key = name.to_sym
      definition = COUNTER_DEFINITIONS.fetch(metric_key)
      normalized_labels = normalize_labels(definition[:labels], labels)
      series_key = JSON.generate(normalized_labels)

      with_locked_store(File::LOCK_SH) do
        state = read_raw_state
        state.dig("counters", metric_key.to_s, series_key) || 0
      end
    rescue StandardError
      0
    end

    def histogram_snapshot(name, labels = {})
      flush!
      metric_key = name.to_sym
      definition = HISTOGRAM_DEFINITIONS.fetch(metric_key)
      normalized_labels = normalize_labels(definition[:labels], labels)
      series_key = JSON.generate(normalized_labels)

      with_locked_store(File::LOCK_SH) do
        state = read_raw_state
        entry = state.dig("histograms", metric_key.to_s, series_key)
        return { count: 0, sum: 0.0, buckets: {} } unless entry

        buckets_map = {}
        definition[:buckets].each_with_index do |bound, idx|
          buckets_map[bound] = entry["bucket_counts"][idx]
        end
        buckets_map["+Inf"] = entry["count"]
        { count: entry["count"], sum: entry["sum"], buckets: buckets_map }
      end
    rescue StandardError
      { count: 0, sum: 0.0, buckets: {} }
    end

    def record_http_request(method:, route:, status_code:, duration_seconds:)
      labels = {
        method: method.to_s.upcase,
        route: route.to_s,
        status_code: status_code.to_s
      }
      increment_counter(:http_requests_total, labels, flush: false)
      observe_histogram(:http_request_duration_seconds, duration_seconds, labels, flush: true)
    rescue StandardError
      nil
    end

    def record_db_query(sql:, duration_seconds:)
      operation = extract_sql_operation(sql)
      return unless operation

      observe_histogram(:rails_db_query_duration_seconds, duration_seconds, { operation: operation }, flush: false)
    rescue StandardError
      nil
    end

    def record_transfer(status:, amount_cents: nil, error_code: "none")
      code_label = present_or_default(error_code, "none")
      completed_with_amount = status.to_s == "completed" && amount_cents.is_a?(Numeric) && amount_cents.positive?

      increment_counter(:pix_transfers_total, { status: status.to_s, error_code: code_label }, flush: !completed_with_amount)
      return unless completed_with_amount

      increment_counter(:pix_transfer_amount_cents_total, {}, by: amount_cents.to_i, flush: false)
      increment_counter(:pix_amount_reais_total, {}, by: (amount_cents.to_f / 100.0).round(2), flush: false)
      observe_histogram(:pix_transfer_amount_cents, amount_cents.to_i, flush: true)
    rescue StandardError
      nil
    end

    def record_pix_key_operation(operation:, key_type:, status:)
      increment_counter(
        :pix_keys_operations_total,
        {
          operation: operation.to_s,
          key_type: present_or_default(key_type, "unknown"),
          status: status.to_s
        }
      )
    rescue StandardError
      nil
    end

    def record_security_event(event)
      increment_counter(:pix_security_events_total, { event: event.to_s })
    rescue StandardError
      nil
    end

    def record_rate_limit(limiter)
      increment_counter(:pix_auth_rate_limited_total, { limiter: limiter.to_s })
    rescue StandardError
      nil
    end

    def record_notification(notifier:, event_type:, status:)
      short_notifier = present_or_default(notifier.to_s.split("::").last, notifier.to_s)
      increment_counter(
        :pix_notifications_delivery_total,
        {
          notifier: short_notifier,
          event_type: event_type.to_s,
          status: status.to_s
        }
      )
    rescue StandardError
      nil
    end

    def render_prometheus_text
      flush!
      lines = []

      counters_copy, histograms_copy = snapshot_state

      COUNTER_DEFINITIONS.each do |name, definition|
        lines << "# HELP #{name} #{definition[:help]}"
        lines << "# TYPE #{name} counter"
        counters_copy[name].each do |labels, value|
          lines << "#{name}#{format_labels(labels)} #{format_number(value)}"
        end
      end

      HISTOGRAM_DEFINITIONS.each do |name, definition|
        lines << "# HELP #{name} #{definition[:help]}"
        lines << "# TYPE #{name} histogram"
        histogram_data = histograms_copy[name]
        histogram_data[:series].each do |labels, entry|
          histogram_data[:buckets].each_with_index do |bound, index|
            bucket_labels = labels.merge(le: format_number(bound))
            lines << "#{name}_bucket#{format_labels(bucket_labels)} #{entry[:bucket_counts][index]}"
          end
          inf_labels = labels.merge(le: "+Inf")
          lines << "#{name}_bucket#{format_labels(inf_labels)} #{entry[:count]}"
          lines << "#{name}_sum#{format_labels(labels)} #{format_number(entry[:sum])}"
          lines << "#{name}_count#{format_labels(labels)} #{entry[:count]}"
        end
      end

      append_runtime_gauges(lines)
      append_database_gauges(lines)

      "#{lines.join("\n")}\n"
    end

    private

    def clear_pending_buffers_locked!
      @pending_counters = {}
      @pending_histograms = {}
      @pending_ops = 0
    end

    def should_flush_now?(explicit_flush)
      return true if @pending_ops >= BUFFER_FLUSH_THRESHOLD
      return false if Thread.current[BUFFER_KEY]

      explicit_flush
    end

    def apply_deltas_to_state!(state, counters_delta, histograms_delta)
      counters_delta.each do |metric_name, series_delta|
        target_series = (state["counters"][metric_name] ||= {})
        series_delta.each do |series_key, delta|
          current = target_series[series_key] || 0
          target_series[series_key] = delta.is_a?(Float) || current.is_a?(Float) ? (current.to_f + delta.to_f).round(6) : current + delta
        end
      end

      histograms_delta.each do |metric_name, series_delta|
        buckets_size = HISTOGRAM_DEFINITIONS.fetch(metric_name.to_sym)[:buckets].size
        target_series = (state["histograms"][metric_name] ||= {})
        series_delta.each do |series_key, delta_entry|
          target_entry = (target_series[series_key] ||= {
            "bucket_counts" => Array.new(buckets_size, 0),
            "count" => 0,
            "sum" => 0.0
          })
          delta_entry["bucket_counts"].each_with_index do |inc, idx|
            target_entry["bucket_counts"][idx] = (target_entry["bucket_counts"][idx] || 0) + inc
          end
          target_entry["count"] = (target_entry["count"] || 0) + delta_entry["count"]
          target_entry["sum"] = (target_entry["sum"].to_f + delta_entry["sum"].to_f).round(6)
        end
      end
    end

    def default_store_path
      env_path = ENV["METRICS_STORE_PATH"].to_s.strip
      return env_path unless env_path.empty?

      base_dir = if defined?(Rails) && Rails.respond_to?(:root) && Rails.root
        Rails.root.join("tmp", "prometheus_metrics").to_s
      else
        File.join(Dir.tmpdir, "pix_prometheus_metrics")
      end

      filename = if defined?(Rails) && Rails.respond_to?(:env) && Rails.env.test?
        "metrics_state_test_#{Process.pid}.json"
      elsif defined?(Minitest)
        "metrics_state_unit_#{Process.pid}.json"
      else
        "metrics_state.json"
      end

      File.join(base_dir, filename)
    end

    def ensure_store_initialized!
      FileUtils.mkdir_p(File.dirname(@store_path))
      with_locked_store(File::LOCK_EX) do
        write_raw_state(initial_raw_state) unless File.exist?(@store_path) && File.size?(@store_path)
      end
    end

    def initial_raw_state
      counters = COUNTER_DEFINITIONS.keys.each_with_object({}) { |name, hash| hash[name.to_s] = {} }
      counters["pix_transfer_amount_cents_total"]["{}"] = 0
      counters["pix_amount_reais_total"]["{}"] = 0.0

      histograms = HISTOGRAM_DEFINITIONS.keys.each_with_object({}) { |name, hash| hash[name.to_s] = {} }

      { "counters" => counters, "histograms" => histograms }
    end

    def with_locked_store(lock_mode)
      @mutex.synchronize do
        FileUtils.mkdir_p(File.dirname(@lock_path))
        File.open(@lock_path, File::RDWR | File::CREAT, 0o600) do |lock_file|
          lock_file.flock(lock_mode)
          begin
            yield
          ensure
            lock_file.flock(File::LOCK_UN)
          end
        end
      end
    end

    def read_raw_state
      return initial_raw_state unless File.exist?(@store_path)

      raw = File.read(@store_path)
      return initial_raw_state if raw.strip.empty?

      JSON.parse(raw)
    rescue JSON::ParserError
      initial_raw_state
    end

    def write_raw_state(state)
      File.write(@store_path, JSON.generate(state))
    end

    def snapshot_state
      raw_state = with_locked_store(File::LOCK_SH) { read_raw_state }

      counters_copy = COUNTER_DEFINITIONS.keys.each_with_object({}) do |name, hash|
        series = raw_state.dig("counters", name.to_s) || {}
        hash[name] = series.each_with_object({}) do |(series_key, value), parsed_series|
          parsed_series[JSON.parse(series_key, symbolize_names: true)] = value
        end
      end

      histograms_copy = HISTOGRAM_DEFINITIONS.each_with_object({}) do |(name, definition), hash|
        series = raw_state.dig("histograms", name.to_s) || {}
        parsed_series = series.each_with_object({}) do |(series_key, entry), acc|
          acc[JSON.parse(series_key, symbolize_names: true)] = {
            bucket_counts: Array(entry["bucket_counts"]),
            count: entry["count"].to_i,
            sum: entry["sum"].to_f
          }
        end
        hash[name] = {
          buckets: definition[:buckets],
          series: parsed_series
        }
      end

      [ counters_copy, histograms_copy ]
    end

    def normalize_labels(expected_keys, labels)
      expected_keys.each_with_object({}) do |key, result|
        result[key] = (labels[key] || labels[key.to_s] || "unknown").to_s
      end
    end

    def format_labels(labels)
      return "" if labels.nil? || labels.empty?

      formatted = labels.map do |key, value|
        escaped = value.to_s.gsub("\\", "\\\\\\\\").gsub("\n", "\\n").gsub('"', '\\"')
        "#{key}=\"#{escaped}\""
      end.join(",")
      "{#{formatted}}"
    end

    def format_number(value)
      if value.is_a?(Float)
        formatted = format("%.6f", value).sub(/\.?0+\z/, "")
        formatted.empty? ? "0" : formatted
      else
        value.to_s
      end
    end

    def present_or_default(value, default)
      str = value.to_s.strip
      str.empty? ? default : str
    end

    def extract_sql_operation(sql)
      case sql.to_s.lstrip
      when /\ASELECT\b/i then "SELECT"
      when /\AINSERT\b/i then "INSERT"
      when /\AUPDATE\b/i then "UPDATE"
      when /\ADELETE\b/i then "DELETE"
      when /\A(?:BEGIN|COMMIT|ROLLBACK)\b/i then "TRANSACTION"
      end
    end

    def append_runtime_gauges(lines)
      uptime = Process.clock_gettime(Process::CLOCK_MONOTONIC) - @boot_time
      lines << "# HELP ruby_process_uptime_seconds Tempo de atividade do processo Ruby em segundos"
      lines << "# TYPE ruby_process_uptime_seconds gauge"
      lines << "ruby_process_uptime_seconds #{format_number(uptime)}"

      gc_stat = GC.stat
      lines << "# HELP ruby_gc_count_total Total de execucoes do Garbage Collector do Ruby"
      lines << "# TYPE ruby_gc_count_total counter"
      lines << "ruby_gc_count_total #{gc_stat[:count].to_i}"

      lines << "# HELP ruby_gc_heap_live_slots Slots vivos no heap do Ruby"
      lines << "# TYPE ruby_gc_heap_live_slots gauge"
      lines << "ruby_gc_heap_live_slots #{gc_stat[:heap_live_slots].to_i}"
    rescue StandardError
      nil
    end

    def append_database_gauges(lines)
      return unless defined?(ActiveRecord::Base) && ActiveRecord::Base.connected?

      pool_stat = ActiveRecord::Base.connection_pool.stat
      lines << "# HELP rails_db_connection_pool Estado do pool de conexoes do ActiveRecord"
      lines << "# TYPE rails_db_connection_pool gauge"
      %i[size connections busy dead idle waiting].each do |state_key|
        next unless pool_stat.key?(state_key)

        lines << "rails_db_connection_pool{state=\"#{state_key}\"} #{pool_stat[state_key].to_i}"
      end

      lines << "# HELP pix_accounts_total Total de contas cadastradas por status e bloqueio antifraude"
      lines << "# TYPE pix_accounts_total gauge"
      Account.group(:status, :fraud_blocked).count.each do |(status, fraud_blocked), count|
        lines << "pix_accounts_total{status=\"#{status}\",fraud_blocked=\"#{fraud_blocked}\"} #{count}"
      end

      lines << "# HELP pix_active_keys_total Total de chaves PIX ativas por tipo"
      lines << "# TYPE pix_active_keys_total gauge"
      PixKey.where(status: "active").group(:key_type).count.each do |key_type, count|
        lines << "pix_active_keys_total{key_type=\"#{key_type}\"} #{count}"
      end

      lines << "# HELP pix_transactions_by_status_total Total acumulado de transacoes no banco por status"
      lines << "# TYPE pix_transactions_by_status_total gauge"
      Transaction.group(:status).count.each do |status, count|
        lines << "pix_transactions_by_status_total{status=\"#{status}\"} #{count}"
      end
    rescue StandardError
      nil
    end
  end
end
