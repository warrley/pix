require "test_helper"

class Pix::KeyValidatorServiceTest < ActiveSupport::TestCase
  test "validates correct CPF" do
    assert Pix::KeyValidatorService.valid_cpf?("52998224725")
    assert Pix::KeyValidatorService.valid_cpf?("529.982.247-25")
  end

  test "rejects invalid CPF check digits" do
    assert_not Pix::KeyValidatorService.valid_cpf?("12345678900")
    assert_not Pix::KeyValidatorService.valid_cpf?("52998224700")
  end

  test "rejects CPF with all same digits" do
    assert_not Pix::KeyValidatorService.valid_cpf?("00000000000")
    assert_not Pix::KeyValidatorService.valid_cpf?("11111111111")
  end

  test "validates correct CNPJ" do
    assert Pix::KeyValidatorService.valid_cnpj?("11444777000161")
    assert Pix::KeyValidatorService.valid_cnpj?("11.444.777/0001-61")
  end

  test "rejects invalid CNPJ check digits" do
    assert_not Pix::KeyValidatorService.valid_cnpj?("11444777000100")
  end

  test "rejects CNPJ with all same digits" do
    assert_not Pix::KeyValidatorService.valid_cnpj?("00000000000000")
  end

  test "validates email format" do
    assert Pix::KeyValidatorService.valid_email?("test@example.com")
    assert_not Pix::KeyValidatorService.valid_email?("invalid-email")
    assert_not Pix::KeyValidatorService.valid_email?("a" * 80 + "@test.com")
  end

  test "validates E.164 phone format" do
    assert Pix::KeyValidatorService.valid_phone?("+5585999999999")
    assert Pix::KeyValidatorService.valid_phone?("+5511988887777")
    assert_not Pix::KeyValidatorService.valid_phone?("(85) 99999-9999")
    assert_not Pix::KeyValidatorService.valid_phone?("85999999999")
  end

  test "generates valid random UUID key" do
    uuid = Pix::KeyValidatorService.generate_random_key
    assert_equal 36, uuid.length
    assert_match(/\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/i, uuid)
  end
end
