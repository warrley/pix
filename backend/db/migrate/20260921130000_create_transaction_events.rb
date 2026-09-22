class CreateTransactionEvents < ActiveRecord::Migration[8.1]
  def change
    create_table :transaction_events do |t|
      t.references :transaction, null: false, foreign_key: { on_delete: :restrict }
      t.string :previous_status, limit: 20
      t.string :current_status, limit: 20, null: false
      t.decimal :amount, precision: 14, scale: 2, null: false
      t.references :source_account, null: false, foreign_key: { to_table: :accounts, on_delete: :restrict }
      t.references :destination_account, null: false, foreign_key: { to_table: :accounts, on_delete: :restrict }
      t.string :reason, limit: 255

      t.datetime :created_at, null: false
    end
  end
end
