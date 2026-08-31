class CreateUsers < ActiveRecord::Migration[8.1]
  def change
    create_table :users do |t|
      t.string :name, null: false
      t.string :doc_id, limit: 14, null: false
      t.string :email, null: false
      t.string :phone, limit: 15

      t.timestamps
    end

    add_index :users, :doc_id, unique: true
    add_index :users, "lower(email)", unique: true, name: "index_users_on_lower_email"
  end
end
