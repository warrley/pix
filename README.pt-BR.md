# Projeto PIX

Este é o nosso projeto universitário para a disciplina de Gestão de Configuração (GC). Estamos construindo um sistema PIX simples com contas e transferências.

## Estrutura do Projeto
- `/backend`: API em Ruby on Rails

## Como executar

### 1. Variáveis de Ambiente
Antes de executar o projeto, você deve criar um arquivo `.env` no diretório raiz (este arquivo é ignorado pelo Git por razões de segurança). Peça as variáveis de desenvolvimento a um membro da equipe ou copie os padrões a seguir:

```env
SECRET_KEY_BASE=local_development_secret_key
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

### 2. Iniciar o Projeto
Certifique-se de ter o Docker instalado. Usamos o PostgreSQL 15, que será baixado e iniciado automaticamente pelo Docker. Para iniciar o banco de dados e o backend, execute:

```bash
docker compose up --build
```

### 3. Corrigindo Erros Comuns de Permissão (Usuários Linux)
Se você estiver no Linux e receber um erro de `Permission denied` relativo às pastas `backend/tmp` ou `backend/log` travando o contêiner, é porque o Docker cria essas pastas com o usuário `root`. Corrija executando:

```bash
sudo chmod -R 777 backend/tmp backend/log
```

Se receber um erro de permissão no arquivo `backend/config/master.key`, execute:

```bash
sudo chmod 644 backend/config/master.key
```

### 4. Verifique se está funcionando!
Abra seu navegador e acesse o endpoint de verificação de saúde (health check):
👉 [http://localhost:3000/up](http://localhost:3000/up)

Se você vir uma tela verde com a logo do Rails, sua configuração local está perfeita!

### 5. Alimentando o Banco de Dados (Seeds de Teste)
Ao rodar o `docker compose up --build` pela primeira vez, o comando interno `rails db:prepare` executa as migrações e popula o banco automaticamente. Se você já tinha o banco criado anteriormente e precisa carregar os dados de teste para o Postman, execute:

```bash
docker compose exec backend bundle exec rails db:seed
```

#### Usuários Pré-configurados:
| Usuário | E-mail | Senha | Papel (Role) | Chave Pix | Saldo Inicial |
|---|---|---|---|---|---|
| **Alice Silva** | `alice.test@example.com` | `password123` | `operator` (Acesso total) | `alice.test@example.com` | R$ 1.500,00 |
| **Bob Santos** | `bob.test@example.com` | `password123` | `customer` | `bob.test@example.com` | R$ 800,00 |

### 6. Observabilidade (Prometheus + Grafana)
Defina `METRICS_BEARER_TOKEN`, `GRAFANA_ADMIN_USER` e `GRAFANA_ADMIN_PASSWORD` no seu arquivo `.env` local (ignorado pelo Git) antes de subir os containers com `docker compose up`:
- **Endpoint de Métricas da API (`/metrics`)**: `http://localhost:3000/metrics` (protegido via header `Authorization: Bearer <METRICS_BEARER_TOKEN>`).
- **Prometheus**: [http://127.0.0.1:9090](http://127.0.0.1:9090) (publicado apenas em loopback por padrão).
- **Grafana**: [http://127.0.0.1:3001](http://127.0.0.1:3001) (autenticado com `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` definidos no `.env` — já vem com o datasource Prometheus e o dashboard **PIX — Observabilidade Completa** pré-provisionados em `ops/grafana/`).
- **PostgreSQL Exporter**: `http://127.0.0.1:9187/metrics`
O seed também cria exemplos de movimentações concluídas, canceladas, estornadas e com falha nos últimos sete dias.

### Painel administrativo

Com o backend em execução, acesse:

- Painel: [http://localhost:3000/admin](http://localhost:3000/admin)
- Operador: `alice.test@example.com`
- Senha: `password123`

O painel usa os endpoints protegidos `/api/v1/admin/*` e exige uma conta `operator` com os scopes `users:admin`, `accounts:admin` e `reports:read`.

---

## Testes de API com o Postman

Disponibilizamos uma suíte completa de testes automatizados e manuais para validação de 100% dos fluxos da API:

- **Arquivo:** [`postman_pix_master_suite.json`](postman_pix_master_suite.json)
- **Cobertura:** 25 requisições cobrindo autenticação JWT, cadastro/extrato/bloqueio de contas, chaves Pix, transferências com idempotência e estornos (Refund).

### Como executar no VS Code / Postman:
1. Abra a extensão do **Postman** no VS Code ou o aplicativo desktop do Postman.
2. Clique em **Import** e selecione o arquivo `postman_pix_master_suite.json` na raiz do projeto.
3. Certifique-se de que a variável de ambiente `baseUrl` esteja apontando para `http://localhost:3000` (já vem como padrão).
4. Execute as requisições individualmente ou rode a coleção inteira via **Collection Runner**. As variáveis e tokens são propagados automaticamente entre as requisições.

---

## Contribuindo
Consulte o arquivo `CONTRIBUTING.pt-BR.md` (ou `CONTRIBUTING.md`) para conhecer as regras da nossa equipe antes de enviar código.
