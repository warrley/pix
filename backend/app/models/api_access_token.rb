require "digest"

class ApiAccessToken < ApplicationRecord
  DEFAULT_SCOPES = %w[
    profile:read profile:write
    accounts:read accounts:write
    pix_keys:read pix_keys:write
    transfers:read transfers:write
    reports:read
  ].freeze
  OPERATOR_SCOPES = (DEFAULT_SCOPES + %w[accounts:admin users:admin]).freeze
  LIFETIME = 15.minutes

  belongs_to :user

  validates :token_digest, presence: true, uniqueness: true
  validates :expires_at, presence: true
  validates :scopes, presence: true
  validate :scopes_are_allowed

  scope :active, -> { where(revoked_at: nil).where("expires_at > ?", Time.current) }

  def self.issue!(user:, scopes: nil)
    raw_token = SecureRandom.urlsafe_base64(32)
    allowed_scopes = scopes || (user.operator? ? OPERATOR_SCOPES : DEFAULT_SCOPES)
    record = create!(
      user: user,
      token_digest: digest(raw_token),
      scopes: allowed_scopes,
      expires_at: LIFETIME.from_now
    )
    [ raw_token, record ]
  end

  def self.authenticate(raw_token)
    return unless raw_token.to_s.match?(/\A[A-Za-z0-9_-]{43}\z/)

    record = active.find_by(token_digest: digest(raw_token))
    if record && (record.last_used_at.nil? || record.last_used_at < 5.minutes.ago)
      record.update_column(:last_used_at, Time.current)
    end
    record
  end

  def self.digest(raw_token)
    Digest::SHA256.hexdigest(raw_token)
  end

  def revoke!
    update!(revoked_at: Time.current)
  end

  def scope?(scope)
    scopes.include?(scope)
  end

  private

  def scopes_are_allowed
    allowed = user&.operator? ? OPERATOR_SCOPES : DEFAULT_SCOPES
    errors.add(:scopes, "contain unsupported values") unless (scopes - allowed).empty?
  end
end
