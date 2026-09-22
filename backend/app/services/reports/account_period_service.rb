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
      return Result.new(success?: false, data: nil, error: "Account not found") unless account

      period = resolve_period
      return Result.new(success?: false, data: nil, error: @period_error) unless period

      start_time = period[:start_time]
      end_time = period[:end_time]

      # Cálculo do saldo de abertura (opening balance)
      # Reverte todas as transações concluídas desde start_time até o momento atual
      completed_debits_since_start = Transaction.where(source_account_id: account.id, status: :completed)
                                                .where("created_at >= ?", start_time)
                                                .sum(:amount)

      completed_credits_since_start = Transaction.where(destination_account_id: account.id, status: :completed)
                                                 .where("created_at >= ?", start_time)
                                                 .sum(:amount)

      opening_balance = account.balance + BigDecimal(completed_debits_since_start.to_s) - BigDecimal(completed_credits_since_start.to_s)

      # Créditos e débitos concluídos estritamente dentro do período [start_time, end_time]
      period_credits = Transaction.where(destination_account_id: account.id, status: :completed)
                                  .where(created_at: start_time..end_time)
                                  .sum(:amount)

      period_debits = Transaction.where(source_account_id: account.id, status: :completed)
                                 .where(created_at: start_time..end_time)
                                 .sum(:amount)

      total_credits = BigDecimal(period_credits.to_s)
      total_debits = BigDecimal(period_debits.to_s)
      closing_balance = opening_balance + total_credits - total_debits

      # Validação de conciliação: opening + inflows - outflows == closing
      reconciliation_valid = (closing_balance == (opening_balance + total_credits - total_debits))

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
          amount: tx.amount,
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
        opening_balance: opening_balance.to_s("F"),
        total_inflows: total_credits.to_s("F"),
        total_outflows: total_debits.to_s("F"),
        closing_balance: closing_balance.to_s("F"),
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
            tx[:amount].to_s,
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
      if @month_param.present? || @year_param.present?
        year = (@year_param || Date.current.year).to_i
        month = (@month_param || Date.current.month).to_i

        unless month.between?(1, 12) && year.positive?
          @period_error = "Invalid month or year"
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
          @period_error = "Invalid date: #{e.message}"
          nil
        end
      elsif @start_date_param.present? || @end_date_param.present?
        begin
          start_date = @start_date_param.present? ? Date.parse(@start_date_param.to_s) : Date.new(2000, 1, 1)
          end_date = @end_date_param.present? ? Date.parse(@end_date_param.to_s) : Date.current

          if start_date > end_date
            @period_error = "start_date cannot be after end_date"
            return nil
          end

          {
            start_time: start_date.beginning_of_day,
            end_time: end_date.end_of_day,
            month: (start_date.month == end_date.month && start_date.year == end_date.year) ? start_date.month : nil,
            year: (start_date.year == end_date.year) ? start_date.year : nil
          }
        rescue ArgumentError => e
          @period_error = "Invalid date format: #{e.message}"
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
