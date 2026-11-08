require "test_helper"

class Pix::MoneyTest < ActiveSupport::TestCase
  test "parses decimal strings as integer cents without floating point" do
    assert_equal 1_050, Pix::Money.parse_cents("10.50")
    assert_equal 1_000, Pix::Money.parse_cents("10")
    assert_equal "10.50", Pix::Money.format_cents(1_050)
  end

  test "rejects more than two decimal places and numeric floats" do
    assert_raises(Pix::Money::InvalidAmount) { Pix::Money.parse_cents("1.001") }
    assert_raises(Pix::Money::InvalidAmount) { Pix::Money.parse_cents(1.25) }
  end

  test "enforces the system transfer ceiling" do
    assert_equal 999_999_999, Pix::Money.parse_cents("9999999.99")
    assert_raises(Pix::Money::AmountLimitExceeded) { Pix::Money.parse_cents("10000000.00") }
  end

  test "formats negative balances without losing the sign or cents" do
    assert_equal "-0.50", Pix::Money.format_cents(-50)
  end
end
