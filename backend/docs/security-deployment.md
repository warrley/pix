# Configuração de segurança

## Migração e provisionamento

1. Faça backup do banco e execute `bin/rails db:migrate`. Contas existentes sem senha não conseguem emitir tokens até a migração de credenciais. Defina senhas iniciais ou recuperadas com `security:set_user_password` a partir de um ambiente controlado.
2. Promova um usuário confiável com `security:bootstrap_operator`. Injete `OPERATOR_EMAIL` e `OPERATOR_INITIAL_PASSWORD` pelo secret manager do ambiente; não digite segredos em comandos ou histórico do shell.
3. Emita o token em `POST /api/v1/auth/token`. Tokens duram 15 minutos, são armazenados somente como SHA-256 e podem ser revogados em `DELETE /api/v1/auth/token`. Alterar a senha revoga os tokens ativos do usuário.
4. Não habilite cadastro público. A criação de usuários pela API exige token e papel de operador. Distribua credenciais por canal seguro e permita que o titular troque a senha autenticado.

## Fluxo de deploy deste repositório

- O deploy automatizado atual é `.github/workflows/cd.yml`: após o CI na branch `main` (ou execução manual), conecta à VPS por SSH e executa `docker compose up -d --build` em `~/pix`. Esse fluxo usa o `.env` local da VPS; variáveis presentes apenas nos Secrets do GitHub não chegam ao container automaticamente.
- Configure o `.env` na VPS com `RAILS_ENV=production`, `RAILS_MASTER_KEY`, `DATABASE_URL` (incluindo `sslmode=verify-full`), `PGSSLROOTCERT` quando necessário, `CORS_ALLOWED_ORIGINS` e, quando houver integração, `NOTIFICATIONS_WEBHOOK_URL`, `NOTIFICATIONS_WEBHOOK_ALLOWED_HOSTS` e `NOTIFICATIONS_WEBHOOK_SECRET`. O `docker-compose.yml` encaminha essas variáveis para o serviço Rails. Não grave os valores reais no Git nem os inclua em comandos ou logs.
- O compose atual executa `rails db:prepare` ao iniciar o backend. Faça e valide o backup antes do primeiro deploy com as migrações de segurança e confira os logs de inicialização antes de liberar tráfego.
- A configuração `config/deploy.yml` é uma alternativa Kamal, não é chamada pelo workflow acima. Ela agora exige `KAMAL_SERVER_IP` e `KAMAL_REGISTRY_SERVER`, em vez de aceitar os valores de exemplo que estavam no arquivo. Para Kamal, configure também as variáveis claras e segredos indicados nesse arquivo em seu ambiente/secret manager.
- Em produção, `database.yml` usa `sslmode=verify-full`. O host do banco deve corresponder ao certificado; quando a CA não estiver no trust store da imagem, disponibilize o arquivo de CA ao container e configure `PGSSLROOTCERT` para o caminho dentro do container. Não desative a verificação para contornar erros de certificado. O serviço `db` do Compose raiz não habilita TLS no PostgreSQL; não o use como banco de produção sem configurar TLS no servidor e um certificado válido para o nome `db`. Prefira apontar `DATABASE_URL` para uma instância PostgreSQL gerenciada com TLS configurado.
- Antes de fechar o acesso temporário de perímetro, execute o roteiro em `docs/security-manual-smoke-tests.md` e confirme que uma rota de conta sem token responde `401`.

## TLS e navegador

- O Kamal termina TLS com certificado automático para `pix.warley.xyz`; Rails assume o proxy confiável, força HTTPS, envia HSTS e mantém `/up` disponível para health checks.
- Configure `CORS_ALLOWED_ORIGINS` como lista separada por vírgula de origens completas, por exemplo `https://app.example.com`. Sem origens configuradas, chamadas do navegador são negadas. Não inclua `*` em produção.
- Em implantação Kamal, encaminhe `CORS_ALLOWED_ORIGINS`, `NOTIFICATIONS_WEBHOOK_URL` e `NOTIFICATIONS_WEBHOOK_ALLOWED_HOSTS` em `env.clear` conforme cada ambiente. O segredo de assinatura fica em `env.secret`.
- A conexão PostgreSQL de produção exige `sslmode=verify-full`. Configure `DB_HOST` como o nome DNS coberto pelo certificado do PostgreSQL e `PGSSLROOTCERT` com a CA confiável. Não use endereço IP se o certificado não contiver esse IP.
- Proteja também o armazenamento de banco, backups, logs e volumes com criptografia gerenciada pelo provedor e restrinja o acesso às chaves. Essa camada depende do provedor de infraestrutura e deve ser verificada na implantação.

## Webhooks

- Configure `NOTIFICATIONS_WEBHOOK_URL` com endpoint HTTPS e `NOTIFICATIONS_WEBHOOK_ALLOWED_HOSTS` com os nomes DNS exatos permitidos, separados por vírgula. O envio falha fechado para host não permitido, endereço IP não público ou ausência de segredo.
- Configure `NOTIFICATIONS_WEBHOOK_SECRET` em secret manager. Use um segredo aleatório forte, exclusivo por integração e faça rotação coordenada com o receptor.
- O receptor verifica `X-Webhook-Signature` como `v1=<hex HMAC-SHA256>`, calculado sobre `timestamp + "." + corpo HTTP bruto` com `NOTIFICATIONS_WEBHOOK_SECRET`. Rejeite timestamps fora de cinco minutos e deduplique `X-Webhook-Event-Id` para tornar retries idempotentes.
- O cliente de webhook não segue redirects, valida certificados TLS e fixa o IP público validado para reduzir risco de DNS rebinding. Atualize a allowlist ao trocar o endpoint.
- Entregas são repetidas pelo Active Job até três tentativas com espera exponencial. Após esgotar tentativas, Solid Queue mantém a execução em `solid_queue_failed_executions`; monitore essa tabela/fila e reexecute somente após corrigir a causa. O evento mantém o mesmo ID em cada tentativa.

## Dados pessoais e auditoria

- Respostas de usuário não incluem CPF/CNPJ; comprovantes exibem somente os quatro últimos dígitos. Extratos mantêm a máscara já usada pelo domínio.
- Notificações usam ID do destinatário e os campos financeiros do evento necessários à integração. Logs não incluem e-mail, telefone, documento, token, segredo ou E2E.
- Eventos de autenticação, negação de acesso, operação antifraude e transferência são registrados sem credenciais ou payload integral. Restrinja retenção e acesso aos logs de acordo com a política operacional.
