class RefundRequest < ApplicationRecord
  belongs_to :pix_transaction, class_name: "Transaction", foreign_key: :transaction_id
  belongs_to :requested_by, class_name: "User"
  belongs_to :reviewed_by, class_name: "User", optional: true

  enum :status, {
    pending:  "pending",
    approved: "approved",
    rejected: "rejected"
  }, default: :pending, validate: true

  enum :category, {
    fraud:             "fraud",
    operational_error: "operational_error",
    disagreement:      "disagreement",
    other:             "other"
  }, default: :disagreement, validate: true

  validates :reason, presence: true, length: { maximum: 255 }
  validates :decision_note, length: { maximum: 255 }, allow_nil: true
  validate :transaction_must_be_completed, on: :create
  validate :no_duplicate_pending_request, on: :create

  private

  def transaction_must_be_completed
    return if pix_transaction&.completed?

    errors.add(:transaction_id, "Only completed transactions can be requested for refund")
  end

  def no_duplicate_pending_request
    return unless transaction_id.present?
    return unless self.class.where(transaction_id: transaction_id, status: "pending").exists?

    errors.add(:transaction_id, "A pending refund request already exists for this transaction")
  end
end
