# PIX Project

This is our university project for Configuration Management. We are building a simple PIX system with accounts and transfers.

## Project Structure
- `/backend`: Ruby on Rails API

## How to run

### 1. Environment Variables
Before running the project, you must create a `.env` file in the root directory (this file is ignored by Git for security). Ask a team member for the development variables, or copy these defaults:

```env
SECRET_KEY_BASE=local_development_secret_key
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

### 2. Start the Project
Make sure you have Docker installed. We use PostgreSQL 15, which will be automatically pulled and started by Docker. To start the database and backend, run:

```bash
docker compose up --build
```

### 3. Fixing Common Permission Errors (Linux Users)
If you are on Linux and get a `Permission denied` error about the `backend/tmp` or `backend/log` folders crashing the container, it's because Docker creates these folders as the `root` user. Fix it by running:

```bash
sudo chmod -R 777 backend/tmp backend/log
```

If you get a permission error about `backend/config/master.key`, run:

```bash
sudo chmod 644 backend/config/master.key
```

### 4. Verify it's working!
Open your browser and navigate to the health check endpoint:
👉 [http://localhost:3000/up](http://localhost:3000/up)

If you see a green screen with the Rails logo, your local setup is perfect!

### 5. Seeding the Database (Test Data)
When running `docker compose up --build` for the first time, `rails db:prepare` runs migrations and seeds the database automatically. If your database was already initialized and you want to reload the test seeds for Postman, run:

```bash
docker compose exec backend bundle exec rails db:seed
```

#### Pre-configured Test Users:
| User | Email | Password | Role | Pix Key | Initial Balance |
|---|---|---|---|---|---|
| **Alice Silva** | `alice.test@example.com` | `password123` | `operator` (Full access) | `alice.test@example.com` | R$ 1,500.00 |
| **Bob Santos** | `bob.test@example.com` | `password123` | `customer` | `bob.test@example.com` | R$ 800.00 |

### 6. Observability (Prometheus + Grafana)
Set `METRICS_BEARER_TOKEN`, `GRAFANA_ADMIN_USER`, and `GRAFANA_ADMIN_PASSWORD` in your ignored `.env` file before starting the containers with `docker compose up`:
- **API Metrics Endpoint (`/metrics`)**: `http://localhost:3000/metrics` (protected by `Authorization: Bearer <METRICS_BEARER_TOKEN>`).
- **Prometheus**: [http://127.0.0.1:9090](http://127.0.0.1:9090) (bound to loopback by default).
- **Grafana**: [http://127.0.0.1:3001](http://127.0.0.1:3001) (authenticated with `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from `.env` — pre-provisioned with the Prometheus datasource and the **PIX — Observabilidade Completa** dashboard under `ops/grafana/`).
- **PostgreSQL Exporter**: `http://127.0.0.1:9187/metrics`

---

## API Testing with Postman

We provide a comprehensive Postman test collection covering 100% of the API v1 flows:

- **File:** [`postman_pix_master_suite.json`](postman_pix_master_suite.json)
- **Coverage:** 25 requests including JWT auth, accounts, statement, fraud blocking, Pix keys, transfers with idempotency, and refunds.

### How to run in VS Code / Postman:
1. Open the **Postman** extension in VS Code or Postman Desktop.
2. Click **Import** and select `postman_pix_master_suite.json` from the repository root.
3. Ensure `baseUrl` is set to `http://localhost:3000` (default).
4. Run requests individually or run the whole suite with **Collection Runner**. Tokens and IDs are dynamically linked across requests.

---

## Contributing
Check the `CONTRIBUTING.md` file for our team rules before pushing code.
