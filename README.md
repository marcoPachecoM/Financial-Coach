# Financial Coach API

> Personal continuation of a collaborative hackathon project, originally built by a team and now maintained solo.

## Overview

Financial Coach is a personal finance assistant. Users sign in, upload their bank transactions as CSV files, and get a categorized view of their spending: transactions are normalized and assigned to categories (using merchant rules, a cache, and an LLM served through Ollama when needed), then summarized in analytics endpoints by period and by category. A copilot endpoint answers questions and analyzes spending on top of that data. The backend is a Flask API with JWT authentication (short-lived access tokens, rotating refresh tokens), and a React + Vite frontend renders the dashboard.

## 1. Requirements

Install these before starting:

| Tool | Version | Used for | Install (macOS / Homebrew) |
|---|---|---|---|
| [uv](https://docs.astral.sh/uv/) | 0.13+ | Python version + virtual environment + dependencies | `brew install uv` |
| Python | 3.14.x | Backend (uv installs it for you, see below) | `uv python install 3.14` |
| Node.js | 20.19+ (22 LTS recommended) | Frontend (Vite 8) | `brew install node` |
| Docker (optional) | any recent | Running the API + Redis with Compose | Docker Desktop |
| Java | 25 (LTS) | Spring Boot backend in `backend/` (section 6) | `brew install openjdk@25` |
| Docker | any recent | PostgreSQL for `backend/` (dev and tests) | Docker Desktop |

SQLite is used for local development of the Flask API by default; no database server is needed. The Spring Boot backend uses PostgreSQL in Docker.

> Python does not need to be installed system-wide. `uv` downloads and isolates Python 3.14 for this project only.

## 2. Backend Setup

Run these from the project root:

```bash
# 1. Install Python 3.14 (once per machine)
uv python install 3.14

# 2. Pin the project to 3.14 (creates .python-version; only needed once)
uv python pin 3.14

# 3. Create the virtual environment in .venv/
uv venv

# 4. Activate it
source .venv/bin/activate

# 5. Install dependencies (development set includes test/lint tools)
uv pip install -r requirements-dev.txt

# 6. Create your local environment file
cp .env.example .env
```

Check the setup:

```bash
python --version   # Python 3.14.x (inside the activated .venv)
```

> Use `python --version` (or `python3 --version`). `python -v` is *verbose mode*, not the version.

Every new terminal needs `source .venv/bin/activate` before running backend commands. Alternatively, skip activation and prefix commands with `uv run`, e.g. `uv run flask run`.

## 3. Environment Variables

Edit `.env` (created from `.env.example`). Defaults work for local development. Important variables:

- FLASK_ENV: development | testing | production
- SECRET_KEY
- JWT_SECRET_KEY
- JWT_ISSUER
- JWT_AUDIENCE
- JWT_ACCESS_TOKEN_EXPIRES (minutes)
- JWT_REFRESH_TOKEN_EXPIRES (days)
- DATABASE_URL
- CORS_ALLOWED_ORIGINS (comma separated)
- RATE_LIMIT_STORAGE_URI
- LOG_LEVEL

Production fails fast if critical secrets/config are missing.

AI features use Ollama. `AI_PROVIDER`, `AI_MODEL` and `OLLAMA_BASE_URL` point to a model served from the Colab notebook; set `OLLAMA_BASE_URL` to the URL printed by that notebook. The rest of the API works without it.

## 4. Database initialization

Run the script to create the database and seed the demo users:

```bash
python -m scripts.seed_users
```

The script is idempotent (safe to run again) and stores only Argon2id password hashes.

## 5. Run the Application

### 5.1 Backend API (http://localhost:8000)

With the `.venv` activated, from the project root:

```bash
flask run
```

Check it is up:

```bash
curl http://localhost:8000/api/v1/health
```

Production container/server uses Gunicorn (not `flask run`).

### 5.2 Frontend (http://localhost:5173)

In a second terminal:

```bash
cd frontend
cp .env.example .env     # VITE_API_URL=http://localhost:8000/api/v1
npm install              # first time only
npm run dev
```

Open http://localhost:5173. The frontend expects the API from 5.1 to be running.

### 5.3 Troubleshooting

- `command not found: python` inside the project: the venv is not activated (`source .venv/bin/activate`).
- `python --version` shows 3.13 or older: run `uv venv` again, or use `uv run` commands.
- Login fails: run `python -m scripts.seed_users` again.
- CORS errors in the browser: `CORS_ALLOWED_ORIGINS` must include `http://localhost:5173`.

## 6. Spring Boot Backend (migration in progress)

The API is being migrated to Spring Boot in `backend/`, following the contract in [docs/openapi.yaml](docs/openapi.yaml). The Flask API above remains the working backend until the migration is complete.

Current state: database schema (Flyway migrations), temporary HTTP Basic authentication against the `users` table, the **accounts** module (`GET`/`POST /api/v1/accounts`, `GET`/`PATCH /api/v1/accounts/{id}`) the **transactions** module (`GET`/`POST /api/v1/transactions`, `GET`/`PATCH`/`DELETE /api/v1/transactions/{id}`) and the **CSV import** (`POST /api/v1/transactions/upload-csv`: all or nothing, creates missing accounts, skips rows already imported). Automatic categorization currently uses the merchant cache and falls back to `Other`; the LLM step comes later. JWT login comes last; until then, the React frontend keeps using the Flask API. See [docs/database.md](docs/database.md) for the data model.

Requirements: Java 25 and Docker running. If Java 25 is not your default JDK:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
```

Run the tests (Testcontainers starts a throwaway PostgreSQL, applies every migration and checks the constraints):

```bash
cd backend
./mvnw verify
```

Run the app locally against a persistent PostgreSQL:

```bash
cd backend
docker compose up -d --wait                               # PostgreSQL 18 on localhost:5432
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # applies migrations + demo data
curl http://localhost:8080/actuator/health                # {"status":"UP"}
```

The `dev` profile also loads demo data:

| User | Password | Role |
|---|---|---|
| `client1@example.local` | `ClientPass123!` | CLIENT (owns a "Debit card" account) |
| `admin@example.local` | `AdminPass123!` | ADMIN |

Until JWT login exists, authenticate with HTTP Basic (email + password):

```bash
curl -u 'client1@example.local:ClientPass123!' http://localhost:8080/api/v1/health/protected
curl -u 'admin@example.local:AdminPass123!' http://localhost:8080/api/v1/health/admin

# Accounts
curl -u 'client1@example.local:ClientPass123!' http://localhost:8080/api/v1/accounts
curl -u 'client1@example.local:ClientPass123!' -H 'Content-Type: application/json' \
     -d '{"name": "Cash", "monthly_budget": "800"}' http://localhost:8080/api/v1/accounts

# Transactions (account id of the demo "Debit card" account)
curl -u 'client1@example.local:ClientPass123!' -H 'Content-Type: application/json' \
     -d '{"type": "EXPENSE", "datetime": "2026-11-20T10:15", "name": "Oxxo", "amount": "45.50",
          "account_id": "019a0000-0000-7000-8000-000000000101"}' \
     http://localhost:8080/api/v1/transactions
curl -u 'client1@example.local:ClientPass123!' \
     'http://localhost:8080/api/v1/transactions?account_id=019a0000-0000-7000-8000-000000000101&size=20'

# CSV import (header: Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes)
curl -u 'client1@example.local:ClientPass123!' -F 'file=@transactions.csv' \
     http://localhost:8080/api/v1/transactions/upload-csv
```

Inspect the database with:

```bash
docker compose exec postgres psql -U financial_coach -d financial_coach -c '\dt'
```

Migration rules:

- Migrations live in `backend/src/main/resources/db/migration` and are named `V<n>__<description>.sql`.
- Never edit a migration that has been applied; add a new one instead.
- Hibernate runs with `ddl-auto: validate`: only Flyway changes the schema.

## 7. Authentication Flow

Endpoints:

- POST /api/v1/auth/login
- POST /api/v1/auth/refresh
- POST /api/v1/auth/logout
- GET /api/v1/auth/me

Demo protected checks:

- GET /api/v1/health/protected
- GET /api/v1/health/admin

JWT design:

- short-lived access token
- longer-lived refresh token
- issuer/audience validation
- required claims validation
- token type enforcement
- refresh token rotation + reuse detection + revocation state in DB

## 8. Authorization

Use reusable decorators in route layers:

- `@jwt_required()` for authenticated users
- `@roles_required("ADMIN")` for role checks

Do not trust client-provided role/user ownership data.

## 9. Test Commands

```bash
pytest
pytest --cov=app --cov-report=term-missing
```

Test folders:

- tests/unit
- tests/integration
- tests/security
- tests/factories

## 10. Static Analysis

```bash
ruff check .
ruff format --check .
mypy .
```

## 11. Security Analysis

```bash
bandit -r app
pip-audit
```

## 12. Docker

Build and run:

```bash
docker build -t financial-coach-api .
docker run --env-file .env -p 8000:8000 financial-coach-api
```

Compose (API + Redis for distributed rate limiting option):

```bash
docker compose up --build
```

## 13. CI

GitHub Actions workflow runs on push and pull_request:

- ruff
- mypy
- bandit
- pip-audit
- pytest + coverage gate (>=60%)
- `backend` job: `./mvnw verify` on Java 25 (Flyway migrations tested against PostgreSQL with Testcontainers)

## 14. Architecture

High-level structure:

- app/config: environment settings and validation
- app/extensions: db/jwt/limiter wiring
- app/auth: routes, schemas, auth service, token repository, decorators
- app/users: user model + access layer
- app/common: errors, responses, logging, security middleware
- app/api: blueprint registration

Principles:

- routes validate/auth then delegate
- services implement application logic
- repositories isolate persistence
- centralized reusable security controls

## 15. Add a New Module

1. Create package, e.g. app/clients
2. Add models/repository/service/routes/schemas as needed
3. Register blueprint in app/api
4. Add tests across unit/integration/security
5. Apply role checks and schema validation from foundation patterns

## 16. Add a Protected Endpoint

Example pattern:

1. Add route to a blueprint
2. Apply `@jwt_required()` and optional `@roles_required(...)`
3. Validate external input with Marshmallow schema
4. Call a service
5. Return sanitized DTO response

## 17. Production Security Requirements

- Never use default development secrets
- Never enable Flask debug in production
- Never use wildcard CORS for authenticated APIs
- Never log passwords/JWTs/secrets
- Use strong secret management and key rotation
- Use distributed rate-limit storage (e.g., Redis)
- Enforce TLS and set HSTS behind HTTPS
- Keep dependencies pinned and scanned regularly
