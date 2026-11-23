require "test_helper"

class Pix::KeyLifecycleServiceTest < ActiveSupport::TestCase
  setup do
    @account = accounts(:one)
    @user = users(:one)
    @user.update!(doc_id: "52998224725")

    # Clear keys for account to control test state
    @account.pix_keys.destroy_all

    @suspended_key = PixKey.create!(
      account: @account,
      key_type: "email",
      key_value: "suspended@example.com",
      status: :suspended
    )
  end

  test "successfully unblocks a suspended key" do
    result = Pix::KeyLifecycleService.unblock(@suspended_key)

    assert result.success?
    assert_nil result.error
    assert_equal "active", result.pix_key.reload.status
  end

  test "fails to unblock an active key" do
    active_key = PixKey.create!(
      account: @account,
      key_type: "email",
      key_value: "active@example.com",
      status: :active
    )

    result = Pix::KeyLifecycleService.unblock(active_key)

    assert_not result.success?
    assert_equal "Only suspended keys can be unblocked", result.error
  end

  test "fails to unblock a cancelled key" do
    cancelled_key = PixKey.create!(
      account: @account,
      key_type: "email",
      key_value: "cancelled@example.com",
      status: :cancelled
    )

    result = Pix::KeyLifecycleService.unblock(cancelled_key)

    assert_not result.success?
    assert_equal "Only suspended keys can be unblocked", result.error
  end

  test "fails when owning account is fraud-blocked" do
    @account.update!(fraud_blocked: true)

    result = Pix::KeyLifecycleService.unblock(@suspended_key)

    assert_not result.success?
    assert_equal "Account is fraud-blocked", result.error
  end

  test "fails when owning account status is blocked" do
    @account.update!(status: "blocked")

    result = Pix::KeyLifecycleService.unblock(@suspended_key)

    assert_not result.success?
    assert_equal "Account is fraud-blocked", result.error
  end

  test "fails when owning account is closed" do
    @account.update!(status: "closed")

    result = Pix::KeyLifecycleService.unblock(@suspended_key)

    assert_not result.success?
    assert_equal "Account must be active", result.error
  end

  test "fails when account already has 5 active keys" do
    5.times do |i|
      PixKey.create!(
        account: @account,
        key_type: "email",
        key_value: "active#{i}@example.com",
        status: :active
      )
    end

    result = Pix::KeyLifecycleService.unblock(@suspended_key)

    assert_not result.success?
    assert_equal "maximum of 5 active PIX keys per account reached", result.error
  end

  test "fails when another active key with the same value already exists" do
    other_user = User.create!(name: "Other User", email: "other@example.com", doc_id: "04252011000110")
    other_account = Account.create!(user: other_user, balance: 100.0, status: "active")

    PixKey.create!(
      account: other_account,
      key_type: "email",
      key_value: @suspended_key.key_value,
      status: :active
    )

    result = Pix::KeyLifecycleService.unblock(@suspended_key)

    assert_not result.success?
    assert_equal "Key is already registered", result.error
  end

  test "allows unblocking when duplicate key value exists but is cancelled" do
    other_user = User.create!(name: "Other User 2", email: "other2@example.com", doc_id: "04252011000110")
    other_account = Account.create!(user: other_user, balance: 100.0, status: "active")

    PixKey.create!(
      account: other_account,
      key_type: "email",
      key_value: @suspended_key.key_value,
      status: :cancelled
    )

    result = Pix::KeyLifecycleService.unblock(@suspended_key)

    assert result.success?
    assert_equal "active", @suspended_key.reload.status
  end

  test "returns not found for unknown key" do
    result = Pix::KeyLifecycleService.unblock(999_999)

    assert_not result.success?
    assert_equal "Record not found", result.error
  end

  test "unblock! raises error when unblock fails" do
    @account.update!(status: "blocked")

    assert_raises(Pix::KeyLifecycleService::Error) do
      Pix::KeyLifecycleService.unblock!(@suspended_key)
    end
  end

  test "unblock! returns the pix key when successful" do
    unblocked = Pix::KeyLifecycleService.unblock!(@suspended_key)

    assert_equal @suspended_key.id, unblocked.id
    assert_equal "active", unblocked.reload.status
  end

  test "successfully reassociates an active key to another account of the same user" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "portability@example.com", status: :active)

    result = Pix::KeyLifecycleService.reassociate(active_key, target_account)

    assert result.success?
    assert_nil result.error
    assert_equal target_account.id, active_key.reload.account_id
  end

  test "fails to reassociate a suspended key" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")

    result = Pix::KeyLifecycleService.reassociate(@suspended_key, target_account)

    assert_not result.success?
    assert_equal "Only active keys can be moved", result.error
  end

  test "fails to reassociate a cancelled key" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    cancelled_key = PixKey.create!(account: @account, key_type: "email", key_value: "cancelled_move@example.com", status: :cancelled)

    result = Pix::KeyLifecycleService.reassociate(cancelled_key, target_account)

    assert_not result.success?
    assert_equal "Only active keys can be moved", result.error
  end

  test "fails to reassociate to an account of another user" do
    other_user = User.create!(name: "Other User", email: "other_user_assoc@example.com", doc_id: "04252011000110")
    other_account = Account.create!(user: other_user, balance: 100.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "other_user_move@example.com", status: :active)

    result = Pix::KeyLifecycleService.reassociate(active_key, other_account)

    assert_not result.success?
    assert_equal "New account must belong to the same user", result.error
  end

  test "fails to reassociate when target account is blocked" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "blocked")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "target_blocked@example.com", status: :active)

    result = Pix::KeyLifecycleService.reassociate(active_key, target_account)

    assert_not result.success?
    assert_equal "Account is fraud-blocked", result.error
  end

  test "fails to reassociate when target account is fraud-blocked" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active", fraud_blocked: true)
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "target_fraud@example.com", status: :active)

    result = Pix::KeyLifecycleService.reassociate(active_key, target_account)

    assert_not result.success?
    assert_equal "Account is fraud-blocked", result.error
  end

  test "fails to reassociate when target account is closed" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "closed")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "target_closed@example.com", status: :active)

    result = Pix::KeyLifecycleService.reassociate(active_key, target_account)

    assert_not result.success?
    assert_equal "New account must be active", result.error
  end

  test "fails to reassociate when target account already has 5 active keys" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    5.times do |i|
      PixKey.create!(account: target_account, key_type: "email", key_value: "target_active#{i}@example.com", status: :active)
    end
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "target_sixth@example.com", status: :active)

    result = Pix::KeyLifecycleService.reassociate(active_key, target_account)

    assert_not result.success?
    assert_equal "maximum of 5 active PIX keys per account reached", result.error
  end

  test "reassociate returns not found for unknown key or unknown account" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "unknown_test@example.com", status: :active)

    result = Pix::KeyLifecycleService.reassociate(999_999, target_account)
    assert_not result.success?
    assert_equal "Record not found", result.error

    result2 = Pix::KeyLifecycleService.reassociate(active_key, 999_999)
    assert_not result2.success?
    assert_equal "Account not found", result2.error
  end

  test "reassociate! raises error when reassociate fails" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "closed")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "raise_err@example.com", status: :active)

    assert_raises(Pix::KeyLifecycleService::Error) do
      Pix::KeyLifecycleService.reassociate!(active_key, target_account)
    end
  end

  test "reassociate! returns the pix key when successful" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "bang_success@example.com", status: :active)

    reassociated = Pix::KeyLifecycleService.reassociate!(active_key, target_account)

    assert_equal active_key.id, reassociated.id
    assert_equal target_account.id, reassociated.reload.account_id
  end
end
