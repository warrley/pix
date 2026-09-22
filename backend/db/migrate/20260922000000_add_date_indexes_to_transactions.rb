class AddDateIndexesToTransactions < ActiveRecord::Migration[8.1]
  def change
    add_index :transactions, [ :source_account_id, :created_at ]
    add_index :transactions, [ :destination_account_id, :created_at ]
  end
end
