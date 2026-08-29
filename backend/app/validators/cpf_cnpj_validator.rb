
class CpfCnpjValidator < ActiveModel::EachValidator
  def validate_each(record, attribute, value)
    return if value.blank?

    normalized = value.to_s.gsub(/\D/, "")

    valid = case normalized.length
            when 11 then valid_cpf?(normalized)
            when 14 then valid_cnpj?(normalized)
            else false
            end

    record.errors.add(attribute, "não é um CPF ou CNPJ válido") unless valid
  end

  private

  def valid_cpf?(doc_id)
    return false unless doc_id.length == 11
    return false if doc_id.match?(/\A(\d)\1{10}\z/)

    digits = doc_id.chars.map(&:to_i)

    sum = digits[0, 9].each_with_index.sum { |digit, index| digit * (10 - index) }
    first_digit = sum % 11
    first_digit = first_digit < 2 ? 0 : 11 - first_digit

    return false if digits[9] != first_digit

    base_with_first_digit = digits[0, 9] + [first_digit]
    sum = base_with_first_digit.each_with_index.sum { |digit, index| digit * (11 - index) }
    second_digit = sum % 11
    second_digit = second_digit < 2 ? 0 : 11 - second_digit

    digits[10] == second_digit
  end

  def valid_cnpj?(doc_id)
    return false unless doc_id.length == 14
    return false if doc_id.match?(/\A(\d)\1{13}\z/)

    digits = doc_id.chars.map(&:to_i)

    weights = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    sum = digits[0, 12].each_with_index.sum { |digit, index| digit * weights[index] }
    first_digit = sum % 11
    first_digit = first_digit < 2 ? 0 : 11 - first_digit

    return false if digits[12] != first_digit

    base_with_first_digit = digits[0, 12] + [first_digit]
    weights = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    sum = base_with_first_digit.each_with_index.sum { |digit, index| digit * weights[index] }
    second_digit = sum % 11
    second_digit = second_digit < 2 ? 0 : 11 - second_digit

    digits[13] == second_digit
  end
end