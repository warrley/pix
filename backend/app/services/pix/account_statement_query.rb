module Pix
  class AccountStatementQuery
    def self.call(account_id:, page: 1, per_page: 20)
      new(account_id: account_id, page: page, per_page: per_page).call
    end

    def initialize(account_id:, page:, per_page:)
      @account_id = account_id
      @page = [page.to_i, 1].max
      @per_page = [[per_page.to_i, 1].max, 100].min # max de 100 por regra da issue
    end

    def call
      # Busca transações onde a conta é origem ou destino
      relation = Transaction.where(source_account_id: @account_id)
                            .or(Transaction.where(destination_account_id: @account_id))
                            .order(created_at: :desc)

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
