class Account < ApplicationRecord
  attribute :agency_number, :string, default: "0001"

  enum :status, {
    active:  "active",
    blocked: "blocked",
    closed:  "closed"
  }, default: :active, validate: true

  belongs_to :user
  has_many :pix_keys, dependent: :restrict_with_error
  has_many :sent_transactions, class_name: "Transaction", foreign_key: :source_account_id, dependent: :restrict_with_error
  has_many :received_transactions, class_name: "Transaction", foreign_key: :destination_account_id, dependent: :restrict_with_error

  before_validation :generate_account_number, on: :create

  validates :user, presence: true
  validates :account_number, presence: true, uniqueness: true
  validates :agency_number, presence: true, length: { maximum: 10 }
  validates :balance, numericality: { greater_than_or_equal_to: 0 }

  def balance
    Pix::Money.decimal_from_cents(balance_cents)
  end

  def balance=(value)
    self.balance_cents = Pix::Money.cents_from_decimal(value)
  end

  def as_json(options = {})
    super(options).except("balance_cents").merge("balance" => Pix::Money.format_cents(balance_cents))
  end

  private

  def generate_account_number
    return if account_number.present?

    loop do
      self.account_number = rand(100_000..999_999).to_s
      break unless Account.exists?(account_number: account_number)
    end
  end
end
