Rails.application.routes.draw do
  # Define your application routes per the DSL in https://guides.rubyonrails.org/routing.html

  # Reveal health status on /up that returns 200 if the app boots with no exceptions, otherwise 500.
  # Can be used by load balancers and uptime monitors to verify that the app is live.
  get "up" => "rails/health#show", as: :rails_health_check

  namespace :api do
    namespace :v1 do
      resources :users, only: [ :create, :show, :update, :destroy ] do
        resources :accounts, only: [ :index ]
      end
      get "users/:user_id/reports/transactions",
        to: "user_reports#transactions",
        as: :user_transactions_report,
        defaults: { format: :json }
      resources :accounts, only: [ :create, :show, :destroy ] do
        member do
          patch :block_fraud, to: "account_security#block_fraud"
          patch :unblock_fraud, to: "account_security#unblock_fraud"
        end
        resources :pix_keys, only: [ :create, :index ]
        resources :transfers, only: [ :index ], controller: "account_transfers"
        get "reports/statement", to: "account_reports#statement"
      end
      resources :pix_keys, only: [ :destroy ]
      resources :transfers, only: [ :create, :show ] do
        member do
          post :cancel
        end
      end
    end
  end

  # Defines the root path route ("/")
  # root "posts#index"
end
