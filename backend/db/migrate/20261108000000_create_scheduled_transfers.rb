class CreateScheduledTransfers < ActiveRecord::Migration[8.1]
  def change
    create_table :scheduled_transfers do |t|
      t.references :account, null: false, foreign_key: { on_delete: :restrict }
      t.references :destination_account, null: false, foreign_key: { to_table: :accounts, on_delete: :restrict }
      t.string :pix_key_used, limit: 77, null: false
      t.bigint :amount_cents, null: false
      t.string :description, limit: 140
      t.date :scheduled_for, null: false
      t.string :status, limit: 20, null: false, default: "pending"
      t.datetime :processing_started_at
      t.string :failure_reason, limit: 255
      t.references :transaction, foreign_key: { on_delete: :restrict }, index: false
      t.string :execution_key, limit: 32, null: false
      t.timestamps

      t.check_constraint "amount_cents > 0", name: "scheduled_transfers_amount_positive"
      t.check_constraint "status IN ('pending', 'processing', 'executed', 'cancelled', 'failed')", name: "scheduled_transfers_status_valid"
    end

    add_index :scheduled_transfers, :execution_key, unique: true
    add_index :scheduled_transfers, :transaction_id, unique: true
    add_index :scheduled_transfers, [ :status, :scheduled_for ], name: "index_scheduled_transfers_for_processing"
    add_index :scheduled_transfers, [ :account_id, :scheduled_for, :id ], name: "index_scheduled_transfers_for_account_listing"
  end
end
