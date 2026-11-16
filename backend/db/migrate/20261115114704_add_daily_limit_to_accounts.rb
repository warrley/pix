class AddDailyLimitToAccounts < ActiveRecord::Migration[8.1]
  def change
    add_column :accounts, :daily_limit, :decimal, precision: 14, scale: 2, null: false, default: 5_000.00
    add_check_constraint :accounts, "daily_limit >= 0", name: "accounts_daily_limit_non_negative"
  end
end
