require "test_helper"

class Pix::AccountStatementQueryTest < ActiveSupport::TestCase
  setup do
    @user1 = User.new(name: "Alice", email: "alice@example.com", doc_id: "68072551061")
    @user1.save!(validate: false)

    @user2 = User.new(name: "Bob", email: "bob@example.com", doc_id: "33734063071")
    @user2.save!(validate: false)

    @account1 = Account.create!(user: @user1, account_number: "11111", balance: 1000.0)
    @account2 = Account.create!(user: @user2, account_number: "22222", balance: 500.0)

    @tx1 = Transaction.create!(
      amount: 150.0,
      source_account: @account1,
      destination_account: @account2,
      end_to_end_id: "E01011010202609211000abcde123451",
      pix_key_used: "bob@example.com",
      status: :completed,
      created_at: Time.utc(2026, 9, 20, 0, 0, 0)
    )

    @tx2 = Transaction.create!(
      amount: 50.0,
      source_account: @account2,
      destination_account: @account1,
      end_to_end_id: "E01011010202609211000abcde123452",
      pix_key_used: "alice@example.com",
      status: :completed,
      created_at: Time.utc(2026, 9, 21, 23, 59, 59)
    )
  end

  test "should return both debits and credits for an account sorted newest first" do
    result = Pix::AccountStatementQuery.call(account_id: @account1.id)

    transactions = result[:transactions]
    assert_equal 2, transactions.size

    # Mais recente primeiro (@tx2) -> source=Account2, dest=Account1 -> credit para Account1
    assert_equal @tx2.id, transactions[0][:id]
    assert_equal "credit", transactions[0][:operation_type]

    # @tx1 -> source=Account1, dest=Account2 -> debit para Account1
    assert_equal @tx1.id, transactions[1][:id]
    assert_equal "debit", transactions[1][:operation_type]
  end

  test "should handle pagination correctly" do
    result = Pix::AccountStatementQuery.call(account_id: @account1.id, page: 1, per_page: 1)

    assert_equal 1, result[:transactions].size
    assert_equal 2, result[:pagination][:total_count]
    assert_equal 1, result[:pagination][:current_page]
    assert_equal 2, result[:pagination][:total_pages]
  end

  test "filters transactions by an inclusive date range" do
    result = Pix::AccountStatementQuery.call(
      account_id: @account1.id,
      start_date: "2026-09-20",
      end_date: "2026-09-21"
    )

    assert_equal [ @tx2.id, @tx1.id ], result[:transactions].map { |transaction| transaction[:id] }
  end

  test "filters an ISO 8601 range using UTC boundaries" do
    result = Pix::AccountStatementQuery.call(
      account_id: @account1.id,
      start_date: "2026-09-19T21:00:00-03:00",
      end_date: "2026-09-20T20:59:59-03:00"
    )

    assert_equal [ @tx1.id ], result[:transactions].map { |transaction| transaction[:id] }
  end

  test "rejects invalid dates" do
    error = assert_raises(ArgumentError) do
      Pix::AccountStatementQuery.call(account_id: @account1.id, start_date: "2026-99-99")
    end

    assert_equal "invalid date: 2026-99-99", error.message
  end

  test "rejects a date range whose start is after its end" do
    error = assert_raises(ArgumentError) do
      Pix::AccountStatementQuery.call(
        account_id: @account1.id,
        start_date: "2026-09-22",
        end_date: "2026-09-21"
      )
    end

    assert_equal "start_date must be before or equal to end_date", error.message
  end
end
