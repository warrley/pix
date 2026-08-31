# Projeto PIX

Este é o nosso projeto universitário para a disciplina de Gestão de Configuração (GC). Estamos construindo um sistema PIX simples com contas e transferências.

## Estrutura do Projeto
- `/backend`: API em Ruby on Rails
- `/frontend`: Aplicação Web em Next.js

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
docker compose up
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
👉 [http://localhost/up](http://localhost/up)

Se você vir uma tela verde com a logo do Rails, sua configuração local está perfeita!

## Contribuindo
Consulte o arquivo `CONTRIBUTING.pt-BR.md` (ou `CONTRIBUTING.md`) para conhecer as regras da nossa equipe antes de enviar código.
