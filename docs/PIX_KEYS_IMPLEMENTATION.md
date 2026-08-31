# 📌 Registro de Progresso — Issue #9: PIX Keys Engine
> Responsável: `tiago-ufc`  
> Branches: `feat/pix-key-model` (Etapas 1 e 2) & `feat/pix-keys-api` (Etapa 3)

---

## 🟢 Etapa 1: Migration e Banco de Dados (CONCLUÍDO)

### 📄 Arquivo de Migration
- **Caminho**: `backend/db/migrate/20260831000000_create_pix_keys.rb`
- **Data de Execução**: 31/08/2026

### 🛠️ Especificação da Tabela `pix_keys`

| Coluna | Tipo | Restrições | Descrição |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | `PRIMARY KEY`, Auto-incremento | Identificador único da chave PIX |
| `account_id` | `bigint` | `NOT NULL`, `FK -> accounts` | Conta proprietária da chave |
| `key_type` | `string(10)` | `NOT NULL`, Check Constraint | Tipo da chave (`cpf`, `cnpj`, `email`, `phone`, `random`) |
| `key_value` | `string(77)` | `NOT NULL` | Valor da chave PIX |
| `status` | `string(20)` | `NOT NULL`, Default `"active"`, Check Constraint | Estado (`active`, `suspended`, `cancelled`) |
| `created_at` | `timestamp` | `NOT NULL` | Data/hora de criação |
| `updated_at` | `timestamp` | `NOT NULL` | Data/hora de atualização |

### 🔒 Índices e Regras de Integridade do Banco (PostgreSQL)
1. **Índice Único Parcial (Regra BACEN)**:
   ```sql
   CREATE UNIQUE INDEX index_pix_keys_on_key_value_active ON pix_keys (key_value) WHERE status = 'active';
   ```
   *Garante que uma mesma chave não pode estar ativa simultaneamente em duas contas no sistema inteiro, mas permite o recadastramento futuro se a chave anterior for cancelada.*

2. **Check Constraints**:
   - `CHECK (key_type IN ('cpf', 'cnpj', 'email', 'phone', 'random'))`
   - `CHECK (status IN ('active', 'suspended', 'cancelled'))`

---

## 🟢 Etapa 2: Model & Validator Service (CONCLUÍDO)

### 📄 Componentes Criados
- `app/services/pix/key_validator_service.rb`:
  - **CPF**: Validação Módulo-11 de dígitos verificadores e rejeição de dígitos idênticos.
  - **CNPJ**: Validação de pesos e dígitos verificadores de CNPJ.
  - **Email**: Validação no padrão RFC 5322 e limite de 77 caracteres.
  - **Telefone**: Validação de padrão E.164 (`+55` + DDD 2 dígitos + 8 ou 9 dígitos).
  - **Aleatória (EVP)**: Gerador automático de UUID v4 (`SecureRandom.uuid`).
- `app/models/pix_key.rb`:
  - Enums para `status` e `key_type` com validação.
  - Associações: `belongs_to :account` e `has_one :user, through: :account`.
  - Normalização automática (remoção de caracteres não numéricos para CPF/CNPJ, `downcase` em Email).
  - **Regra BACEN**: Validação de limite máximo de 5 chaves ativas por conta.
  - **Regra BACEN**: Validação de titularidade exigindo que chave do tipo CPF seja idêntica ao `doc_id` do titular da conta.
- `app/models/account.rb`:
  - Adicionada associação `has_many :pix_keys, dependent: :restrict_with_error`.

### 🧪 Testes Unitários Realizados
- `test/services/pix/key_validator_service_test.rb`: Testes para CPF, CNPJ, Email, Telefone E.164 e UUID.
- `test/models/pix_key_test.rb`: Testes cobrindo validações de modelo, limite de 5 chaves e conferência de titularidade do CPF.
- **Resultado**: 51 testes executados, 136 asserções com 0 falhas e 0 erros.

---

## 🟢 Etapa 3: Controllers & API Endpoints (CONCLUÍDO)

### 📄 Componentes Criados
- `config/routes.rb`:
  - `POST /api/v1/accounts/:account_id/pix_keys` -> Cadastro de nova chave PIX.
  - `GET /api/v1/accounts/:account_id/pix_keys` -> Listagem de chaves ativas da conta.
  - `DELETE /api/v1/pix_keys/:id` -> Cancelamento de chave PIX (soft delete).
- `app/controllers/api/v1/pix_keys_controller.rb`:
  - Utilização dos envelopes de resposta `render_success` e `render_error`.
  - Tratamento de exceção `ActiveRecord::RecordNotUnique` para chaves duplicadas (retornando HTTP 422).
  - Soft-delete executando `@pix_key.cancelled!` na ação `destroy`.

### 🧪 Testes de Integração Realizados
- `test/integration/api/v1/pix_keys_test.rb`:
  - Testes de criação (201 Created), erros de validação/duplicidade (422 Unprocessable Entity), conta não encontrada (404 Not Found), listagem de ativas (200 OK) e cancelamento (200 OK).
- **Resultado Final**: 59 testes executados, 155 asserções com 0 falhas e 0 erros.
