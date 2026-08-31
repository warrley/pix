module Api
  module V1
    class UsersController < ApplicationController
      before_action :set_user, only: [ :show, :update, :destroy ]

      def show
        render_success(@user)
      end

      def create
        user = User.new(user_params)

        if user.save
          render_success(user, status: :created)
        else
          render_error(user.errors.to_hash)
        end
      end

      def update
        if @user.update(user_params)
          render_success(@user)
        else
          render_error(@user.errors.to_hash)
        end
      end

      def destroy
        if @user.destroy # because the accounts linked
          head :no_content
        else
          render_error(@user.errors.to_hash)
        end
      end

      private

      def set_user
        @user = User.find(params[:id])
      end

      def user_params
        params.require(:user).permit(:name, :email, :doc_id, :phone)
      end
    end
  end
end
