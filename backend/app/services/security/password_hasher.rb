require "openssl"
require "base64"
require "securerandom"

module Security
  class PasswordHasher
    COST = 16_384
    BLOCK_SIZE = 8
    PARALLELIZATION = 1
    KEY_LENGTH = 32

    def self.create(password)
      salt = SecureRandom.random_bytes(16)
      digest = OpenSSL::KDF.scrypt(
        password,
        salt: salt,
        N: COST,
        r: BLOCK_SIZE,
        p: PARALLELIZATION,
        length: KEY_LENGTH
      )
      [ "scrypt", COST, BLOCK_SIZE, PARALLELIZATION, Base64.strict_encode64(salt), Base64.strict_encode64(digest) ].join("$")
    end

    def self.valid?(password, encoded_digest)
      algorithm, cost, block_size, parallelization, encoded_salt, encoded_expected = encoded_digest.to_s.split("$", 6)
      return false unless algorithm == "scrypt" && encoded_expected.present?
      return false unless [ COST.to_s, BLOCK_SIZE.to_s, PARALLELIZATION.to_s ] == [ cost, block_size, parallelization ]
      return false unless password.is_a?(String) && password.bytesize <= 128

      salt = Base64.strict_decode64(encoded_salt)
      expected = Base64.strict_decode64(encoded_expected)
      actual = OpenSSL::KDF.scrypt(
        password,
        salt: salt,
        N: Integer(cost),
        r: Integer(block_size),
        p: Integer(parallelization),
        length: expected.bytesize
      )
      ActiveSupport::SecurityUtils.secure_compare(actual, expected)
    rescue ArgumentError, OpenSSL::KDF::KDFError
      false
    end
  end
end
