class User < ApplicationRecord
  validates :name, presence: true
  validates :email, presence: true,
                    uniqueness: { case_sensitive: false },
                    format: { with: URI::MailTo::EMAIL_REGEXP }
  validates :doc_id, cpf_cnpj: true
  validates :phone, format: { with: /\A\+55\d{10,11}\z/, message: "deve estar no formato E.164 (+55DDXXXXXXXXX)" },
                    allow_nil: true
end
