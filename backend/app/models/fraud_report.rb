class FraudReport < ApplicationRecord
  belongs_to :reporter, class_name: "User"
  belongs_to :reported_account, class_name: "Account"
  belongs_to :pix_transaction, class_name: "Transaction", foreign_key: :transaction_id, optional: true
  belongs_to :reviewed_by, class_name: "User", optional: true

  enum :status, {
    pending:      "pending",
    under_review: "under_review",
    resolved:     "resolved",
    dismissed:    "dismissed"
  }, default: :pending, validate: true

  enum :category, {
    scam:                "scam",
    phishing:            "phishing",
    impersonation:       "impersonation",
    suspicious_activity: "suspicious_activity",
    other:               "other"
  }, default: :scam, validate: true

  validates :description, presence: true, length: { maximum: 500 }
  validates :resolution_note, length: { maximum: 255 }, allow_nil: true
  validate :cannot_report_own_account, on: :create

  private

  def cannot_report_own_account
    return unless reporter_id.present? && reported_account&.user_id == reporter_id

    errors.add(:reported_account_id, "Cannot report your own account")
  end
end
