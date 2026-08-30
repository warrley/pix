class CreateAccounts < ActiveRecord::Migration[8.1]
  def change
    create_table :accounts do |t|
      t.references :user, null: false, foreign_key: true
      t.string :account_number, limit: 20, null: false
      t.string :agency_number, limit: 10, null: false, default: "0001"
      t.decimal :balance, precision: 14, scale: 2, null: false, default: 0.0
      t.string :status, limit: 20, null: false, default: "active"

      t.timestamps
    end

    add_index :accounts, :account_number, unique: true
    add_check_constraint :accounts, "balance >= 0", name: "accounts_balance_non_negative"
    add_check_constraint :accounts, "status IN ('active', 'blocked', 'closed')", name: "accounts_status_valid"
  end
end
