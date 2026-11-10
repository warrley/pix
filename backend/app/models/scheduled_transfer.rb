class ScheduledTransfer < ApplicationRecord
  STATUSES = %w[pending processing executed cancelled failed].freeze

  belongs_to :account
  belongs_to :destination_account, class_name: "Account"
  belongs_to :completed_transaction, class_name: "Transaction", foreign_key: :transaction_id, optional: true

  enum :status, STATUSES.index_with(&:itself), default: :pending, validate: true

  before_validation :assign_execution_key, on: :create

  validates :pix_key_used, presence: true, length: { maximum: 77 }
  validates :amount_cents, numericality: { only_integer: true, greater_than: 0 }
  validates :description, length: { maximum: 140 }, allow_nil: true
  validates :scheduled_for, presence: true
  validates :execution_key, presence: true, uniqueness: true, length: { is: 32 }
  validates :failure_reason, length: { maximum: 255 }, allow_nil: true
  validate :scheduled_for_is_future, on: :create

  def amount
    Pix::Money.decimal_from_cents(amount_cents)
  end

  def amount=(value)
    self.amount_cents = Pix::Money.cents_from_decimal(value)
  end

  def as_json(options = {})
    super(options).except("amount_cents", "execution_key", "processing_started_at").merge("amount" => Pix::Money.format_cents(amount_cents))
  end

  def rotate_execution_key!
    update!(execution_key: new_execution_key)
  end

  private

  def assign_execution_key
    self.execution_key ||= new_execution_key
  end

  def new_execution_key
    "E01011010#{Time.current.strftime('%Y%m%d%H%M')}#{SecureRandom.alphanumeric(11).downcase}"
  end

  def scheduled_for_is_future
    return if scheduled_for.blank? || scheduled_for > Time.current.in_time_zone("America/Sao_Paulo").to_date

    errors.add(:scheduled_for, "must be a future date")
  end
end
