module Api
  module V1
    module Admin
      class DashboardController < BaseController
        def overview
          completed = Transaction.completed
          today = Time.current.beginning_of_day
          active_tokens = ApiAccessToken.active
          completed_count = completed.count
          total_moved = completed.sum(:amount_cents)
          moved_today = completed.where(created_at: today..Time.current).sum(:amount_cents)
          finished_count = Transaction.where(status: %w[completed failed]).count
          daily_series = daily_volume
          daily_avg_cents = (daily_series.sum { |d| d[:amount_cents] } / 7.0).round
          active_accounts_count = Account.active.count
          active_keys_count = PixKey.active.count
          refunded_count = Transaction.refunded.count
          settled_total = completed_count + refunded_count

          render_success({
            total_users: User.count,
            active_users: User.joins(:accounts).merge(Account.active).distinct.count,
            online_users: active_tokens.distinct.count(:user_id),
            total_accounts: Account.count,
            active_accounts: active_accounts_count,
            blocked_accounts: Account.where(fraud_blocked: true).count,
            total_balance_cents: Account.sum(:balance_cents),
            total_transactions: Transaction.count,
            transactions_today: Transaction.where(created_at: today..Time.current).count,
            total_moved_cents: total_moved,
            moved_today_cents: moved_today,
            average_ticket_cents: completed_count.positive? ? (total_moved / completed_count) : 0,
            success_rate: finished_count.positive? ? ((completed_count.to_f / finished_count) * 100).round(1) : 100.0,
            pix_keys: {
              total: PixKey.count,
              active: active_keys_count,
              suspended: PixKey.suspended.count,
              by_type: PixKey.active.group(:key_type).count
            },
            scheduled_transfers: {
              pending: ScheduledTransfer.pending.count,
              pending_amount_cents: ScheduledTransfer.pending.sum(:amount_cents)
            },
            queues: {
              pending_refund_requests: RefundRequest.pending.count,
              pending_fraud_reports: FraudReport.pending.count,
              suspended_pix_keys: PixKey.suspended.count
            },
            insights: {
              daily_avg_cents: daily_avg_cents,
              today_vs_avg_pct: daily_avg_cents.positive? ? (((moved_today - daily_avg_cents).to_f / daily_avg_cents) * 100).round(1) : 0.0,
              max_transaction_cents: completed.maximum(:amount_cents) || 0,
              refunded_cents: Transaction.refunded.sum(:amount_cents),
              refund_rate: settled_total.positive? ? ((refunded_count.to_f / settled_total) * 100).round(1) : 0.0,
              blocked_balance_cents: Account.where(fraud_blocked: true).sum(:balance_cents),
              geolocated_transactions: Transaction.where.not(latitude: nil, longitude: nil).count,
              avg_keys_per_account: active_accounts_count.positive? ? (active_keys_count.to_f / active_accounts_count).round(2) : 0.0
            },
            top_failure_reasons: top_failure_reasons,
            top_accounts: top_accounts,
            status_counts: Transaction.group(:status).count,
            recent_transactions: recent_transactions,
            daily: daily_series
          })
        end

        def users
          per_page = [ [ params.fetch(:per_page, 20).to_i, 100 ].min, 1 ].max
          page = [ params.fetch(:page, 1).to_i, 1 ].max
          users = User.includes(:accounts, :api_access_tokens).order(created_at: :desc)
          users = users.where("name ILIKE :search OR email ILIKE :search", search: "%#{params[:search].to_s.strip}%") if params[:search].present?

          total = users.count
          paginated = users.offset((page - 1) * per_page).limit(per_page)
          render_success({
            items: paginated.map { |user| user_admin_data(user) },
            pagination: {
              page: page,
              per_page: per_page,
              total: total,
              total_pages: (total.to_f / per_page).ceil
            }
          })
        end

        def transactions
          per_page = [ [ params.fetch(:per_page, 20).to_i, 100 ].min, 1 ].max
          page = [ params.fetch(:page, 1).to_i, 1 ].max
          transactions = Transaction.includes(source_account: :user, destination_account: :user).order(created_at: :desc)
          transactions = transactions.where(status: params[:status]) if Transaction.statuses.key?(params[:status])

          total = transactions.count
          paginated = transactions.offset((page - 1) * per_page).limit(per_page)
          render_success({
            items: paginated.map { |transaction| transaction_admin_data(transaction) },
            pagination: {
              page: page,
              per_page: per_page,
              total: total,
              total_pages: (total.to_f / per_page).ceil
            }
          })
        end

        def audit_logs
          page, per_page = pagination_params
          logs = AdminAuditLog.includes(:operator).order(created_at: :desc)
          total = logs.count
          paginated = logs.offset((page - 1) * per_page).limit(per_page)

          render_success({
            items: paginated.map do |log|
              {
                id: log.id,
                action: log.action,
                target_type: log.target_type,
                target_id: log.target_id,
                details: log.details,
                ip_address: log.ip_address,
                operator_name: log.operator.name,
                operator_email: log.operator.email,
                created_at: log.created_at.iso8601
              }
            end,
            pagination: {
              page: page,
              per_page: per_page,
              total: total,
              total_pages: (total.to_f / per_page).ceil
            }
          })
        end

        private

        def recent_transactions
          Transaction.includes(source_account: :user, destination_account: :user)
            .order(created_at: :desc)
            .limit(8)
            .map { |transaction| transaction_admin_data(transaction) }
        end

        def daily_volume
          7.times.map do |offset|
            date = offset.days.ago.to_date
            completed_today = Transaction.completed.where(created_at: date.beginning_of_day..date.end_of_day)
            {
              date: date.iso8601,
              amount_cents: completed_today.sum(:amount_cents),
              count: completed_today.count
            }
          end.reverse
        end

        def user_admin_data(user)
          active_token = user.api_access_tokens.active.order(last_used_at: :desc).first
          active_account = user.accounts.active.exists?
          {
            id: user.id,
            name: user.name,
            email: user.email,
            phone: user.phone,
            role: user.role,
            status: active_account ? "active" : "inactive",
            online: active_token.present?,
            accounts_count: user.accounts.count,
            balance_cents: user.accounts.sum(:balance_cents),
            last_active_at: active_token&.last_used_at&.iso8601,
            created_at: user.created_at.iso8601
          }
        end

        def transaction_admin_data(transaction)
          {
            id: transaction.id,
            end_to_end_id: transaction.end_to_end_id,
            amount_cents: transaction.amount_cents,
            amount: transaction.amount.to_s,
            status: transaction.status,
            description: transaction.description,
            source: account_summary(transaction.source_account),
            destination: account_summary(transaction.destination_account),
            created_at: transaction.created_at.iso8601
          }
        end

        def account_summary(account)
          {
            account_number: account.account_number,
            owner_name: account.user.name
          }
        end

        def top_failure_reasons
          Transaction.failed
            .where.not(failure_reason: [ nil, "" ])
            .group(:failure_reason)
            .order(Arel.sql("COUNT(*) DESC"))
            .limit(5)
            .count
            .map { |reason, count| { reason: reason, count: count } }
        end

        def top_accounts
          outgoing = Transaction.completed
            .group(:source_account_id)
            .order(Arel.sql("SUM(amount_cents) DESC"))
            .limit(5)
            .pluck(:source_account_id, Arel.sql("SUM(amount_cents)"), Arel.sql("COUNT(*)"))

          accounts_by_id = Account.includes(:user).where(id: outgoing.map(&:first)).index_by(&:id)
          outgoing.filter_map do |account_id, sent_cents, tx_count|
            account = accounts_by_id[account_id]
            next unless account

            {
              account_number: account.account_number,
              owner_name: account.user.name,
              sent_cents: sent_cents.to_i,
              tx_count: tx_count.to_i,
              balance_cents: account.balance_cents
            }
          end
        end
      end
    end
  end
end
