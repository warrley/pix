module Pix
  class KeyLifecycleService
    class Error < StandardError; end

    Result = Struct.new(:success?, :pix_key, :error, keyword_init: true)

    def self.unblock(pix_key)
      new(pix_key).unblock
    end

    def self.unblock!(pix_key)
      result = unblock(pix_key)
      raise Error, result.error unless result.success?

      result.pix_key
    end

    def self.reassociate(pix_key, new_account)
      new(pix_key).reassociate(new_account)
    end

    def self.reassociate!(pix_key, new_account)
      result = reassociate(pix_key, new_account)
      raise Error, result.error unless result.success?

      result.pix_key
    end

    def initialize(pix_key)
      @pix_key = pix_key.is_a?(PixKey) ? pix_key : PixKey.find_by(id: pix_key)
    end

    def unblock
      return Result.new(success?: false, pix_key: nil, error: "Record not found") unless @pix_key

      unless @pix_key.suspended?
        return Result.new(success?: false, pix_key: @pix_key, error: "Only suspended keys can be unblocked")
      end

      account = @pix_key.account
      unless account
        return Result.new(success?: false, pix_key: @pix_key, error: "Account not found")
      end

      if account.blocked? || (account.respond_to?(:fraud_blocked?) && account.fraud_blocked?)
        return Result.new(success?: false, pix_key: @pix_key, error: "Account is fraud-blocked")
      end

      unless account.active?
        return Result.new(success?: false, pix_key: @pix_key, error: "Account must be active")
      end

      if account.pix_keys.where(status: "active").where.not(id: @pix_key.id).count >= 5
        return Result.new(success?: false, pix_key: @pix_key, error: "maximum of 5 active PIX keys per account reached")
      end

      if PixKey.where(status: "active", key_value: @pix_key.key_value).where.not(id: @pix_key.id).exists?
        return Result.new(success?: false, pix_key: @pix_key, error: "Key is already registered")
      end

      ActiveRecord::Base.transaction do
        @pix_key.lock!
        account.lock!

        if account.blocked? || (account.respond_to?(:fraud_blocked?) && account.fraud_blocked?)
          return Result.new(success?: false, pix_key: @pix_key, error: "Account is fraud-blocked")
        end

        unless account.active?
          return Result.new(success?: false, pix_key: @pix_key, error: "Account must be active")
        end

        if account.pix_keys.where(status: "active").where.not(id: @pix_key.id).count >= 5
          return Result.new(success?: false, pix_key: @pix_key, error: "maximum of 5 active PIX keys per account reached")
        end

        if PixKey.where(status: "active", key_value: @pix_key.key_value).where.not(id: @pix_key.id).exists?
          return Result.new(success?: false, pix_key: @pix_key, error: "Key is already registered")
        end

        @pix_key.update!(status: :active)
      end

      Result.new(success?: true, pix_key: @pix_key, error: nil)
    rescue ActiveRecord::RecordNotUnique
      Result.new(success?: false, pix_key: @pix_key, error: "Key is already registered")
    rescue ActiveRecord::RecordInvalid => e
      Result.new(success?: false, pix_key: @pix_key, error: e.record.errors.full_messages.to_sentence)
    rescue StandardError => e
      Result.new(success?: false, pix_key: @pix_key, error: e.message)
    end

    def reassociate(new_account)
      return Result.new(success?: false, pix_key: nil, error: "Record not found") unless @pix_key

      unless @pix_key.active?
        return Result.new(success?: false, pix_key: @pix_key, error: "Only active keys can be moved")
      end

      current_account = @pix_key.account
      unless current_account
        return Result.new(success?: false, pix_key: @pix_key, error: "Account not found")
      end

      target_account = new_account.is_a?(Account) ? new_account : Account.find_by(id: new_account)
      unless target_account
        return Result.new(success?: false, pix_key: @pix_key, error: "Account not found")
      end

      if current_account.user_id != target_account.user_id
        return Result.new(success?: false, pix_key: @pix_key, error: "New account must belong to the same user")
      end

      if target_account.blocked? || (target_account.respond_to?(:fraud_blocked?) && target_account.fraud_blocked?)
        return Result.new(success?: false, pix_key: @pix_key, error: "Account is fraud-blocked")
      end

      unless target_account.active?
        return Result.new(success?: false, pix_key: @pix_key, error: "New account must be active")
      end

      if target_account.pix_keys.where(status: "active").where.not(id: @pix_key.id).count >= 5
        return Result.new(success?: false, pix_key: @pix_key, error: "maximum of 5 active PIX keys per account reached")
      end

      ActiveRecord::Base.transaction do
        @pix_key.lock!

        first_id, second_id = [current_account.id, target_account.id].sort
        locked_first = Account.lock.find(first_id)
        locked_second = (first_id == second_id) ? locked_first : Account.lock.find(second_id)
        locked_current = (locked_first.id == current_account.id) ? locked_first : locked_second
        locked_target = (locked_first.id == target_account.id) ? locked_first : locked_second

        unless @pix_key.active?
          return Result.new(success?: false, pix_key: @pix_key, error: "Only active keys can be moved")
        end

        if locked_current.user_id != locked_target.user_id
          return Result.new(success?: false, pix_key: @pix_key, error: "New account must belong to the same user")
        end

        if locked_target.blocked? || (locked_target.respond_to?(:fraud_blocked?) && locked_target.fraud_blocked?)
          return Result.new(success?: false, pix_key: @pix_key, error: "Account is fraud-blocked")
        end

        unless locked_target.active?
          return Result.new(success?: false, pix_key: @pix_key, error: "New account must be active")
        end

        if locked_target.pix_keys.where(status: "active").where.not(id: @pix_key.id).count >= 5
          return Result.new(success?: false, pix_key: @pix_key, error: "maximum of 5 active PIX keys per account reached")
        end

        @pix_key.update!(account: locked_target)
      end

      Result.new(success?: true, pix_key: @pix_key, error: nil)
    rescue ActiveRecord::RecordNotUnique
      Result.new(success?: false, pix_key: @pix_key, error: "Key is already registered")
    rescue ActiveRecord::RecordInvalid => e
      Result.new(success?: false, pix_key: @pix_key, error: e.record.errors.full_messages.to_sentence)
    rescue StandardError => e
      Result.new(success?: false, pix_key: @pix_key, error: e.message)
    end
  end
end
