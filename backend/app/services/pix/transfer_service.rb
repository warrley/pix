module Pix
  class TransferService
    DEFAULT_ISPB = "01011010"

    Result = Struct.new(:success?, :transaction, :error, keyword_init: true)

    def self.call(...)
      new(...).call
    end

    def initialize(source_account_id:, pix_key:, amount:, description: nil, end_to_end_id: nil)
      @source_account_id = source_account_id
      @pix_key_raw = pix_key.to_s.strip
      @amount = BigDecimal(amount.to_s)
      @description = description
      @end_to_end_id = end_to_end_id.presence || generate_end_to_end_id
    end

    def call
      validation_error = validate_pre_transfer
      if validation_error
        failed_tx = record_failed_transaction(validation_error)
        enqueue_notification(failed_tx, "failed")
        return Result.new(success?: false, transaction: failed_tx, error: validation_error)
      end

      transaction_record = nil

      begin
        ActiveRecord::Base.transaction do
          first_id, second_id = [ @source_account_id, @destination_account.id ].sort
          locked_first = Account.lock.find(first_id)
          locked_second = Account.lock.find(second_id)

          locked_source = (locked_first.id == @source_account_id) ? locked_first : locked_second
          locked_destination = (locked_first.id == @destination_account.id) ? locked_first : locked_second

          if locked_source.fraud_blocked?
            raise StandardError, "Source account is blocked for suspected fraud"
          end

          if locked_source.status != "active"
            raise StandardError, "Source account is not active"
          end

          if locked_destination.fraud_blocked?
            raise StandardError, "Destination account is blocked for suspected fraud"
          end

          if locked_destination.status == "closed"
            raise StandardError, "Destination account is closed"
          end

          if locked_source.balance < @amount
            raise StandardError, "Insufficient funds"
          end

          transaction_record = Transaction.create!(
            end_to_end_id: @end_to_end_id,
            source_account: locked_source,
            destination_account: locked_destination,
            pix_key_used: @pix_key_raw,
            amount: @amount,
            description: @description,
            status: :processing
          )

          record_event(transaction_record, current_status: "created", reason: "Transfer created")

          locked_source.update!(balance: locked_source.balance - @amount)
          locked_destination.update!(balance: locked_destination.balance + @amount)

          transaction_record.update!(status: :completed)
          record_event(transaction_record, previous_status: "created", current_status: "completed", reason: "Balance transfer completed")
        end
      rescue StandardError => e
        failed_tx = record_failed_transaction(e.message)
        enqueue_notification(failed_tx, "failed")
        return Result.new(success?: false, transaction: failed_tx, error: e.message)
      end

      enqueue_notification(transaction_record, "completed")
      Result.new(success?: true, transaction: transaction_record, error: nil)
    end

    private

    def validate_pre_transfer
      return "amount must be greater than zero" if @amount <= 0

      @source_account = Account.find_by(id: @source_account_id)
      return "source account not found" unless @source_account
      return "Source account is blocked for suspected fraud" if @source_account.fraud_blocked?
      return "source account is blocked" if @source_account.blocked?
      return "source account is closed" if @source_account.closed?

      @pix_key = PixKey.find_by(key_value: normalize_key_value(@pix_key_raw))
      return "PIX key not found" unless @pix_key
      return "PIX key is not active" unless @pix_key.active?

      @destination_account = @pix_key.account
      return "destination account not found" unless @destination_account
      return "Destination account is blocked for suspected fraud" if @destination_account.fraud_blocked?
      return "destination account is closed" if @destination_account.closed?

      if @source_account.id == @destination_account.id
        return "self-transfers are not allowed"
      end

      if @source_account.balance < @amount
        return "insufficient funds"
      end

      nil
    end

    def record_failed_transaction(reason)
      return nil unless @source_account && @destination_account && @source_account.id != @destination_account.id

      Transaction.create(
        end_to_end_id: @end_to_end_id,
        source_account: @source_account,
        destination_account: @destination_account,
        pix_key_used: @pix_key_raw,
        amount: @amount > 0 ? @amount : 0.01,
        description: @description,
        status: :failed,
        failure_reason: reason
      )
    rescue StandardError
      nil
    end

    def record_event(transaction_record, previous_status: nil, current_status:, reason:)
      TransactionEvent.create!(
        transaction_record: transaction_record,
        previous_status: previous_status,
        current_status: current_status,
        amount: transaction_record.amount,
        source_account: transaction_record.source_account,
        destination_account: transaction_record.destination_account,
        reason: reason,
        created_at: Time.current
      )
    end

    def normalize_key_value(value)
      digits = value.gsub(/\D/, "")
      return digits if [ 11, 14 ].include?(digits.length) && !value.include?("@")

      value.downcase
    end

    def generate_end_to_end_id
      timestamp = Time.current.strftime("%Y%m%d%H%M")
      random_chars = SecureRandom.alphanumeric(11).downcase
      "E#{DEFAULT_ISPB}#{timestamp}#{random_chars}"
    end

    def enqueue_notification(transaction, event_type)
      return unless transaction

      Notifications::Enqueuer.enqueue(transaction.id, event_type)
    end
  end
end
