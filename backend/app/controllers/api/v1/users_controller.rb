module Api
  module V1
    class UsersController < ApplicationController
      before_action :set_user, only: [ :show, :update, :destroy ]
      before_action :authorize_operator!, only: :create
      before_action -> { require_api_scope!("users:admin") }, only: :create
      before_action -> { require_api_scope!("profile:read") }, only: :show
      before_action -> { require_api_scope!("profile:write") }, only: [ :update, :destroy ]
      before_action -> { authorize_user!(@user) }, only: [ :show, :update, :destroy ]

      def show
        render_success(user_data(@user))
      end

      def create
        user = User.new(user_params)
        unless user.password.present?
          user.errors.add(:password, :blank)
          return render_error(user.errors.to_hash)
        end

        if user.save
          Security::AuditLogger.record(event: "user_created", actor: current_user, resource: user, request: request)
          render_success(user_data(user), status: :created)
        else
          render_error(user.errors.to_hash)
        end
      end

      def update
        if @user.update(user_params)
          Security::AuditLogger.record(event: "user_updated", actor: current_user, resource: @user, request: request)
          render_success(user_data(@user))
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
        permitted = params.require(:user).permit(:name, :email, :doc_id, :phone)
        permitted[:password] = params[:user][:password] if params[:user].key?(:password)
        permitted
      end

      def user_data(user)
        user.as_json(only: %i[id name email phone created_at updated_at])
      end
    end
  end
end
