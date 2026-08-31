# 📌 Registro de Progresso — Issue #9: PIX Keys Engine
> Responsável: `tiago-ufc`  
> Branch: `feat/pix-key-model`

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

## 🟡 Etapa 2: Model & Validator Service (PENDENTE)
- [ ] Criar `app/services/pix/key_validator_service.rb` (Validações de CPF/CNPJ módulo-11, E.164, etc.)
- [ ] Criar `app/models/pix_key.rb` (Relacionamentos e validações de modelo, limite de 5 chaves)
- [ ] Testes unitários de model e validator (`test/models/pix_key_test.rb`, `test/services/pix/key_validator_service_test.rb`)
---

## 🟡 Etapa 3: Controllers & API Endpoints (PENDENTE)
- [ ] Criar `app/controllers/api/v1/pix_keys_controller.rb`
- [ ] Rotas `POST`, `GET`, `DELETE` (soft delete)
- [ ] Testes de integração de API (`test/integration/api/v1/pix_keys_test.rb`)
