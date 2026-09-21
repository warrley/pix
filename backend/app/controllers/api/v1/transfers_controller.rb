module Api
  module V1
    class TransfersController < ApplicationController
      def create
        # Mantém a lógica existente se houver, ou cria a base padrão
      end

      def show
        @transaction = Transaction.find(params[:id])
        render json: @transaction, status: :ok
      end

      def cancel
        result = Pix::CancelTransferService.call(transaction_id: params[:id])

        if result.success?
          render json: result.transaction, status: :ok
        elsif result.error == "Transaction not found"
          render json: { error: result.error }, status: :not_found
        else
          render json: { error: result.error }, status: :unprocessable_entity
        end
      end
    end
  end
end
