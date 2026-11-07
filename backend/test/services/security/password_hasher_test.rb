require "minitest/autorun"
require "active_support/security_utils"
require "active_support/core_ext/object/blank"
require_relative "../../../app/services/security/password_hasher"

class Security::PasswordHasherTest < Minitest::Test
  def test_verifies_password_and_rejects_changes
    digest = Security::PasswordHasher.create("long secure passphrase")

    assert Security::PasswordHasher.valid?("long secure passphrase", digest)
    refute Security::PasswordHasher.valid?("different passphrase", digest)
  end

  def test_uses_a_unique_salt_and_rejects_oversized_passwords
    first = Security::PasswordHasher.create("long secure passphrase")
    second = Security::PasswordHasher.create("long secure passphrase")

    refute_equal first, second
    refute Security::PasswordHasher.valid?("x" * 129, first)
  end
end
