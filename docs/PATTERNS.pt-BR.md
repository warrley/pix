# 📐 Projeto PIX — Padrões de Código e Normas

> Todos os desenvolvedores da equipe devem seguir estes padrões.
> A consistência na base de código é um critério de avaliação.

---

## 1. Formato de Resposta da API

Todos os endpoints sempre retornam a **mesma estrutura de envelope**:

```json
{
  "data": "<object | array | null>",
  "error": "<string | object | null>"
}
```

> **Regra**: Quando `data` possui conteúdo → `error` é `null`. Quando algo dá errado → `data` é `null`.
> O cliente **sempre** sabe quais campos esperar.

### ✅ Sucesso — recurso único
```json
{
  "data": {
    "id": 1,
    "name": "João Silva",
    "tax_id": "12345678909",
    "email": "joao@example.com",
    "created_at": "2026-08-25T22:00:00.000Z"
  },
  "error": null
}
```

### ✅ Sucesso — coleção
```json
{
  "data": [
    { "id": 1, "account_number": "100001", "balance": "250.00", "status": "active" },
    { "id": 2, "account_number": "100002", "balance": "0.00",   "status": "active" }
  ],
  "error": null
}
```

### ✅ Sucesso — sem conteúdo (DELETE)
```json
{
  "data": null,
  "error": null
}
```

### ❌ Erro de Validação (422 Unprocessable Entity)
```json
{
  "data": null,
  "error": {
    "tax_id": ["is invalid", "has already been taken"],
    "email":  ["can't be blank"]
  }
}
```

### ❌ Erro de Regra de Negócio (400 Bad Request)
```json
{
  "data": null,
  "error": "Cannot close account with existing balance"
}
```

### ❌ Não Encontrado (404 Not Found)
```json
{
  "data": null,
  "error": "Record not found"
}
```

---

### Helper — adicionar ao `ApplicationController` (todos herdam este comportamento)
```ruby
# app/controllers/application_controller.rb
class ApplicationController < ActionController::API
  def render_success(data, status: :ok)
    render json: { data: data, error: nil }, status: status
  end

  def render_error(error, status: :unprocessable_entity)
    render json: { data: nil, error: error }, status: status
  end

  def render_not_found
    render json: { data: nil, error: "Record not found" }, status: :not_found
  end
end
```

### Usando helpers em todos os controllers
```ruby
def create
  @user = User.new(user_params)
  if @user.save
    render_success(@user, status: :created)
  else
    render_error(@user.errors)
  end
end

def show
  render_success(@user)
end

def destroy
  @user.destroy
  render_success(nil)
end

private

def set_user
  @user = User.find(params[:id])
rescue ActiveRecord::RecordNotFound
  render_not_found
end
```

---

## 2. Padrões de Models Rails

### Enums — Use sempre valores em string (NÃO inteiros)
```ruby
# ✅ Correto — legível para humanos no banco de dados
enum :status, {
  active:  "active",
  blocked: "blocked",
  closed:  "closed"
}, validate: true

# ❌ Errado — números mágicos no banco de dados (ilegível)
enum :status, { active: 0, blocked: 1, closed: 2 }
```

### Associações
```ruby
# Model pai
class User < ApplicationRecord
  has_many :accounts, dependent: :restrict_with_error
end

# Model filho
class Account < ApplicationRecord
  belongs_to :user

  has_many :pix_keys,           dependent: :restrict_with_error
  has_many :sent_transactions,  class_name: "Transaction", foreign_key: :source_account_id
  has_many :received_transactions, class_name: "Transaction", foreign_key: :destination_account_id
end
```

### Validações — Sempre com mensagens
```ruby
class User < ApplicationRecord
  validates :name,   presence: true
  validates :tax_id, presence: true, uniqueness: true
  validates :email,  presence: true,
                     uniqueness: { case_sensitive: false },
                     format: { with: URI::MailTo::EMAIL_REGEXP }

  validate :tax_id_must_be_mathematically_valid

  private

  def tax_id_must_be_mathematically_valid
    return if tax_id.blank?
    valid = case tax_id.length
            when 11 then Pix::KeyValidatorService.valid_cpf?(tax_id)
            when 14 then Pix::KeyValidatorService.valid_cnpj?(tax_id)
            else false
            end
    errors.add(:tax_id, "is invalid") unless valid
  end
end
```

### Callbacks — Apenas para auto-gerações simples
```ruby
class Account < ApplicationRecord
  before_create :generate_account_number

  private

  def generate_account_number
    loop do
      self.account_number = rand(100_000..999_999).to_s
      break unless Account.exists?(account_number: account_number)
    end
  end
end
```

---

## 3. Padrão Service Object

A lógica de negócios deve ficar em Service Objects, NUNCA em controllers ou models.

### Localização de arquivos
```
app/services/pix/key_validator_service.rb
app/services/pix/transfer_service.rb
```

### Estrutura
```ruby
# app/services/pix/transfer_service.rb
module Pix
  class TransferService
    Result = Struct.new(:success?, :transaction, :error, keyword_init: true)

    def initialize(source_account_id:, pix_key:, amount:, description: nil)
      @source_account_id = source_account_id
      @pix_key           = pix_key
      @amount            = amount
      @description       = description
    end

    def call
      validate_inputs!
      execute_transfer
    rescue TransferError => e
      Result.new(success?: false, error: e.message)
    end

    private

    def validate_inputs!
      raise TransferError, "Amount must be positive"      if @amount <= 0
      raise TransferError, "PIX key not found"            unless destination_account
      raise TransferError, "Source account is blocked"    if source_account.blocked?
      raise TransferError, "Destination account is closed" if destination_account.closed?
      raise TransferError, "Insufficient funds"           if source_account.balance < @amount
      raise TransferError, "Self-transfer not allowed"    if source_account.id == destination_account.id
    end

    def execute_transfer
      transaction_record = nil

      ActiveRecord::Base.transaction do
        source      = Account.lock.find(@source_account_id)
        destination = Account.lock.find(destination_account.id)

        source.update!(balance: source.balance - @amount)
        destination.update!(balance: destination.balance + @amount)

        transaction_record = Transaction.create!(
          end_to_end_id:          generate_end_to_end_id,
          source_account_id:      source.id,
          destination_account_id: destination.id,
          pix_key_used:           @pix_key,
          amount:                 @amount,
          description:            @description,
          status:                 "completed"
        )
      end

      Result.new(success?: true, transaction: transaction_record)
    end

    def source_account
      @source_account ||= Account.find(@source_account_id)
    end

    def destination_account
      @destination_account ||= PixKey.active.find_by(key_value: @pix_key)&.account
    end

    def generate_end_to_end_id
      "E12345678" + Time.current.strftime("%Y%m%d%H%M") + SecureRandom.alphanumeric(11).upcase
    end
  end

  class TransferError < StandardError; end
end
```

### Chamando um service a partir do controller
```ruby
# app/controllers/api/v1/transfers_controller.rb
def create
  result = Pix::TransferService.new(
    source_account_id: params[:source_account_id],
    pix_key:           params[:pix_key],
    amount:            params[:amount].to_d,
    description:       params[:description]
  ).call

  if result.success?
    render json: { data: result.transaction }, status: :created
  else
    render json: { error: result.error }, status: :unprocessable_entity
  end
end
```

---

## 4. Padrão de Controller

```ruby
# app/controllers/api/v1/users_controller.rb
module Api
  module V1
    class UsersController < ApplicationController
      before_action :set_user, only: [:show, :update, :destroy]

      def show
        render json: { data: @user }
      end

      def create
        @user = User.new(user_params)
        if @user.save
          render json: { data: @user }, status: :created
        else
          render json: { errors: @user.errors }, status: :unprocessable_entity
        end
      end

      def update
        if @user.update(user_params)
          render json: { data: @user }
        else
          render json: { errors: @user.errors }, status: :unprocessable_entity
        end
      end

      def destroy
        @user.destroy
        head :no_content
      end

      private

      def set_user
        @user = User.find(params[:id])
      rescue ActiveRecord::RecordNotFound
        render json: { error: "Record not found" }, status: :not_found
      end

      def user_params
        params.expect(user: [:name, :tax_id, :email, :phone])
      end
    end
  end
end
```

---

## 5. Padrões de Teste

### Teste de Model
```ruby
# test/models/user_test.rb
class UserTest < ActiveSupport::TestCase
  test "valid with all required fields" do
    user = User.new(name: "João", tax_id: "52998224725", email: "joao@test.com")
    assert user.valid?
  end

  test "invalid without name" do
    user = User.new(tax_id: "52998224725", email: "joao@test.com")
    assert_not user.valid?
    assert_includes user.errors[:name], "can't be blank"
  end

  test "invalid with duplicate tax_id" do
    users(:default).update!(tax_id: "52998224725")
    user = User.new(name: "Maria", tax_id: "52998224725", email: "maria@test.com")
    assert_not user.valid?
    assert_includes user.errors[:tax_id], "has already been taken"
  end

  test "invalid with wrong CPF check digits" do
    user = User.new(name: "João", tax_id: "12345678900", email: "joao@test.com")
    assert_not user.valid?
    assert_includes user.errors[:tax_id], "is invalid"
  end
end
```

### Teste de Requisição (Request Test)
```ruby
# test/controllers/api/v1/users_controller_test.rb
class Api::V1::UsersControllerTest < ActionDispatch::IntegrationTest
  test "POST /api/v1/users creates a user" do
    post api_v1_users_path, params: {
      user: { name: "João", tax_id: "52998224725", email: "joao@test.com" }
    }, as: :json

    assert_response :created
    assert_equal "João", response.parsed_body.dig("data", "name")
  end

  test "POST /api/v1/users returns 422 with invalid tax_id" do
    post api_v1_users_path, params: {
      user: { name: "João", tax_id: "00000000000", email: "joao@test.com" }
    }, as: :json

    assert_response :unprocessable_entity
    assert response.parsed_body.key?("errors")
  end

  test "GET /api/v1/users/:id returns 404 for unknown id" do
    get api_v1_user_path(id: 999_999), as: :json
    assert_response :not_found
    assert_equal "Record not found", response.parsed_body["error"]
  end
end
```

---

## 6. Convenções de Nomenclatura

| Elemento | Convenção | Exemplo |
| :--- | :--- | :--- |
| Models | Singular PascalCase | `User`, `PixKey`, `Transaction` |
| Controllers | Plural PascalCase | `UsersController`, `PixKeysController` |
| Services | PascalCase + sufixo Service | `TransferService`, `KeyValidatorService` |
| Tabelas do Banco | Plural snake_case | `users`, `pix_keys`, `transactions` |
| Colunas do Banco | snake_case | `tax_id`, `account_number`, `end_to_end_id` |
| Rotas | Plural kebab-case | `/api/v1/pix-keys` |
| Branches | `tipo/descricao-curta` | `feat/user-model`, `fix/transfer-rollback` |
| Commits | Conventional Commits | `feat(backend): add CPF validator service` |

---

## 7. Estrutura de Rotas

```ruby
# config/routes.rb
Rails.application.routes.draw do
  namespace :api do
    namespace :v1 do
      resources :users, only: [:create, :show, :update, :destroy]

      resources :accounts, only: [:create, :show, :update, :destroy] do
        resources :pix_keys, only: [:create, :index], shallow: true
      end

      resources :pix_keys, only: [:destroy]

      resources :transfers, only: [:create, :show]
    end
  end
end
```

Esta configuração gera as seguintes rotas:
```
POST   /api/v1/users
GET    /api/v1/users/:id
PUT    /api/v1/users/:id
DELETE /api/v1/users/:id

POST   /api/v1/accounts
GET    /api/v1/accounts/:id
DELETE /api/v1/accounts/:id

POST   /api/v1/accounts/:account_id/pix_keys
GET    /api/v1/accounts/:account_id/pix_keys
DELETE /api/v1/pix_keys/:id

POST   /api/v1/transfers
GET    /api/v1/transfers/:id
```
