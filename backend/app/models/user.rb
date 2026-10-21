class User < ApplicationRecord
  before_validation :normalize_attributes

  has_many :accounts, dependent: :restrict_with_error
  has_many :api_access_tokens, dependent: :destroy
  after_update :revoke_tokens_after_password_change, if: :saved_change_to_password_digest?

  attr_reader :password

  validates :name, presence: true
  validates :email, presence: true,
                    uniqueness: { case_sensitive: false },
                    format: { with: URI::MailTo::EMAIL_REGEXP }
  validates :doc_id, presence: true, cpf_cnpj: true, uniqueness: true
  validates :role, inclusion: { in: %w[customer operator] }
  validates :phone, format: { with: /\A\+55\d{10,11}\z/, message: "must be in E.164 format (+55DDXXXXXXXXX)" },
                    allow_blank: true
  validates :password, length: { minimum: 12, maximum: 128 }, allow_nil: true
  validates :password, format: { with: /\S/, message: "must contain a non-whitespace character" }, allow_nil: true

  def password=(value)
    @password = value
    self.password_digest = Security::PasswordHasher.create(value) if value.present?
  end

  def authenticate_password(value)
    return false if password_digest.blank?

    Security::PasswordHasher.valid?(value.to_s, password_digest)
  end

  def operator?
    role == "operator"
  end

  private

  def normalize_attributes
    self.doc_id = doc_id.to_s.gsub(/\D/, "") if doc_id.present?
    self.email = email.to_s.strip.downcase if email.present?
  end

  def revoke_tokens_after_password_change
    api_access_tokens.active.update_all(revoked_at: Time.current, updated_at: Time.current)
  end
end
