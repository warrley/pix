require "minitest/autorun"
require "active_support/security_utils"
require "active_support/core_ext/numeric/time"
require "active_support/core_ext/time"
require_relative "../../../app/services/notifications/webhook_signer"

class Notifications::WebhookSignerTest < Minitest::Test
  def test_signature_is_deterministic_for_the_same_body_secret_and_timestamp
    first = Notifications::WebhookSigner.sign(body: '{"id":1}', secret: "secret", timestamp: 1_800_000_000)
    second = Notifications::WebhookSigner.sign(body: '{"id":1}', secret: "secret", timestamp: 1_800_000_000)

    assert_equal first, second
  end

  def test_verification_rejects_body_changes_wrong_secrets_and_stale_timestamps
    signature = Notifications::WebhookSigner.sign(body: "body", secret: "secret", timestamp: 1_800_000_000)

    assert Notifications::WebhookSigner.valid?(body: "body", secret: "secret", **signature, now: Time.at(1_800_000_000))
    refute Notifications::WebhookSigner.valid?(body: "changed", secret: "secret", **signature, now: Time.at(1_800_000_000))
    refute Notifications::WebhookSigner.valid?(body: "body", secret: "wrong", **signature, now: Time.at(1_800_000_000))
    refute Notifications::WebhookSigner.valid?(body: "body", secret: "secret", **signature, now: Time.at(1_800_000_000 + 600))
  end
end
