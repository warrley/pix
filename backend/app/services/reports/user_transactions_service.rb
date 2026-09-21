require "csv"

module Reports
  class UserTransactionsService
    CSV_HEADERS = %w[
      row_type
      user_id
      account_id
      account_number
      agency_number
      status
      total_transactions_count
      total_sent_amount
      total_received_amount
      net_balance_change
      average_ticket_amount
    ].freeze

    def self.call(...)
      new(...).call
    end

    def self.to_csv(report)
      new.to_csv(report)
    end

    def initialize(user_id: nil)
      @user_id = user_id
    end

    def call
      user = User.find(@user_id)
      accounts = user.accounts.order(:id).to_a
      account_ids = accounts.map(&:id)
      transactions = completed_transactions(account_ids)
      transactions_by_account = group_transactions_by_account(transactions, account_ids)

      {
        user_id: user.id,
        summary: presentation_metrics(metrics_for(transactions, account_ids)),
        accounts: accounts.map do |account|
          {
            account_id: account.id,
            account_number: account.account_number,
            agency_number: account.agency_number,
            status: account.status,
            **presentation_metrics(metrics_for(transactions_by_account[account.id], [ account.id ]))
          }
        end
      }
    end

    def to_csv(report)
      CSV.generate(headers: true, row_sep: "\r\n") do |csv|
        csv << CSV_HEADERS
        report[:accounts].each { |account| csv << account_row(report[:user_id], account) }
        csv << summary_row(report)
      end
    end

    private

    def completed_transactions(account_ids)
      return [] if account_ids.empty?

      Transaction
        .where(status: :completed)
        .where("source_account_id IN (?) OR destination_account_id IN (?)", account_ids, account_ids)
        .select(:id, :source_account_id, :destination_account_id, :amount)
        .to_a
    end

    def group_transactions_by_account(transactions, account_ids)
      transactions_by_account = Hash.new { |hash, account_id| hash[account_id] = [] }

      transactions.each do |transaction|
        transactions_by_account[transaction.source_account_id] << transaction if account_ids.include?(transaction.source_account_id)
        transactions_by_account[transaction.destination_account_id] << transaction if account_ids.include?(transaction.destination_account_id)
      end

      transactions_by_account
    end

    def metrics_for(transactions, owner_account_ids)
      metrics = empty_metrics

      transactions.each do |transaction|
        amount = BigDecimal(transaction.amount.to_s)
        metrics[:total_transactions_count] += 1
        metrics[:total_transaction_amount] += amount
        metrics[:total_sent_amount] += amount if owner_account_ids.include?(transaction.source_account_id)
        metrics[:total_received_amount] += amount if owner_account_ids.include?(transaction.destination_account_id)
      end

      metrics[:net_balance_change] = metrics[:total_received_amount] - metrics[:total_sent_amount]
      metrics[:average_ticket_amount] = if metrics[:total_transactions_count].positive?
        metrics[:total_transaction_amount] / metrics[:total_transactions_count]
      else
        BigDecimal("0")
      end

      metrics
    end

    def empty_metrics
      {
        total_transactions_count: 0,
        total_transaction_amount: BigDecimal("0"),
        total_sent_amount: BigDecimal("0"),
        total_received_amount: BigDecimal("0"),
        net_balance_change: BigDecimal("0"),
        average_ticket_amount: BigDecimal("0")
      }
    end

    def presentation_metrics(metrics)
      metrics.slice(:total_transactions_count).merge(
        total_sent_amount: metrics[:total_sent_amount].to_f,
        total_received_amount: metrics[:total_received_amount].to_f,
        net_balance_change: metrics[:net_balance_change].to_f,
        average_ticket_amount: metrics[:average_ticket_amount].to_f
      )
    end

    def account_row(user_id, account)
      [
        "account",
        user_id,
        account[:account_id],
        account[:account_number],
        account[:agency_number],
        account[:status],
        account[:total_transactions_count],
        money(account[:total_sent_amount]),
        money(account[:total_received_amount]),
        money(account[:net_balance_change]),
        money(account[:average_ticket_amount])
      ]
    end

    def summary_row(report)
      summary = report[:summary]

      [
        "summary",
        report[:user_id],
        nil,
        nil,
        nil,
        nil,
        summary[:total_transactions_count],
        money(summary[:total_sent_amount]),
        money(summary[:total_received_amount]),
        money(summary[:net_balance_change]),
        money(summary[:average_ticket_amount])
      ]
    end

    def money(value)
      format("%.2f", BigDecimal(value.to_s))
    end
  end
end
