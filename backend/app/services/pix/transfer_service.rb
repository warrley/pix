module Pix
  class TransferService
    DEFAULT_ISPB = "01011010"

    Result = Struct.new(:success?, :transaction, :error, keyword_init: true)

    def self.call(...)
      new(...).call
    end

    def initialize(source_account_id:, pix_key:, amount_cents:, description: nil, end_to_end_id: nil)
      @source_account_id = source_account_id
      @pix_key_raw = pix_key.to_s.strip
      @amount_cents = amount_cents
      @description = description
      @end_to_end_id = end_to_end_id.presence || generate_end_to_end_id
    end

    def call
      validation_error = validate_pre_transfer
      if validation_error
        failed_tx = record_failed_transaction(validation_error.message)
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
            raise DomainFailure, DomainError.new(code: "account.fraud_blocked", message: "Source account is blocked for suspected fraud", http_status: :unprocessable_entity)
          end

          if locked_source.status != "active"
            raise DomainFailure, DomainError.new(code: "account.not_active", message: "Source account is not active", http_status: :unprocessable_entity)
          end

          if locked_destination.fraud_blocked?
            raise DomainFailure, DomainError.new(code: "account.fraud_blocked", message: "Destination account is blocked for suspected fraud", http_status: :unprocessable_entity)
          end

          if locked_destination.status == "closed"
            raise DomainFailure, DomainError.new(code: "account.closed", message: "Destination account is closed", http_status: :unprocessable_entity)
          end

          if locked_source.balance_cents < @amount_cents
            raise DomainFailure, DomainError.new(code: "transfer.insufficient_funds", message: "Insufficient funds", http_status: :unprocessable_entity)
          end

          limit_error = TransferLimitPolicy.new(
            source_account: locked_source,
            destination_account: locked_destination,
            amount_cents: @amount_cents
          ).error
          raise DomainFailure, limit_error if limit_error

          transaction_record = Transaction.create!(
            end_to_end_id: @end_to_end_id,
            source_account: locked_source,
            destination_account: locked_destination,
            pix_key_used: @pix_key_raw,
            amount_cents: @amount_cents,
            description: @description,
            status: :processing
          )

          record_event(transaction_record, current_status: "created", reason: "Transfer created")

          locked_source.update!(balance_cents: locked_source.balance_cents - @amount_cents)
          locked_destination.update!(balance_cents: locked_destination.balance_cents + @amount_cents)

          transaction_record.update!(status: :completed)
          record_event(transaction_record, previous_status: "created", current_status: "completed", reason: "Balance transfer completed")
        end
      rescue DomainFailure => e
        failed_tx = record_failed_transaction(e.message)
        enqueue_notification(failed_tx, "failed")
        return Result.new(success?: false, transaction: failed_tx, error: e.error)
      rescue StandardError => e
        failed_tx = record_failed_transaction("Transfer could not be completed")
        enqueue_notification(failed_tx, "failed")
        return Result.new(success?: false, transaction: failed_tx, error: DomainError.new(code: "transfer.failed", message: "Transfer could not be completed", http_status: :unprocessable_entity))
      end

      enqueue_notification(transaction_record, "completed")
      Result.new(success?: true, transaction: transaction_record, error: nil)
    end

    private

    def validate_pre_transfer
      return DomainError.new(code: "amount.invalid", message: "Amount must be a positive integer number of cents", http_status: :unprocessable_entity) unless @amount_cents.is_a?(Integer) && @amount_cents.positive?
      return DomainError.new(code: "amount.limit_exceeded", message: "Amount exceeds the system transfer limit", http_status: :unprocessable_entity) if @amount_cents > Money::MAX_TRANSFER_CENTS

      @source_account = Account.find_by(id: @source_account_id)
      return DomainError.new(code: "account.not_found", message: "Source account not found", http_status: :not_found) unless @source_account
      return DomainError.new(code: "account.fraud_blocked", message: "Source account is blocked for suspected fraud", http_status: :unprocessable_entity) if @source_account.fraud_blocked?
      return DomainError.new(code: "account.blocked", message: "Source account is blocked", http_status: :unprocessable_entity) if @source_account.blocked?
      return DomainError.new(code: "account.closed", message: "Source account is closed", http_status: :unprocessable_entity) if @source_account.closed?

      @pix_key = PixKey.find_by(key_value: normalize_key_value(@pix_key_raw))
      return DomainError.new(code: "pix_key.not_found", message: "PIX key not found", http_status: :not_found) unless @pix_key
      return DomainError.new(code: "pix_key.not_active", message: "PIX key is not active", http_status: :unprocessable_entity) unless @pix_key.active?

      @destination_account = @pix_key.account
      return DomainError.new(code: "account.not_found", message: "Destination account not found", http_status: :not_found) unless @destination_account
      return DomainError.new(code: "account.fraud_blocked", message: "Destination account is blocked for suspected fraud", http_status: :unprocessable_entity) if @destination_account.fraud_blocked?
      return DomainError.new(code: "account.closed", message: "Destination account is closed", http_status: :unprocessable_entity) if @destination_account.closed?

      if @source_account.id == @destination_account.id
        return DomainError.new(code: "transfer.self_transfer", message: "Self-transfers are not allowed", http_status: :unprocessable_entity)
      end

      if @source_account.balance_cents < @amount_cents
        return DomainError.new(code: "transfer.insufficient_funds", message: "Insufficient funds", http_status: :unprocessable_entity)
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
        amount_cents: @amount_cents.positive? ? @amount_cents : 1,
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
        amount_cents: transaction_record.amount_cents,
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
