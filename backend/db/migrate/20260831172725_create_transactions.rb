class CreateTransactions < ActiveRecord::Migration[8.1]
  def change
    create_table :transactions do |t|
      t.string :end_to_end_id, limit: 32, null: false
      t.references :source_account, null: false, foreign_key: { to_table: :accounts }
      t.references :destination_account, null: false, foreign_key: { to_table: :accounts }
      t.string :pix_key_used, limit: 77, null: false
      t.decimal :amount, precision: 14, scale: 2, null: false
      t.string :description, limit: 140
      t.string :status, limit: 20, null: false, default: "processing"
      t.string :failure_reason, limit: 255

      t.timestamps
    end

    add_index :transactions, :end_to_end_id, unique: true

    add_check_constraint :transactions, "amount > 0", name: "transactions_amount_positive"
    add_check_constraint :transactions, "source_account_id <> destination_account_id", name: "transactions_no_self_transfer"
    add_check_constraint :transactions, "status IN ('processing', 'completed', 'failed', 'cancelled')", name: "transactions_status_valid"
  end
end
