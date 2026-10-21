class Transaction < ApplicationRecord
  enum :status, {
    processing: "processing",
    completed:  "completed",
    failed:     "failed",
    cancelled:  "cancelled"
  }, default: :processing, validate: true

  belongs_to :source_account, class_name: "Account"
  belongs_to :destination_account, class_name: "Account"
  has_many :transaction_events, dependent: :restrict_with_error

  validates :end_to_end_id, presence: true, uniqueness: true, length: { is: 32 }
  validates :pix_key_used, presence: true, length: { maximum: 77 }
  validates :amount_cents, numericality: { greater_than: 0 }
  validates :description, length: { maximum: 140 }, allow_nil: true
  validates :failure_reason, length: { maximum: 255 }, allow_nil: true
  validate :prevent_self_transfer

  def amount
    Pix::Money.decimal_from_cents(amount_cents)
  end

  def amount=(value)
    self.amount_cents = Pix::Money.cents_from_decimal(value)
  end

  def as_json(options = {})
    super(options).except("amount_cents").merge("amount" => Pix::Money.format_cents(amount_cents))
  end

  private

  def prevent_self_transfer
    if source_account_id.present? && source_account_id == destination_account_id
      errors.add(:destination_account_id, "cannot be the same as source account")
    end
  end
end
