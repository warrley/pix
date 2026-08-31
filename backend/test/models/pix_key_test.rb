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
    assert_includes sixth_key.errors[:base], "Maximum of 5 active PIX keys per account reached"
  end

  test "normalizes email to lowercase" do
    pix_key = PixKey.new(account: @account, key_type: "email", key_value: "USER@EXAMPLE.COM")
    pix_key.valid?
    assert_equal "user@example.com", pix_key.key_value
  end
end
