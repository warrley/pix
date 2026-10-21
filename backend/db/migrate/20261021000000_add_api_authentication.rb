class AddApiAuthentication < ActiveRecord::Migration[8.1]
  def change
    add_column :users, :password_digest, :string
    add_column :users, :role, :string, null: false, default: "customer"
    add_check_constraint :users, "role IN ('customer', 'operator')", name: "users_role_valid"

    create_table :api_access_tokens do |t|
      t.references :user, null: false, foreign_key: { on_delete: :cascade }
      t.string :token_digest, null: false
      t.string :scopes, array: true, null: false, default: []
      t.datetime :expires_at, null: false
      t.datetime :revoked_at
      t.datetime :last_used_at
      t.timestamps
    end

    add_index :api_access_tokens, :token_digest, unique: true
    add_index :api_access_tokens, [ :user_id, :expires_at ]
  end
end
