class AdminAuditLog < ApplicationRecord
  belongs_to :operator, class_name: "User"

  validates :action, presence: true, length: { maximum: 80 }
  validates :target_type, presence: true, length: { maximum: 60 }
  validates :target_id, presence: true

  def self.record!(operator:, action:, target:, details: {}, request: nil)
    create!(
      operator: operator,
      action: action,
      target_type: target.class.name,
      target_id: target.id,
      details: details || {},
      ip_address: request&.remote_ip,
      created_at: Time.current
    )
  end
end
