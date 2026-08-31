# 🗄️ Modelagem do Banco de Dados — Sistema PIX
> Baseado na Especificação Oficial do BACEN: github.com/bacen/pix-dict-api & github.com/bacen/pix-api

---

## 📐 Diagrama Lógico (DER)

```mermaid
erDiagram
    USERS {
        bigint id PK
        string name
        string doc_id "11 dígitos = CPF, 14 dígitos = CNPJ — ÚNICO"
        string email "ÚNICO case-insensitive"
        string phone "Formato E.164 (+5585999999999)"
    }

    ACCOUNTS {
        bigint id PK
        bigint user_id FK
        string account_number "ÚNICO"
        string agency_number "padrão: 0001"
        decimal balance "precisão 14.2 — nunca negativo"
        string status "active | blocked | closed"
    }

    PIX_KEYS {
        bigint id PK
        bigint account_id FK
        string key_type "cpf | cnpj | email | phone | random"
        string key_value "ÚNICO globalmente (apenas chaves ativas)"
        string status "active | suspended | cancelled"
    }

    TRANSACTIONS {
        bigint id PK
        string end_to_end_id "ÚNICO — previne cobrança dupla em retentativas"
        bigint source_account_id FK
        bigint destination_account_id FK
        string pix_key_used "snapshot da chave no momento da transferência"
        decimal amount "precisão 14.2 — sempre > 0"
        string description "opcional — máx 140 caracteres"
        string status "processing | completed | failed | cancelled"
        string failure_reason "preenchido se status = failed"
    }

    USERS ||--o{ ACCOUNTS : "possui"
    ACCOUNTS ||--o{ PIX_KEYS : "possui (máx 5 ativas por regra BACEN)"
    ACCOUNTS ||--o{ TRANSACTIONS : "envia de"
    ACCOUNTS ||--o{ TRANSACTIONS : "recebe em"
```

---

## ⚠️ Decisões Principais de Projeto

### Dinheiro: NUNCA use `float`. Use sempre `decimal(14, 2)`.
Ponto flutuante (float) causa erros de arredondamento binário em cálculos financeiros. `decimal` é exato.

### `tax_id_type` NÃO é necessário — é implícito pelo tamanho.
- 11 dígitos → CPF → validar usando o algoritmo de dígito verificador módulo-11
- 14 dígitos → CNPJ → validar usando o algoritmo de pesos específico do CNPJ
- Qualquer outro tamanho → inválido

### Unicidade da Chave PIX é GLOBAL e PARCIAL
- Uma chave só pode pertencer a UMA conta ativa em todo o sistema.
- Garantido via índice único parcial: `UNIQUE WHERE status = 'active'`
- Isso permite que uma chave cancelada seja recadastrada posteriormente.

### Máximo de 5 chaves PIX ativas por conta (regra BACEN)
Garantido na camada de aplicação/modelo antes da criação.

### Chave CPF deve pertencer ao titular da conta (regra BACEN)
Você não pode cadastrar o CPF de outra pessoa como sua chave PIX.

### Telefone deve ser armazenado no formato E.164 (regra BACEN)
Armazene como `+5585999999999`, nunca como `(85) 99999-9999`.

### `end_to_end_id` garante idempotência
Se um timeout de rede fizer o cliente retestar uma requisição de transferência, a segunda requisição encontra o `end_to_end_id` existente e NÃO cobra o remetente duas vezes.

### Contas bloqueadas PODEM receber, mas NÃO PODEM enviar (regra BACEN)
Contas encerradas não podem enviar nem receber.

### Auto-transferências são bloqueadas no nível do banco de dados
`CHECK (source_account_id <> destination_account_id)`

---

## 🔄 Ordem de Migração (Cadeia de Dependências)

```
1. create_users            (sem dependências)
2. create_accounts         (depende de: users)
3. create_pix_keys         (depende de: accounts)
4. create_transactions     (depende de: accounts x2)
```

Cada desenvolvedor deve realizar o merge de sua migração nesta ordem para evitar erros de chave estrangeira (foreign key)!
