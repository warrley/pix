module Pix
  class KeyValidatorService
    CPF_LENGTH = 11
    CNPJ_LENGTH = 14
    MAX_EMAIL_LENGTH = 77
    PHONE_E164_REGEX = /\A\+55\d{10,11}\z/

    def self.valid_cpf?(cpf)
      clean_cpf = cpf.to_s.gsub(/\D/, "")
      return false if clean_cpf.length != CPF_LENGTH
      return false if clean_cpf.chars.uniq.length == 1

      digits = clean_cpf.chars.map(&:to_i)

      d1 = calculate_cpf_digit(digits[0..8], 10)
      return false if d1 != digits[9]

      d2 = calculate_cpf_digit(digits[0..9], 11)
      return false if d2 != digits[10]

      true
    end

    def self.valid_cnpj?(cnpj)
      clean_cnpj = cnpj.to_s.gsub(/\D/, "")
      return false if clean_cnpj.length != CNPJ_LENGTH
      return false if clean_cnpj.chars.uniq.length == 1

      digits = clean_cnpj.chars.map(&:to_i)

      d1 = calculate_cnpj_digit(digits[0..11], [ 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 ])
      return false if d1 != digits[12]

      d2 = calculate_cnpj_digit(digits[0..12], [ 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 ])
      return false if d2 != digits[13]

      true
    end

    def self.valid_email?(email)
      return false if email.blank? || email.length > MAX_EMAIL_LENGTH

      (email =~ URI::MailTo::EMAIL_REGEXP) != nil
    end

    def self.valid_phone?(phone)
      return false if phone.blank?

      (phone =~ PHONE_E164_REGEX) != nil
    end

    def self.generate_random_key
      SecureRandom.uuid
    end

    private

    def self.calculate_cpf_digit(digits, starting_multiplier)
      sum = digits.each_with_index.sum do |digit, index|
        digit * (starting_multiplier - index)
      end
      remainder = sum % 11
      remainder < 2 ? 0 : 11 - remainder
    end

    def self.calculate_cnpj_digit(digits, weights)
      sum = digits.each_with_index.sum do |digit, index|
        digit * weights[index]
      end
      remainder = sum % 11
      remainder < 2 ? 0 : 11 - remainder
    end
  end
end
