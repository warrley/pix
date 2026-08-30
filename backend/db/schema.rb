# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.1].define(version: 2026_08_29_010000) do
  # These are extensions that must be enabled in order to support this database
  enable_extension "pg_catalog.plpgsql"

  create_table "accounts", force: :cascade do |t|
    t.string "account_number", limit: 20, null: false
    t.string "agency_number", limit: 10, default: "0001", null: false
    t.decimal "balance", precision: 14, scale: 2, default: "0.0", null: false
    t.datetime "created_at", null: false
    t.string "status", limit: 20, default: "active", null: false
    t.datetime "updated_at", null: false
    t.bigint "user_id", null: false
    t.index ["account_number"], name: "index_accounts_on_account_number", unique: true
    t.index ["user_id"], name: "index_accounts_on_user_id"
    t.check_constraint "balance >= 0::numeric", name: "accounts_balance_non_negative"
    t.check_constraint "status::text = ANY (ARRAY['active'::character varying, 'blocked'::character varying, 'closed'::character varying]::text[])", name: "accounts_status_valid"
  end

  create_table "users", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.string "doc_id", limit: 14, null: false
    t.string "email", null: false
    t.string "name", null: false
    t.string "phone", limit: 15
    t.datetime "updated_at", null: false
    t.index "lower((email)::text)", name: "index_users_on_lower_email", unique: true
    t.index ["doc_id"], name: "index_users_on_doc_id", unique: true
  end

  add_foreign_key "accounts", "users"
end
