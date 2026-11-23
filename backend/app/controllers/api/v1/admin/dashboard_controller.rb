module Api
  module V1
    module Admin
      class DashboardController < ApplicationController
        before_action :authorize_operator!
        before_action -> { require_api_scope!("users:admin") }
        before_action -> { require_api_scope!("accounts:admin") }
        before_action -> { require_api_scope!("reports:read") }

        def overview
          completed = Transaction.completed
          today = Time.current.beginning_of_day
          active_tokens = ApiAccessToken.active

          render_success({
            total_users: User.count,
            active_users: User.joins(:accounts).merge(Account.active).distinct.count,
            online_users: active_tokens.distinct.count(:user_id),
            total_accounts: Account.count,
            active_accounts: Account.active.count,
            blocked_accounts: Account.where(fraud_blocked: true).count,
            total_transactions: Transaction.count,
            transactions_today: Transaction.where(created_at: today..Time.current).count,
            total_moved_cents: completed.sum(:amount_cents),
            moved_today_cents: completed.where(created_at: today..Time.current).sum(:amount_cents),
            status_counts: Transaction.group(:status).count,
            recent_transactions: recent_transactions,
            daily: daily_volume
          })
        end

        def users
          per_page = [[params.fetch(:per_page, 20).to_i, 100].min, 1].max
          page = [params.fetch(:page, 1).to_i, 1].max
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
          per_page = [[params.fetch(:per_page, 20).to_i, 100].min, 1].max
          page = [params.fetch(:page, 1).to_i, 1].max
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
      end
    end
  end
end
