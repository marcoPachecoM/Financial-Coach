# Financial Coach

> Personal continuation of a collaborative hackathon project, originally built by a team and now maintained solo.

## Overview

Financial Coach is a personal finance assistant. Users sign in, import their bank transactions from CSV files (or type them in), and get a categorized view of their spending: analytics by period and category, monthly budgets, recurring transactions that are added automatically, and alerts (budget limits, upcoming or missing recurring charges, price changes, suggested subscriptions).

- **Backend:** Spring Boot 4 (Java 25) in `backend/`, PostgreSQL 18, Flyway, JWT authentication. Contract: [docs/openapi.yaml](docs/openapi.yaml). Data model: [docs/database.md](docs/database.md).
- **Frontend:** React 19 + Vite + Tailwind in `frontend/`.
- **API collection:** [docs/postman/financial-coach.postman_collection.json](docs/postman/financial-coach.postman_collection.json) (Postman → Import).

The original Flask backend was replaced by the Spring Boot one and removed from the repository (it remains in the git history).

## 1. Requirements

| Tool | Version | Used for | Install (macOS / Homebrew) |
|---|---|---|---|
| Java | 25 (LTS) | Backend | `brew install openjdk@25` |
| Docker | any recent | PostgreSQL (dev) and Testcontainers (tests) | Docker Desktop |
| Node.js | 20.19+ (22 LTS recommended) | Frontend (Vite 8) | `brew install node` |

## 2. Backend

Modules: **JWT authentication** (`POST /api/v1/auth/login`, `/refresh` with rotation and reuse detection, `/logout`, `GET /api/v1/auth/me`), the **accounts** module (`GET`/`POST /api/v1/accounts`, `GET`/`PATCH /api/v1/accounts/{id}`), the **transactions** module (`GET`/`POST /api/v1/transactions`, `GET`/`PATCH`/`DELETE /api/v1/transactions/{id}`), the **CSV import** (`POST /api/v1/transactions/upload-csv`: all or nothing, creates missing accounts, skips rows already imported), **analytics** (`GET /api/v1/analytics/summary`, `GET /api/v1/analytics/by-category-group`) the **monthly budget** (`GET /api/v1/accounts/{id}/budget?month=YYYY-MM`), **recurring transactions** (`/api/v1/recurring-transactions`, or `recurrence` when creating a transaction: the next occurrences are created automatically every day, and a matching CSV charge replaces the automatic one) and **alerts** (`GET /api/v1/accounts/{id}/alerts`: budget near or over the limit, upcoming and missing recurring charges, price changes, subscription audit and suggested subscriptions). Transactions have a **category group** from a closed list of 26 (assigned automatically from the merchant cache when missing, falling back to `Other`) and an optional free-text **category** inside it (e.g. group `Utilities`, category `Gas`); the LLM step comes later. The React frontend (below): the copilot panel shows the real alerts with their actions, while its chat still answers locally until the copilot endpoint is migrated. See [docs/database.md](docs/database.md) for the data model.

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

Log in to get a token pair, then send the **access token** in the `Authorization` header (examples use [jq](https://jqlang.org/) to read the JSON: `brew install jq`):

```bash
API=http://localhost:8080/api/v1
LOGIN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
             -d '{"email": "client1@example.local", "password": "ClientPass123!"}')
TOKEN=$(echo "$LOGIN" | jq -r .access_token)      # valid 15 minutes
REFRESH=$(echo "$LOGIN" | jq -r .refresh_token)   # valid 14 days, single use
AUTH="Authorization: Bearer $TOKEN"

curl -H "$AUTH" $API/auth/me

# New pair when the access token expires. The old refresh token stops working;
# using it again is treated as theft and closes every session of the user.
curl -X POST -H "Authorization: Bearer $REFRESH" $API/auth/refresh
curl -X POST -H "Authorization: Bearer $REFRESH" $API/auth/logout

# Accounts
curl -H "$AUTH" $API/accounts
curl -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"name": "Cash", "monthly_budget": "800"}' $API/accounts

# Transactions (account id of the demo "Debit card" account)
ACCOUNT=019a0000-0000-7000-8000-000000000101
curl -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"type": "EXPENSE", "datetime": "2026-11-20T10:15", "name": "Oxxo", "amount": "45.50",
          "account_id": "'$ACCOUNT'"}' \
     $API/transactions
curl -H "$AUTH" "$API/transactions?account_id=$ACCOUNT&size=20"

# CSV import (header: Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes)
curl -H "$AUTH" -F 'file=@transactions.csv' $API/transactions/upload-csv

# Analytics and budget
curl -H "$AUTH" "$API/analytics/summary?account_id=$ACCOUNT&start_date=2026-11-01&end_date=2026-11-30"
curl -H "$AUTH" "$API/analytics/by-category-group?account_id=$ACCOUNT&type=EXPENSE"
curl -H "$AUTH" "$API/accounts/$ACCOUNT/budget?month=2026-11"

# Recurring: Netflix every month from September. The occurrences already due
# (October, November) are created at once; the next ones, every day at 00:05.
curl -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"type": "EXPENSE", "datetime": "2026-09-05T07:00", "name": "Netflix", "amount": "199",
          "category_group": "Entertainment", "account_id": "'$ACCOUNT'",
          "recurrence": {"frequency": "MONTHLY"}}' \
     $API/transactions
curl -H "$AUTH" "$API/recurring-transactions?account_id=$ACCOUNT"
RULE=<id from the list>
curl -X PATCH -H "$AUTH" -H 'Content-Type: application/json' -d '{"amount": "219"}' $API/recurring-transactions/$RULE
curl -X PATCH -H "$AUTH" -H 'Content-Type: application/json' -d '{"end_date": "2026-12-31"}' $API/recurring-transactions/$RULE

# Alerts of the month (most severe first)
curl -H "$AUTH" "$API/accounts/$ACCOUNT/alerts"
```

Alerts are computed on every request (nothing is stored). Each one has a stable `id`, a `type`, a `severity` and structured `data`; the client writes the text:

| Type | When |
|---|---|
| `BUDGET_WARNING` / `BUDGET_EXCEEDED` | The month's expenses reach 80 % / exceed the account's budget. |
| `RECURRING_UPCOMING` | A recurring transaction is due in the next 7 days. |
| `RECURRING_MISSING` | An occurrence was created automatically, but an imported bank statement that already covers its date does not show it. |
| `RECURRING_PRICE_CHANGED` | A real charge of a recurring transaction has another amount. |
| `SUBSCRIPTION_AUDIT` | The month's recurring expenses were charged at the expected amount. |
| `RECURRING_SUGGESTED` | 3+ charges of the same merchant at a regular interval that are not recurring yet. |

Configuration (environment variables; the `dev` profile provides a development secret):

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET_KEY` | none (required) | HS256 key, at least 32 bytes. The app does not start without it outside `dev`. |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `financial-coach-api` / `financial-coach-clients` | Checked on every token. |
| `JWT_ACCESS_TOKEN_TTL` / `JWT_REFRESH_TOKEN_TTL` | `15m` / `14d` | Spring durations (`30m`, `7d`...). |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | dev: local Docker database | Required outside `dev`. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins allowed to call the API (no `*`). |
| `RECURRING_SCHEDULER_ENABLED` | `true` | Creates the due occurrences of recurring transactions daily and at startup. Safe with several instances. |
| `RECURRING_CRON` | `0 5 0 * * *` | When the daily run happens (Spring cron, server time zone). |
| `REFRESH_TOKEN_CLEANUP_CRON` | `0 30 3 * * *` | Daily deletion of refresh tokens expired more than a day ago. |

Login, refresh and logout are rate limited per client IP (5, 10 and 20 per minute) and answer `429` with `Retry-After` when exceeded.

Inspect the database with:

```bash
docker compose exec postgres psql -U financial_coach -d financial_coach -c '\dt'
```

Migration rules:

- Migrations live in `backend/src/main/resources/db/migration` and are named `V<n>__<description>.sql`.
- Never edit a migration that has been applied; add a new one instead.
- Hibernate runs with `ddl-auto: validate`: only Flyway changes the schema.


## 3. Frontend (http://localhost:5173)

With the backend running (`dev` profile), in a second terminal:

```bash
cd frontend
cp .env.example .env     # VITE_API_URL=http://localhost:8080/api/v1
npm install              # first time only
npm run dev
```

Open http://localhost:5173 and log in with the demo user (prefilled in development).

```bash
npm run lint     # ESLint
npm run build    # type check + production build
```

Troubleshooting:

- `release version 25 not supported` when building the backend: Maven is using an older JDK; `export JAVA_HOME=/opt/homebrew/opt/openjdk@25` (add it to `~/.zshrc`).
- CORS errors in the browser: `CORS_ALLOWED_ORIGINS` must include the frontend origin (default `http://localhost:5173`).

## 4. Authentication

- `POST /api/v1/auth/login` → access token (15 min) + refresh token (14 days, single use).
- `POST /api/v1/auth/refresh` → new pair; the old refresh token stops working. Reusing a rotated token is treated as theft and revokes every session of the user.
- `POST /api/v1/auth/logout` (idempotent) and `GET /api/v1/auth/me`.
- Demo checks: `GET /api/v1/health/protected`, `GET /api/v1/health/admin`.

Access tokens are HS256 JWTs validated without a database lookup (issuer, audience, expiry, token type and required claims). Only the `jti` of refresh tokens is stored; expired ones are deleted daily.

## 5. Architecture

Modular monolith, one package per feature under `backend/src/main/java/com/financialcoach/`: `auth`, `users`, `accounts`, `transactions` (including CSV import and recurring transactions), `categorization`, `analytics` (summary, budget, alerts), `health` and `shared` (errors, money, time). Entities and repositories are package-private; modules talk through public services and records. Errors follow RFC 9457 Problem Details with a stable `code`.

## 6. CI

GitHub Actions on push and pull request:

- `backend`: `./mvnw verify` on Java 25 (unit and integration tests, Flyway migrations against PostgreSQL with Testcontainers).
- `frontend`: `npm ci`, ESLint, type check and build.

## 7. Production Security Requirements

- Set a strong, unique `JWT_SECRET_KEY` (never the `dev` one) and real database credentials.
- Never use wildcard CORS for authenticated APIs.
- Never log passwords, JWTs or secrets.
- The login/refresh rate limiter is in memory: with several instances, move it to a shared store.
- Enforce TLS and set HSTS behind HTTPS.
- Keep dependencies updated and scanned.
