require "test_helper"

class PixKeyTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @user.update!(doc_id: "52998224725") # valid CPF for testing
    @account = accounts(:one)
  end

  test "valid with correct attributes for email key" do
    pix_key = PixKey.new(account: @account, key_type: "email", key_value: "user@domain.com")
    assert pix_key.valid?
  end

  test "valid with matching CPF key for account owner" do
    pix_key = PixKey.new(account: @account, key_type: "cpf", key_value: "52998224725")
    assert pix_key.valid?
  end

  test "invalid when CPF key does not match account owner" do
    pix_key = PixKey.new(account: @account, key_type: "cpf", key_value: "01234567890") # different valid CPF
    assert_not pix_key.valid?
    assert_includes pix_key.errors[:key_value].join, "must match account owner's CPF"
  end

  test "validates phone in E.164 format" do
    pix_key = PixKey.new(account: @account, key_type: "phone", key_value: "+5585999999999")
    assert pix_key.valid?

    invalid_phone = PixKey.new(account: @account, key_type: "phone", key_value: "85999999999")
    assert_not invalid_phone.valid?
  end

  test "auto-generates random UUID for random key type" do
    pix_key = PixKey.new(account: @account, key_type: "random")
    assert pix_key.valid?
    assert_not_nil pix_key.key_value
    assert_equal 36, pix_key.key_value.length
  end

  test "enforces max 5 active keys per account" do
    @account.pix_keys.destroy_all

    5.times do |i|
      PixKey.create!(account: @account, key_type: "email", key_value: "key#{i}@test.com")
    end

    sixth_key = PixKey.new(account: @account, key_type: "email", key_value: "key6@test.com")
    assert_not sixth_key.valid?
    assert_includes sixth_key.errors[:base], "maximum of 5 active PIX keys per account reached"
  end

  test "normalizes email to lowercase" do
    pix_key = PixKey.new(account: @account, key_type: "email", key_value: "USER@EXAMPLE.COM")
    pix_key.valid?
    assert_equal "user@example.com", pix_key.key_value
  end

  test "invalid when account is not active on create" do
    @account.update!(status: "closed")
    pix_key = PixKey.new(account: @account, key_type: "email", key_value: "inactive@example.com")
    assert_not pix_key.valid?
    assert_includes pix_key.errors[:account], "must be active to register a PIX key"
  end

  test "invalid when active key already exists with same value" do
    PixKey.create!(account: @account, key_type: "email", key_value: "unique@example.com")
    duplicate_key = PixKey.new(account: @account, key_type: "email", key_value: "unique@example.com")

    assert_not duplicate_key.valid?
    assert_includes duplicate_key.errors[:key_value], "is already registered"
  end

  test "valid with matching CNPJ key for account owner" do
    @user.update!(doc_id: "04252011000110") # valid CNPJ
    pix_key = PixKey.new(account: @account, key_type: "cnpj", key_value: "04252011000110")
    assert pix_key.valid?
  end

  test "invalid when CNPJ key does not match account owner" do
    @user.update!(doc_id: "04252011000110") # valid CNPJ
    pix_key = PixKey.new(account: @account, key_type: "cnpj", key_value: "11222333000181") # different valid CNPJ
    assert_not pix_key.valid?
    assert_includes pix_key.errors[:key_value].join, "must match account owner's CNPJ"
  end

  test "unblock! successfully reactivates a suspended key" do
    suspended_key = PixKey.create!(account: @account, key_type: "email", key_value: "suspended_model@example.com", status: :suspended)
    assert suspended_key.unblock!
    assert_equal "active", suspended_key.reload.status
  end

  test "unblock! raises error when key is not suspended" do
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "active_model@example.com", status: :active)
    err = assert_raises(StandardError) do
      active_key.unblock!
    end
    assert_equal "Only suspended keys can be unblocked", err.message
  end

  test "unblock! raises error when owning account is blocked or inactive" do
    suspended_key = PixKey.create!(account: @account, key_type: "email", key_value: "suspended_blocked@example.com", status: :suspended)
    @account.update!(status: "blocked")

    err = assert_raises(StandardError) do
      suspended_key.unblock!
    end
    assert_equal "Account is fraud-blocked", err.message
  end

  test "reassociate_to! moves key to another account of the same owner" do
    target_account = Account.create!(user: @user, balance: 50.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "reassociate_model@example.com", status: :active)

    assert active_key.reassociate_to!(target_account)
    assert_equal target_account.id, active_key.reload.account_id
  end

  test "reassociate_to! raises error when target account belongs to another user" do
    other_user = User.create!(name: "Another User", email: "another_model@example.com", doc_id: "04252011000110")
    other_account = Account.create!(user: other_user, balance: 50.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "reassociate_other_model@example.com", status: :active)

    err = assert_raises(StandardError) do
      active_key.reassociate_to!(other_account)
    end
    assert_equal "New account must belong to the same user", err.message
  end

  test "reassociate_to! raises error when key is not active" do
    target_account = Account.create!(user: @user, balance: 50.0, status: "active")
    suspended_key = PixKey.create!(account: @account, key_type: "email", key_value: "reassociate_suspended_model@example.com", status: :suspended)

    err = assert_raises(StandardError) do
      suspended_key.reassociate_to!(target_account)
    end
    assert_equal "Only active keys can be moved", err.message
  end

  test "reassociate_to! raises error when target account exceeds 5 active keys" do
    target_account = Account.create!(user: @user, balance: 50.0, status: "active")
    5.times do |i|
      PixKey.create!(account: target_account, key_type: "email", key_value: "target_model_limit_#{i}@example.com", status: :active)
    end
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "reassociate_limit_model@example.com", status: :active)

    err = assert_raises(StandardError) do
      active_key.reassociate_to!(target_account)
    end
    assert_equal "maximum of 5 active PIX keys per account reached", err.message
  end
end
