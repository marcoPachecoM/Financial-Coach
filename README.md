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

Modules: **JWT authentication** (`POST /api/v1/auth/login`, `/refresh` with rotation and reuse detection, `/logout`, `GET /api/v1/auth/me`), the **accounts** module (`GET`/`POST /api/v1/accounts`, `GET`/`PATCH /api/v1/accounts/{id}`), the **transactions** module (`GET`/`POST /api/v1/transactions`, `GET`/`PATCH`/`DELETE /api/v1/transactions/{id}`), the **CSV import** (`POST /api/v1/transactions/upload-csv`: all or nothing, creates missing accounts, skips rows already imported), **analytics** (`GET /api/v1/analytics/summary`, `GET /api/v1/analytics/by-category-group`) the **monthly budget** (`GET /api/v1/accounts/{id}/budget?month=YYYY-MM`), **recurring transactions** (`/api/v1/recurring-transactions`, or `recurrence` when creating a transaction: the next occurrences are created automatically every day, and a matching CSV charge replaces the automatic one) and **alerts** (`GET /api/v1/accounts/{id}/alerts`: budget near or over the limit, upcoming and missing recurring charges, price changes, subscription audit and suggested subscriptions). Transactions have a **category group** from a closed list of 26 (assigned automatically from the merchant cache when missing, falling back to `Other`) and an optional free-text **category** inside it (e.g. group `Utilities`, category `Gas`); the LLM step comes later. The **copilot** (`POST /api/v1/copilot/chat`) answers questions about an account with an LLM (Gemini through Spring AI) that reads the data through read-only tools; see [Copilot](#copilot-ai-chat) below. The React frontend (below): the copilot panel shows the real alerts with their actions and the AI chat. See [docs/database.md](docs/database.md) for the data model.

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
| `GEMINI_API_KEY` | none (required) | Key of the copilot's LLM ([Google AI Studio](https://aistudio.google.com/apikey)). Tests use a fake one. |
| `GEMINI_MODEL` | `gemini-4-hydrogen` | Any model of your key; a lighter one (e.g. `gemini-flash-lite-latest`) is faster and less often overloaded. |
| `COPILOT_REQUESTS_PER_MINUTE` | `10` | Chat messages per user and minute. |
| `COPILOT_DAILY_LIMIT` | `100` | Chat messages per user and day: caps what the LLM can cost. |
| `RATE_LIMIT_LOGIN` / `RATE_LIMIT_REFRESH` / `RATE_LIMIT_LOGOUT` | `5` / `10` / `20` | Requests per minute and client IP on the token endpoints. |

Variables go in `backend/.env` (gitignored; copy `backend/.env.example`) or in the server's environment. Secrets (keys, passwords) always go there, never in `application.yml`. Any other property of `application.yml` can also be overridden without a placeholder, by its name in upper case with `_` instead of `.` and no dashes, e.g. `FINANCIALCOACH_COPILOT_TIMEOUT=30s` for `financial-coach.copilot.timeout`.

### Rate limits

Some endpoints count requests in **fixed windows** and answer `429 RATE_LIMIT_EXCEEDED` with a `Retry-After` header (seconds until the window ends) once the limit is reached:

| Endpoint | Counted per | Limits (default) | Why |
|---|---|---|---|
| `POST /auth/login` | client IP | 5 / minute | Slows down password guessing. |
| `POST /auth/refresh`, `/auth/logout` | client IP | 10 / minute, 20 / minute | Protects the token table. |
| `POST /copilot/chat` | user | 10 / minute and 100 / day | The minute limit stops scripts and loops; the daily one caps the LLM cost. |

How it works (`shared/web/RateLimiter`):

- Time is split into windows aligned on the server's local time: 10:00:00–10:00:59, 10:01:00–10:01:59..., and days from midnight to midnight.
- Each key (`login|<ip>`, `copilot|<user id>`) has one counter per window length. A request adds 1; the first request of a new window starts again at 1.
- Above the limit the request is rejected before reaching the controller (a Spring MVC interceptor), so a rejected chat message never calls the LLM.
- The token endpoints use the IP because the user is not known yet. The copilot uses the user id: it runs after authentication, and people on the same network share an IP.

Example with the login limit (5 per minute):

| Time | Request | Counter | Result |
|---|---|---|---|
| 10:00:05 | 1st | 1 | allowed |
| 10:00:20 | 5th | 5 | allowed |
| 10:00:37 | 6th | 6 | `429`, `Retry-After: 23` |
| 10:01:02 | 7th | 1 (new window) | allowed |

Limitations: a client can send a full window's worth at 10:00:59 and another at 10:01:00 (fixed windows allow that burst), and the counters live in memory, so they reset on restart and each instance counts on its own. Behind a reverse proxy, set `server.forward-headers-strategy` so the real client IP is used.

To change a limit, set its variable, e.g. `COPILOT_DAILY_LIMIT=300` in `backend/.env`, and restart.

### Copilot (AI chat)

`POST /api/v1/copilot/chat` with `{"message": "...", "account_id": "...", "conversation_id": "..."}` returns `{"response", "provider", "model", "usage"}`.

- **Streaming**: `POST /api/v1/copilot/chat/stream` (same body) sends the answer while it is written; see [Streaming](#streaming-server-sent-events) below.
- **Tools**: the model cannot query the database; it calls read-only Java methods (`CopilotTools`): totals, spending by category group, transaction search, budget status and recurring charges. The user and account come from the request, never from the model.
- **Memory**: messages with the same `conversation_id` (generated by the frontend; new on account change or "New chat") share history, so follow-up questions work. The last 10 messages go back to the model; history is in memory and lost on restart.
- **Fail fast**: each LLM call has a 20 s timeout and one retry. An overloaded, over-quota or unreachable model answers `503 COPILOT_UNAVAILABLE`.
- **Token usage**: `usage` has the `input_tokens`, `output_tokens`, `total_tokens` (as billed; Gemini adds thinking tokens, so it can exceed input + output) and `llm_calls` of the message, summed over the whole tool loop. Each message also logs one line, even when it fails (ids only, never the text): `Copilot usage: outcome=ok user=… account=… model=… calls=2 input=1830 output=95 total=2010`. The chat shows the total under each answer.
- **Cost**: `max-output-tokens` and `thinking-level` in `application.yml`, plus the daily limit above. Without billing enabled on the Google project only the free tier is used (Google may use that data to improve its products).
- **Another model**: change `GEMINI_MODEL`. **Another provider**: change the starter in `pom.xml`, the `spring.ai.*` block in `application.yml`, the API key variable, the chat model type in `CopilotConfig`, and delete `GeminiClientConfig`.

#### Streaming (Server-Sent Events)

Without streaming, the user stares at "thinking" for several seconds, even though the model wrote its first words after one second: the backend waits for the whole answer and sends it as one JSON. With streaming, the text appears as the model writes it, like in ChatGPT. **The total time is the same**, but the app feels much faster.

```
Without:  "thinking" ......... 8 s ......... → whole answer at once
With:     "thinking" 2 s (tool call) → "In September " → "you spent $251.96. " → ...
```

`POST /api/v1/copilot/chat/stream` keeps the connection open and writes events one after another:

```
event:delta
data:{"text":"In September "}

event:delta
data:{"text":"you spent $251.96."}

event:done
data:{"provider":"google-genai","model":"gemini-...","usage":{"input_tokens":300,"output_tokens":40,"total_tokens":380,"llm_calls":2}}
```

| Event | Meaning | What the frontend does |
|---|---|---|
| `delta` | A new piece of text (many of them) | Appends it to the answer bubble |
| `done` | Finished; carries model and token usage | Shows the tokens and enables the input again |
| `error` | Failed (`code`, `detail`), e.g. the model is down | Shows the error; it can arrive after some deltas (the answer stopped half-way) |

Exactly one `done` or `error` closes the stream. Errors before the stream starts (404 account, 429 rate limit, 400 validation) are normal Problem Details responses.

Where it lives:

- `CopilotService.stream()` uses Spring AI's `.stream()` instead of `.call()` and turns each chunk into an event (`ChatStreamEvents` defines the three). Tools, memory and token usage work the same as in `/chat`.
- `frontend/src/services/copilot.ts` (`streamChatMessage`) reads the response piece by piece with `fetch`, because `EventSource` (the browser's SSE reader) cannot send a POST body or the `Authorization` header. `useCopilot` grows the bubble with each delta.
- `/chat` (whole answer as JSON) still exists for clients that prefer it; the rate limit counts both endpoints together.

Try it (`-N` prints each event as it arrives):

```bash
curl -N -X POST http://localhost:8080/api/v1/copilot/chat/stream \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"message": "How much did I spend in September?", "account_id": "<account id>"}'
```

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

Modular monolith, one package per feature under `backend/src/main/java/com/financialcoach/`: `auth`, `users`, `accounts`, `transactions` (including CSV import and recurring transactions), `categorization`, `analytics` (summary, budget, alerts), `copilot` (AI chat), `health` and `shared` (errors, money, time, rate limiter). Entities and repositories are package-private; modules talk through public services and records. Errors follow RFC 9457 Problem Details with a stable `code`.

## 6. CI

GitHub Actions on push and pull request:

- `backend`: `./mvnw verify` on Java 25 (unit and integration tests, Flyway migrations against PostgreSQL with Testcontainers).
- `frontend`: `npm ci`, ESLint, type check and build.

## 7. Production Security Requirements

- Set a strong, unique `JWT_SECRET_KEY` (never the `dev` one) and real database credentials.
- Never use wildcard CORS for authenticated APIs.
- Never log passwords, JWTs or secrets.
- The rate limiter (login, refresh, logout, copilot) is in memory: with several instances, move it to a shared store (e.g. Redis).
- Keep the copilot's daily limit and `max-output-tokens` low if billing is enabled on the LLM account, and set a quota there: budget alerts alone do not stop spending.
- Enforce TLS and set HSTS behind HTTPS.
- Keep dependencies updated and scanned.
