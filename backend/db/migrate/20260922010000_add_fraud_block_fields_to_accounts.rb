class AddFraudBlockFieldsToAccounts < ActiveRecord::Migration[8.0]
  def change
    add_column :accounts, :fraud_blocked, :boolean, default: false, null: false
    add_column :accounts, :fraud_blocked_at, :datetime
    add_column :accounts, :fraud_block_reason, :string, limit: 255
    add_index :accounts, :fraud_blocked
  end
end
