class User < ApplicationRecord
  before_validation :normalize_attributes

  validates :name, presence: true
  validates :email, presence: true,
                    uniqueness: { case_sensitive: false },
                    format: { with: URI::MailTo::EMAIL_REGEXP }
  validates :doc_id, presence: true, cpf_cnpj: true, uniqueness: true
  validates :phone, format: { with: /\A\+55\d{10,11}\z/, message: "must be in E.164 format (+55DDXXXXXXXXX)" },
                    allow_blank: true

  private
  def normalize_attributes
    self.doc_id = doc_id.to_s.gsub(/\D/, "") if doc_id.present?
    self.email = email.to_s.strip.downcase if email.present?
  end
end
