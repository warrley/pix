class AddLocationToTransactionsAndEvents < ActiveRecord::Migration[8.1]
  def change
    add_column :transactions, :latitude, :decimal, precision: 10, scale: 8
    add_column :transactions, :longitude, :decimal, precision: 11, scale: 8
    add_column :transaction_events, :latitude, :decimal, precision: 10, scale: 8
    add_column :transaction_events, :longitude, :decimal, precision: 11, scale: 8

    add_check_constraint :transactions,
      "(latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL)",
      name: "transactions_location_complete"
    add_check_constraint :transactions,
      "latitude IS NULL OR latitude BETWEEN -90 AND 90",
      name: "transactions_latitude_valid"
    add_check_constraint :transactions,
      "longitude IS NULL OR longitude BETWEEN -180 AND 180",
      name: "transactions_longitude_valid"

    add_check_constraint :transaction_events,
      "(latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL)",
      name: "transaction_events_location_complete"
    add_check_constraint :transaction_events,
      "latitude IS NULL OR latitude BETWEEN -90 AND 90",
      name: "transaction_events_latitude_valid"
    add_check_constraint :transaction_events,
      "longitude IS NULL OR longitude BETWEEN -180 AND 180",
      name: "transaction_events_longitude_valid"
  end
end
