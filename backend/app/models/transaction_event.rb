class TransactionEvent < ApplicationRecord
  belongs_to :transaction_record, class_name: "Transaction", foreign_key: :transaction_id
  belongs_to :source_account, class_name: "Account"
  belongs_to :destination_account, class_name: "Account"

  validates :transaction_record, :current_status, :amount, :source_account, :destination_account, presence: true
  validates :previous_status, length: { maximum: 20 }, allow_nil: true
  validates :current_status, length: { maximum: 20 }
  validates :amount_cents, numericality: true

  def amount
    Pix::Money.decimal_from_cents(amount_cents)
  end

  def amount=(value)
    self.amount_cents = Pix::Money.cents_from_decimal(value)
  end

  def as_json(options = {})
    super(options).except("amount_cents").merge("amount" => Pix::Money.format_cents(amount_cents))
  end
  validates :reason, length: { maximum: 255 }, allow_nil: true

  def readonly?
    persisted? || super
  end
end
