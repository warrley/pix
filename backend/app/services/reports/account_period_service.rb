require "csv"

module Reports
  class AccountPeriodService
    Result = Struct.new(:success?, :data, :error, keyword_init: true)

    def self.call(...)
      new(...).call
    end

    def initialize(account_id:, start_date: nil, end_date: nil, month: nil, year: nil)
      @account_id = account_id
      @start_date_param = start_date
      @end_date_param = end_date
      @month_param = month
      @year_param = year
    end

    def call
      account = Account.find_by(id: @account_id)
      unless account
        return Result.new(success?: false, data: nil, error: Pix::DomainError.new(
          code: "account.not_found", message: "Account not found", http_status: :not_found
        ))
      end

      period = resolve_period
      return Result.new(success?: false, data: nil, error: @period_error) unless period

      start_time = period[:start_time]
      end_time = period[:end_time]

      # Cálculo do saldo de abertura (opening balance)
      # Reverte todas as transações concluídas desde start_time até o momento atual
      completed_debits_since_start = Transaction.where(source_account_id: account.id, status: :completed)
                                                .where("created_at >= ?", start_time)
                                                .sum(:amount_cents)

      completed_credits_since_start = Transaction.where(destination_account_id: account.id, status: :completed)
                                                 .where("created_at >= ?", start_time)
                                                 .sum(:amount_cents)

      opening_balance_cents = account.balance_cents + completed_debits_since_start - completed_credits_since_start

      # Créditos e débitos concluídos estritamente dentro do período [start_time, end_time]
      period_credits = Transaction.where(destination_account_id: account.id, status: :completed)
                                  .where(created_at: start_time..end_time)
                                  .sum(:amount_cents)

      period_debits = Transaction.where(source_account_id: account.id, status: :completed)
                                 .where(created_at: start_time..end_time)
                                 .sum(:amount_cents)

      total_credits_cents = period_credits
      total_debits_cents = period_debits
      closing_balance_cents = opening_balance_cents + total_credits_cents - total_debits_cents

      # Validação de conciliação: opening + inflows - outflows == closing
      reconciliation_valid = (closing_balance_cents == (opening_balance_cents + total_credits_cents - total_debits_cents))

      # Lista itemizada de transações no período
      transactions_relation = Transaction.where(source_account_id: account.id)
                                         .or(Transaction.where(destination_account_id: account.id))
                                         .where(created_at: start_time..end_time)
                                         .order(created_at: :asc)

      itemized_transactions = transactions_relation.map do |tx|
        is_debit = tx.source_account_id.to_s == account.id.to_s
        counterparty_account = is_debit ? tx.destination_account : tx.source_account
        counterparty_user = counterparty_account&.user

        {
          id: tx.id,
          end_to_end_id: tx.end_to_end_id,
          amount: Pix::Money.format_cents(tx.amount_cents),
          operation_type: is_debit ? "debit" : "credit",
          status: tx.status,
          created_at: tx.created_at,
          description: tx.description,
          pix_key_used: tx.pix_key_used,
          counterparty: {
            name: counterparty_user&.name,
            doc_id: mask_doc_id(counterparty_user&.doc_id)
          }
        }
      end

      report_data = {
        account_id: account.id,
        account_number: account.account_number,
        agency_number: account.agency_number,
        period: {
          start_date: start_time.to_date.to_s,
          end_date: end_time.to_date.to_s,
          month: period[:month],
          year: period[:year]
        },
        opening_balance: Pix::Money.format_cents(opening_balance_cents),
        total_inflows: Pix::Money.format_cents(total_credits_cents),
        total_outflows: Pix::Money.format_cents(total_debits_cents),
        closing_balance: Pix::Money.format_cents(closing_balance_cents),
        reconciliation_valid: reconciliation_valid,
        transactions_count: itemized_transactions.size,
        transactions: itemized_transactions
      }

      Result.new(success?: true, data: report_data, error: nil)
    end

    def self.generate_csv(report_data)
      CSV.generate(headers: true) do |csv|
        csv << [ "PIX STATEMENT REPORT" ]
        csv << [ "Account Number", report_data[:account_number], "Agency", report_data[:agency_number] ]
        csv << [ "Period Start", report_data[:period][:start_date], "Period End", report_data[:period][:end_date] ]
        csv << [ "Opening Balance", report_data[:opening_balance] ]
        csv << [ "Total Inflows (Credits)", report_data[:total_inflows] ]
        csv << [ "Total Outflows (Debits)", report_data[:total_outflows] ]
        csv << [ "Closing Balance", report_data[:closing_balance] ]
        csv << []
        csv << [ "ID", "End-to-End ID", "Date", "Operation", "Amount", "Status", "PIX Key", "Counterparty Name", "Counterparty Doc", "Description" ]

        report_data[:transactions].each do |tx|
          csv << [
            tx[:id],
            tx[:end_to_end_id],
            tx[:created_at]&.iso8601,
            tx[:operation_type],
            tx[:amount],
            tx[:status],
            tx[:pix_key_used],
            tx.dig(:counterparty, :name),
            tx.dig(:counterparty, :doc_id),
            tx[:description]
          ]
        end
      end
    end

    private

    def resolve_period
      has_month_year = @month_param.present? || @year_param.present?
      has_date_range = @start_date_param.present? || @end_date_param.present?
      if has_month_year && has_date_range
        @period_error = Pix::DomainError.new(code: "period.conflicting_filters", message: "Choose month/year or start_date/end_date", http_status: :bad_request)
        return nil
      end

      if has_month_year
        unless @month_param.present? && @year_param.present? && @month_param.to_s.match?(/\A\d{1,2}\z/) && @year_param.to_s.match?(/\A\d{4}\z/)
          @period_error = Pix::DomainError.new(code: "period.invalid", message: "month and year must be provided together", http_status: :bad_request)
          return nil
        end
        year = @year_param.to_i
        month = @month_param.to_i

        unless month.between?(1, 12) && year.positive?
          @period_error = Pix::DomainError.new(code: "period.invalid", message: "Invalid month or year", http_status: :bad_request)
          return nil
        end

        begin
          start_date = Date.new(year, month, 1)
          end_date = Date.new(year, month, -1)
          {
            start_time: start_date.beginning_of_day,
            end_time: end_date.end_of_day,
            month: month,
            year: year
          }
        rescue ArgumentError => e
          @period_error = Pix::DomainError.new(code: "period.invalid", message: "Invalid date", http_status: :unprocessable_entity)
          nil
        end
      elsif has_date_range
        begin
          start_date = @start_date_param.present? ? Date.iso8601(@start_date_param.to_s) : Date.new(2000, 1, 1)
          end_date = @end_date_param.present? ? Date.iso8601(@end_date_param.to_s) : Date.current

          if start_date > end_date
            @period_error = Pix::DomainError.new(code: "period.invalid_range", message: "start_date cannot be after end_date", http_status: :bad_request)
            return nil
          end

          {
            start_time: start_date.beginning_of_day,
            end_time: end_date.end_of_day,
            month: (start_date.month == end_date.month && start_date.year == end_date.year) ? start_date.month : nil,
            year: (start_date.year == end_date.year) ? start_date.year : nil
          }
        rescue ArgumentError => e
          @period_error = Pix::DomainError.new(code: "period.invalid", message: "Invalid date format", http_status: :bad_request)
          nil
        end
      else
        today = Date.current
        start_date = today.beginning_of_month
        end_date = today.end_of_month
        {
          start_time: start_date.beginning_of_day,
          end_time: end_date.end_of_day,
          month: today.month,
          year: today.year
        }
      end
    end

    def mask_doc_id(doc_id)
      return nil if doc_id.blank?
      cleaned = doc_id.to_s.gsub(/\D/, "")
      if cleaned.length == 11
        "***.#{cleaned[3..5]}.#{cleaned[6..8]}-**"
      else
        "***"
      end
    end
  end
end
