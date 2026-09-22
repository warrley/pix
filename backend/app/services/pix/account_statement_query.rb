module Pix
  class AccountStatementQuery
    def self.call(account_id:, page: 1, per_page: 20, start_date: nil, end_date: nil)
      new(account_id: account_id, page: page, per_page: per_page, start_date: start_date, end_date: end_date).call
    end

    def initialize(account_id:, page:, per_page:, start_date: nil, end_date: nil)
      @account_id = account_id
      @page = [ page.to_i, 1 ].max
      @per_page = [ [ per_page.to_i, 1 ].max, 100 ].min # max de 100 por regra da issue
      @start_time = parse_date_boundary(start_date, beginning: true)
      @end_time = parse_date_boundary(end_date, beginning: false)

      if @start_time && @end_time && @start_time > @end_time
        raise ArgumentError, "start_date must be before or equal to end_date"
      end
    end

    def call
      # Busca transações onde a conta é origem ou destino
      relation = Transaction.where(source_account_id: @account_id)
                            .or(Transaction.where(destination_account_id: @account_id))
                            .order(created_at: :desc)
      relation = relation.where("created_at >= ?", @start_time) if @start_time
      relation = relation.where("created_at <= ?", @end_time) if @end_time

      total_count = relation.count
      total_pages = (total_count.to_f / @per_page).ceil
      total_pages = 1 if total_pages.zero?

      offset = (@page - 1) * @per_page
      transactions = relation.limit(@per_page).offset(offset)

      formatted_transactions = transactions.map do |tx|
        is_debit = tx.source_account_id.to_s == @account_id.to_s
        counterparty_account = is_debit ? tx.destination_account : tx.source_account
        counterparty_user = counterparty_account&.user

        {
          id: tx.id,
          amount: tx.amount,
          operation_type: is_debit ? "debit" : "credit",
          status: tx.status,
          end_to_end_id: tx.end_to_end_id,
          created_at: tx.created_at,
          counterparty: {
            name: counterparty_user&.name,
            doc_id: mask_doc_id(counterparty_user&.doc_id)
          }
        }
      end

      {
        transactions: formatted_transactions,
        pagination: {
          current_page: @page,
          per_page: @per_page,
          total_count: total_count,
          total_pages: total_pages
        }
      }
    end

    private

    def parse_date_boundary(value, beginning:)
      return if value.blank?

      utc_date = if value.match?(/\A\d{4}-\d{2}-\d{2}\z/)
        Date.iso8601(value)
      else
        Time.iso8601(value).utc.to_date
      end

      (beginning ? utc_date.beginning_of_day : utc_date.end_of_day).utc
    rescue ArgumentError, Date::Error
      raise ArgumentError, "invalid date: #{value}"
    end

    def mask_doc_id(doc_id)
      return nil if doc_id.blank?
      # Exemplo simples de máscara para CPF/Documento (ex: ***.123.456-**)
      cleaned = doc_id.to_s.gsub(/\D/, "")
      if cleaned.length == 11
        "***.#{cleaned[3..5]}.#{cleaned[6..8]}-**"
      else
        "***"
      end
    end
  end
end
