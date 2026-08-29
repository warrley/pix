require "test_helper"

class UserTest < ActiveSupport::TestCase
  test "accepts a valid CPF" do
    user = User.new(name: "Alice", email: "alice@example.com", doc_id: "52998224725")

    assert user.valid?, -> { user.errors.full_messages.to_sentence }
  end

  test "accepts a valid CNPJ" do
    user = User.new(name: "Empresa Teste", email: "empresa@example.com", doc_id: "11222333000181")

    assert user.valid?, -> { user.errors.full_messages.to_sentence }
  end

  test "rejects an invalid document" do
    user = User.new(name: "Bob", email: "bob@example.com", doc_id: "12345678900")

    assert_not user.valid?
    assert_includes user.errors[:doc_id], "is not a valid CPF or CNPJ"
  end

  test "requires a doc_id" do
    user = User.new(name: "Alice", email: "alice@example.com", doc_id: nil)

    assert_not user.valid?
    assert_includes user.errors[:doc_id], "can't be blank"
  end

  test "requires a name" do
    user = User.new(email: "alice@example.com", doc_id: "52998224725")

    assert_not user.valid?
    assert_includes user.errors[:name], "can't be blank"
  end

  test "requires an email" do
    user = User.new(name: "Alice", doc_id: "52998224725")

    assert_not user.valid?
    assert_includes user.errors[:email], "can't be blank"
  end

  test "rejects duplicate email regardless of case" do
    User.create!(name: "Alice", email: "alice.dup@example.com", doc_id: "51783429097")

    duplicate = User.new(name: "Alice 2", email: "ALICE.DUP@EXAMPLE.COM", doc_id: "11222333000181")

    assert_not duplicate.valid?
    assert_includes duplicate.errors[:email], "has already been taken"
  end

  test "rejects duplicate doc_id" do
    User.create!(name: "Alice", email: "alice.doc@example.com", doc_id: "52998224725")

    duplicate = User.new(name: "Bob", email: "bob.doc@example.com", doc_id: "52998224725")

    assert_not duplicate.valid?
    assert_includes duplicate.errors[:doc_id], "has already been taken"
  end

  test "rejects phone with wrong format" do
    user = User.new(name: "Alice", email: "alice@example.com", doc_id: "52998224725", phone: "1199999")

    assert_not user.valid?
    assert_includes user.errors[:phone], "must be in E.164 format (+55DDXXXXXXXXX)"
  end

  test "accepts phone as nil or empty string" do
    user_nil = User.new(name: "Alice", email: "alice1@example.com", doc_id: "52998224725", phone: nil)
    user_blank = User.new(name: "Alice", email: "alice2@example.com", doc_id: "51783429097", phone: "")

    assert user_nil.valid?, -> { user_nil.errors.full_messages.to_sentence }
    assert user_blank.valid?, -> { user_blank.errors.full_messages.to_sentence }
  end

  test "rejects repeated digits doc_id" do
    user = User.new(name: "Alice", email: "alice@example.com", doc_id: "11111111111")

    assert_not user.valid?
    assert_includes user.errors[:doc_id], "is not a valid CPF or CNPJ"
  end
end
