class CreatePixKeys < ActiveRecord::Migration[8.1]
  def change
    create_table :pix_keys do |t|
      t.references :account, null: false, foreign_key: true
      t.string :key_type, limit: 10, null: false
      t.string :key_value, limit: 77, null: false
      t.string :status, limit: 20, null: false, default: "active"

      t.timestamps
    end

    add_index :pix_keys, :key_value, unique: true, where: "status = 'active'", name: "index_pix_keys_on_key_value_active"
    add_check_constraint :pix_keys, "key_type IN ('cpf', 'cnpj', 'email', 'phone', 'random')", name: "pix_keys_key_type_valid"
    add_check_constraint :pix_keys, "status IN ('active', 'suspended', 'cancelled')", name: "pix_keys_status_valid"
  end
end
