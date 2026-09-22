class AddCancelledAtToTransactions < ActiveRecord::Migration[8.1]
  def change
    add_column :transactions, :cancelled_at, :datetime
  end
end
