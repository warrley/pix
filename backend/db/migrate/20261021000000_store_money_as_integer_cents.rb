class StoreMoneyAsIntegerCents < ActiveRecord::Migration[8.1]
  def up
    add_column :accounts, :balance_cents, :bigint, null: false, default: 0
    add_column :transactions, :amount_cents, :bigint
    add_column :transaction_events, :amount_cents, :bigint

    execute "UPDATE accounts SET balance_cents = (balance * 100)::bigint"
    execute "UPDATE transactions SET amount_cents = (amount * 100)::bigint"
    execute "UPDATE transaction_events SET amount_cents = (amount * 100)::bigint"

    change_column_null :transactions, :amount_cents, false
    change_column_null :transaction_events, :amount_cents, false
    remove_check_constraint :accounts, name: "accounts_balance_non_negative"
    remove_check_constraint :transactions, name: "transactions_amount_positive"
    add_check_constraint :accounts, "balance_cents >= 0", name: "accounts_balance_cents_non_negative"
    add_check_constraint :transactions, "amount_cents > 0", name: "transactions_amount_cents_positive"

    remove_column :accounts, :balance
    remove_column :transactions, :amount
    remove_column :transaction_events, :amount
  end

  def down
    add_column :accounts, :balance, :decimal, precision: 14, scale: 2, default: 0, null: false
    add_column :transactions, :amount, :decimal, precision: 14, scale: 2
    add_column :transaction_events, :amount, :decimal, precision: 14, scale: 2
    execute "UPDATE accounts SET balance = balance_cents / 100.0"
    execute "UPDATE transactions SET amount = amount_cents / 100.0"
    execute "UPDATE transaction_events SET amount = amount_cents / 100.0"
    change_column_null :transactions, :amount, false
    change_column_null :transaction_events, :amount, false

    remove_check_constraint :accounts, name: "accounts_balance_cents_non_negative"
    remove_check_constraint :transactions, name: "transactions_amount_cents_positive"
    remove_column :accounts, :balance_cents
    remove_column :transactions, :amount_cents
    remove_column :transaction_events, :amount_cents
    add_check_constraint :accounts, "balance >= 0::numeric", name: "accounts_balance_non_negative"
    add_check_constraint :transactions, "amount > 0::numeric", name: "transactions_amount_positive"
  end
end
