# Teste manual de segurança e Swagger

Este roteiro verifica o deploy sem expor operações financeiras reais. Execute primeiro em desenvolvimento/staging. Em produção, use somente usuários e contas de teste autorizados.

## 1. Pré-requisitos

- Docker Compose iniciado e banco migrado, ou uma implantação atualizada em staging/produção.
- Uma conta de cliente e, para testes administrativos, uma conta de operador provisionadas pelos procedimentos de `security-deployment.md`.
- A URL base do ambiente. Exemplos: `http://localhost:3000` em desenvolvimento e `https://pix.warley.xyz` em produção.
- Não use credenciais reais em comandos salvos no histórico do shell. Para os testes autenticados, prefira o formulário Swagger aberto em uma sessão privada do navegador.

## 2. Disponibilidade, HTTPS e especificação

Defina a URL do ambiente no terminal:

```sh
API_URL=http://localhost:3000
# Em produção/staging, use a URL HTTPS autorizada para o ambiente.
```

Confirme que o serviço está ativo:

```sh
curl -i "$API_URL/up"
```

Esperado: HTTP `200`.

Confirme que a documentação e a especificação carregam:

```sh
curl -i "$API_URL/api-docs"
curl -fsS "$API_URL/api-docs/openapi.yaml" -o /tmp/pix-openapi.yaml
grep -nE 'auth/token|bearerAuth' /tmp/pix-openapi.yaml
```

Esperado: Swagger UI responde `200`; o YAML contém `/api/v1/auth/token` e `bearerAuth`. A rota alternativa também deve entregar a especificação:

```sh
curl -i "$API_URL/openapi.yaml"
```

Em produção, verifique redirecionamento HTTP para HTTPS e HSTS:

```sh
curl -sSI http://pix.warley.xyz
curl -sSI https://pix.warley.xyz
```

Esperado: HTTP redireciona para `https://pix.warley.xyz`; a resposta HTTPS contém `Strict-Transport-Security`. Confirme também que o navegador não apresenta erro de certificado.

## 3. Autenticação no Swagger

1. Abra `$API_URL/api-docs` no navegador.
2. Localize `POST /api/v1/auth/token` e faça login com a conta de teste. O formato esperado é `email` e `password` no corpo JSON.
3. Confirme resposta `201`, `token_type` igual a `Bearer`, data de expiração e cabeçalho `Cache-Control: no-store`.
4. Copie o token somente para o botão **Authorize** do Swagger. Não o salve em issue, captura de tela ou log.
5. Teste um endpoint permitido para o papel da conta. Para um cliente, use somente recursos associados ao próprio usuário.
6. No botão **Authorize**, encerre a sessão ao terminar e revogue o token usando `DELETE /api/v1/auth/token` autenticado, ou aguarde sua expiração.

## 4. Autorização e isolamento de dados

Use `curl` sem token para conferir o bloqueio básico:

```sh
curl -i "$API_URL/api/v1/accounts/1"
```

Esperado: HTTP `401`, sem dados de conta. Repita com token inválido; também deve retornar `401`.

Com usuários de teste distintos, use o Swagger para validar:

- Cliente autenticado acessa a própria conta e as operações liberadas ao papel.
- Cliente não acessa a conta ou o perfil de outra pessoa; a API retorna `404` para recurso fora do escopo de propriedade.
- Cliente não executa ação exclusiva de operador nem cria usuários; a API retorna `403`.
- Operador autenticado executa somente as ações administrativas documentadas.
- Credenciais incorretas retornam `401` sem revelar se o e-mail existe. Tentativas repetidas podem retornar `429` por limitação de taxa.

Não use IDs ou saldos de clientes reais para esses testes.

## 5. CORS

Configure `CORS_ALLOWED_ORIGINS` no ambiente com a origem exata do frontend autorizado e reinicie o serviço. Faça preflight de uma origem autorizada:

```sh
curl -i -X OPTIONS "$API_URL/api/v1/accounts/1" \
  -H 'Origin: https://app.example.com' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: authorization,content-type'
```

Esperado: a resposta permite exatamente a origem configurada. Repita com uma origem não autorizada; não deve haver `Access-Control-Allow-Origin` permitindo-a. Substitua `https://app.example.com` pela origem real de staging/frontend.

## 6. Webhook assinado

Faça esse teste apenas com um receptor de teste controlado pela equipe:

1. Configure URL HTTPS, allowlist DNS e segredo exclusivo de teste no ambiente.
2. Gere um evento de teste pelo fluxo normal da aplicação.
3. No receptor, confirme `X-Webhook-Signature` no formato `v1=<hex>`, timestamp e `X-Webhook-Event-Id`.
4. Valide HMAC-SHA256 do timestamp concatenado com ponto e o corpo HTTP bruto; confirme rejeição de assinatura inválida e timestamp fora da janela de cinco minutos.
5. Force uma resposta temporária de erro e confirme retry com o mesmo ID de evento; deduplique o ID no receptor.
6. Configure temporariamente host fora da allowlist em staging e confirme que o envio falha fechado. Não aponte testes para endpoints arbitrários ou produção de terceiros.

## 7. Inspeção de logs e critérios de aceite

Verifique logs do backend e do worker após os testes. Não devem conter senha, token bearer, segredo HMAC, CPF/CNPJ completo, e-mail ou payload integral do webhook.

O teste está aprovado quando:

- `/up`, Swagger UI e o contrato OpenAPI atual respondem com sucesso;
- produção redireciona para HTTPS, tem certificado válido e envia HSTS;
- chamadas sem token recebem `401`, e as permissões por papel/propriedade retornam os status esperados;
- CORS libera apenas origens configuradas;
- o receptor valida assinatura e timestamp, deduplica retries, e o envio a host não autorizado falha fechado;
- os logs não expõem credenciais nem dados pessoais completos.
