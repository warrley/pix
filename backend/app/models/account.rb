class Account < ApplicationRecord
  attribute :agency_number, :string, default: "0001"
  attribute :balance, :decimal, default: 0.0
  attribute :status, :string, default: "active"

  belongs_to :user

  before_validation :generate_account_number, on: :create

  validates :user, presence: true
  validates :account_number, presence: true, uniqueness: true
  validates :agency_number, presence: true, length: { maximum: 10 }
  validates :status, inclusion: { in: %w[active blocked closed] }
  validates :balance, numericality: { greater_than_or_equal_to: 0 }

  private

  def generate_account_number
    return if account_number.present?

    loop do
      self.account_number = rand(100_000..999_999).to_s
      break unless Account.exists?(account_number: account_number)
    end
  end
end
