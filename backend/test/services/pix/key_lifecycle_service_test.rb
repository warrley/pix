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

  test "successfully suspends an active key and rejects already suspended or cancelled keys" do
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "active_suspend@example.com", status: :active)

    result = Pix::KeyLifecycleService.suspend(active_key)
    assert result.success?
    assert_equal "suspended", active_key.reload.status

    again = Pix::KeyLifecycleService.suspend(active_key)
    assert_not again.success?
    assert_equal "Only active keys can be suspended", again.error
  end

  test "reassociates an active key to another active account of the same user" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "move_svc@example.com", status: :active)

    result = Pix::KeyLifecycleService.reassociate(active_key, target_account)

    assert result.success?
    assert_equal target_account.id, active_key.reload.account_id
  end

  test "fails to reassociate a suspended key or to another user account" do
    target_account = Account.create!(user: @user, balance: 100.0, status: "active")
    res1 = Pix::KeyLifecycleService.reassociate(@suspended_key, target_account)
    assert_not res1.success?
    assert_equal "Only active keys can be moved", res1.error

    other_user = User.create!(name: "Other User 3", email: "other3@example.com", doc_id: "04252011000110")
    other_account = Account.create!(user: other_user, balance: 100.0, status: "active")
    active_key = PixKey.create!(account: @account, key_type: "email", key_value: "move_other_user@example.com", status: :active)
    res2 = Pix::KeyLifecycleService.reassociate(active_key, other_account)
    assert_not res2.success?
    assert_equal "New account must belong to the same user", res2.error
  end
end
