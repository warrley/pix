class CreateAdminBackofficeTables < ActiveRecord::Migration[8.1]
  def change
    create_table :refund_requests do |t|
      t.references :transaction, null: false, foreign_key: true
      t.references :requested_by, null: false, foreign_key: { to_table: :users }
      t.references :reviewed_by, null: true, foreign_key: { to_table: :users }
      t.string :status, limit: 20, null: false, default: "pending"
      t.string :category, limit: 30, null: false, default: "disagreement"
      t.string :reason, limit: 255, null: false
      t.string :decision_note, limit: 255
      t.datetime :reviewed_at

      t.timestamps
    end
    add_index :refund_requests, [ :transaction_id, :status ]
    add_index :refund_requests, :status

    create_table :fraud_reports do |t|
      t.references :reporter, null: false, foreign_key: { to_table: :users }
      t.references :reported_account, null: false, foreign_key: { to_table: :accounts }
      t.references :transaction, null: true, foreign_key: true
      t.references :reviewed_by, null: true, foreign_key: { to_table: :users }
      t.string :status, limit: 20, null: false, default: "pending"
      t.string :category, limit: 30, null: false, default: "scam"
      t.string :description, limit: 500, null: false
      t.string :resolution_note, limit: 255
      t.datetime :reviewed_at

      t.timestamps
    end
    add_index :fraud_reports, :status

    create_table :admin_audit_logs do |t|
      t.references :operator, null: false, foreign_key: { to_table: :users }
      t.string :action, limit: 80, null: false
      t.string :target_type, limit: 60, null: false
      t.bigint :target_id, null: false
      t.jsonb :details, null: false, default: {}
      t.string :ip_address, limit: 64
      t.datetime :created_at, null: false
    end
    add_index :admin_audit_logs, [ :target_type, :target_id ]
    add_index :admin_audit_logs, :created_at
  end
end
