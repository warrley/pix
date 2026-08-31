class PixKey < ApplicationRecord
  enum :status, {
    active:    "active",
    suspended: "suspended",
    cancelled: "cancelled"
  }, default: :active, validate: true

  enum :key_type, {
    cpf:    "cpf",
    cnpj:   "cnpj",
    email:  "email",
    phone:  "phone",
    random: "random"
  }, validate: true

  belongs_to :account
  has_one :user, through: :account

  before_validation :generate_random_key_value, if: :random?
  before_validation :normalize_key_value

  validates :account, presence: true
  validates :key_type, presence: true
  validates :key_value, presence: true,
                        length: { maximum: 77 },
                        uniqueness: { conditions: -> { where(status: "active") }, message: "is already registered" }

  validate :validate_key_value_format
  validate :max_active_keys_per_account, on: :create
  validate :tax_id_key_must_match_account_owner
  validate :account_must_be_active, on: :create

  private

  def generate_random_key_value
    self.key_value = Pix::KeyValidatorService.generate_random_key if key_value.blank?
  end

  def normalize_key_value
    return if key_value.blank?

    case key_type
    when "cpf", "cnpj"
      self.key_value = key_value.to_s.gsub(/\D/, "")
    when "email"
      self.key_value = key_value.to_s.strip.downcase
    when "phone"
      self.key_value = key_value.to_s.strip
    end
  end

  def validate_key_value_format
    return if key_value.blank? || key_type.blank?

    valid = case key_type
            when "cpf"    then Pix::KeyValidatorService.valid_cpf?(key_value)
            when "cnpj"   then Pix::KeyValidatorService.valid_cnpj?(key_value)
            when "email"  then Pix::KeyValidatorService.valid_email?(key_value)
            when "phone"  then Pix::KeyValidatorService.valid_phone?(key_value)
            when "random" then key_value.length == 36 # UUID format
            else false
            end

    errors.add(:key_value, "is invalid for type #{key_type}") unless valid
  end

  def max_active_keys_per_account
    return unless account
    return unless active?

    if account.pix_keys.where(status: "active").count >= 5
      errors.add(:base, "maximum of 5 active PIX keys per account reached")
    end
  end

  def tax_id_key_must_match_account_owner
    return unless (cpf? || cnpj?) && key_value.present? && account&.user

    owner_doc = account.user.doc_id.to_s.gsub(/\D/, "")
    doc_label = cpf? ? "CPF" : "CNPJ"
    if key_value != owner_doc
      errors.add(:key_value, "must match account owner's #{doc_label} (#{owner_doc})")
    end
  end

  def account_must_be_active
    return unless account

    errors.add(:account, "must be active to register a PIX key") unless account.active?
  end
end
