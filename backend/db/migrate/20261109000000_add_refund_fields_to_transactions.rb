class AddRefundFieldsToTransactions < ActiveRecord::Migration[8.1]
  def up
    add_column :transactions, :refunded_at, :datetime

    remove_check_constraint :transactions, name: "transactions_status_valid"
    add_check_constraint :transactions,
                         "status IN ('processing', 'completed', 'failed', 'cancelled', 'refunded')",
                         name: "transactions_status_valid"
  end

  def down
    remove_check_constraint :transactions, name: "transactions_status_valid"
    add_check_constraint :transactions,
                         "status IN ('processing', 'completed', 'failed', 'cancelled')",
                         name: "transactions_status_valid"

    remove_column :transactions, :refunded_at
  end
end
