class ProcessScheduledTransfersJob < ApplicationJob
  queue_as :default

  BATCH_SIZE = 100
  PROCESSING_TIMEOUT = 15.minutes

  def perform
    today = Time.current.in_time_zone("America/Sao_Paulo").to_date
    due_transfers(today).find_each(batch_size: BATCH_SIZE) do |scheduled_transfer|
      begin
        process_scheduled_transfer(scheduled_transfer)
      rescue StandardError => error
        Rails.logger.error("scheduled_transfer_processing_failed id=#{scheduled_transfer.id} error=#{error.class.name}")
      end
    end
  end

  private

  def process_scheduled_transfer(scheduled_transfer)
    scheduled_transfer.with_lock do
      next unless scheduled_transfer.pending? || stale_processing?(scheduled_transfer)

      scheduled_transfer.update!(status: :processing, processing_started_at: Time.current)
      existing = Transaction.find_by(end_to_end_id: scheduled_transfer.execution_key)
      if existing&.completed?
        scheduled_transfer.update!(status: :executed, completed_transaction: existing, failure_reason: nil, processing_started_at: nil)
        next
      end

      # A failed transaction has not debited the account; a fresh key permits retry.
      scheduled_transfer.rotate_execution_key! if existing&.failed?
      result = Pix::TransferService.call(
        source_account_id: scheduled_transfer.account_id,
        pix_key: scheduled_transfer.pix_key_used,
        amount_cents: scheduled_transfer.amount_cents,
        description: scheduled_transfer.description,
        end_to_end_id: scheduled_transfer.execution_key,
        record_failures: false,
        expected_destination_account_id: scheduled_transfer.destination_account_id
      )

      if result.success? && result.transaction&.completed?
        scheduled_transfer.update!(status: :executed, completed_transaction: result.transaction, failure_reason: nil, processing_started_at: nil)
      elsif result.error&.code == "transfer.failed"
        scheduled_transfer.update!(status: :pending, failure_reason: failure_message(result), processing_started_at: nil)
      else
        scheduled_transfer.update!(status: :failed, failure_reason: failure_message(result), processing_started_at: nil)
      end
    end
  end

  def due_transfers(today)
    ScheduledTransfer.where(scheduled_for: ..today).where(
      "status = :pending OR (status = :processing AND processing_started_at < :timeout)",
      pending: "pending",
      processing: "processing",
      timeout: PROCESSING_TIMEOUT.ago
    ).order(:scheduled_for, :id)
  end

  def stale_processing?(record)
    record.processing? && record.processing_started_at && record.processing_started_at < PROCESSING_TIMEOUT.ago
  end

  def failure_message(result)
    (result.error&.message || result.transaction&.failure_reason || "Transfer could not be completed").truncate(255)
  end
end
