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

Modules: **authentication** (JWT access and refresh tokens), **accounts** with an optional monthly budget, **transactions** (typed in or imported from CSV), **recurring transactions** (the next occurrences are created automatically every day), **analytics** (summary, spending by category group, monthly budget), **alerts** and the AI **copilot**. Transactions have a **category group** from a closed list of 26 (assigned from the merchant cache when missing, falling back to `Other`; an LLM step is planned) and an optional free-text **category** inside it (e.g. group `Utilities`, category `Gas`). See [docs/database.md](docs/database.md) for the data model.

### Endpoints

All under `/api/v1`. Everything except login, refresh and the public health checks needs `Authorization: Bearer <access token>`, and a user only sees their own accounts and data (someone else's id answers `404`, as if it did not exist). Errors are [Problem Details](#5-architecture) with a stable `code`. Full contract: [docs/openapi.yaml](docs/openapi.yaml).

| Method and path | What it does |
|---|---|
| **Auth** | |
| `POST /auth/login` | Email and password → access token (15 min) and refresh token (14 days, single use). |
| `POST /auth/refresh` | Refresh token in the `Authorization` header → new pair. The old refresh token stops working; reusing it revokes every session of the user. |
| `POST /auth/logout` | Revokes the refresh token sent in the header. Idempotent. |
| `GET /auth/me` | The signed-in user's profile (id, username, email, role, active). |
| **Health** | |
| `GET /health`, `GET /health/public` | Public: `{"status": "ok"}` (the first one adds the active Spring profile). `GET /actuator/health` (outside `/api/v1`) also checks the database. |
| `GET /health/protected`, `GET /health/admin` | Demo checks of the security levels: any signed-in user, and `ADMIN` only (`403` for others). |
| **Accounts** | |
| `GET /accounts` | The user's accounts. |
| `POST /accounts` | Creates one: `name` (unique per user, else `409`) and optional `monthly_budget`. |
| `GET /accounts/{id}` | One account. |
| `PATCH /accounts/{id}` | Renames it or changes the budget; `"monthly_budget": null` removes the budget. |
| `GET /accounts/{id}/budget?month=YYYY-MM` | The month's expenses against the budget: budget, spent, remaining, percentage used and status (`ON_TRACK` below 80 %, `WARNING` up to 100 %, `EXCEEDED` above, `NO_BUDGET`). Default month: the current one. |
| `GET /accounts/{id}/alerts?month=YYYY-MM` | The month's [alerts](#alerts), most severe first. Default month: the current one. |
| **Transactions** | |
| `GET /transactions?account_id=…` | Newest first, paginated (`page` from 1, `size` 1–100, default 10). Optional filters, all applied by the server: `start_date` and `end_date`; `q` (text in the name, category, notes or a tag); `category_group`; `tag` (one exact tag); `min_amount` and `max_amount` (absolute amount, inclusive, so `$50–$200` works for expenses and income). |
| `POST /transactions` | Creates one. Expenses have a positive amount, income a negative one. Without `category_group` it is assigned automatically. With `recurrence` (`frequency`, optional `end_date`) it also becomes a recurring transaction. Optional `tags` (names; new ones are created, known ones reused ignoring case; at most 10, up to 40 characters, no `;`). |
| `GET /transactions/{id}` | One transaction. |
| `PATCH /transactions/{id}` | Changes the fields sent; `category` and `notes` can be cleared with `null`. `tags` replaces every tag (`[]` removes them). The next occurrences of a recurring transaction copy the tags of the latest one. |
| `DELETE /transactions/{id}` | Deletes it (soft delete: it disappears from every list and total). If it was an occurrence of a recurring transaction, that occurrence is not created again. |
| `GET /transactions/export` | Downloads the transactions as CSV in the import format, oldest first: one account (`account_id`) or all of them, optionally between `start_date` and `end_date`. Expenses negative, income positive. Text that looks like a spreadsheet formula gets a leading `'` (CSV injection), which the import removes. |
| `POST /transactions/upload-csv` | Multipart `file`. Header: `Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes`, optionally followed by `Tags` (names separated by `;`). All or nothing: every row is validated first (errors listed per row), then accounts that do not exist yet are created and duplicates are skipped: rows already imported, and rows identical to an existing transaction (same account, date and time to the second, name and amount), so an exported file can be imported again. |
| `GET /tags` | The user's tags in use, most used first, with how many transactions carry each; `account_id` limits the count to one account. |
| **Recurring transactions** | |
| `GET /recurring-transactions?account_id=…` | The account's recurring transactions, with their next due date. |
| `POST /recurring-transactions` | Makes an existing transaction recurring: `transaction_id`, `frequency` (`WEEKLY`, `BIWEEKLY`, `MONTHLY`, `YEARLY`), optional `end_date`. Occurrences already due are created at once. |
| `GET /recurring-transactions/{id}` | One recurring transaction. |
| `PATCH /recurring-transactions/{id}` | Changes name, amount, category group, category, notes or `end_date` (an end date stops it). Applies to the occurrences created from now on. |
| `DELETE /recurring-transactions/{id}` | Deletes the rule; the transactions it created are kept as ordinary ones. |
| **Analytics** | |
| `GET /analytics/summary?account_id=…` | Total expenses and income, net, counts, average and largest expense, and the dates of the first and last transaction, optionally between `start_date` and `end_date`. |
| `GET /analytics/daily?account_id=…` | One entry per day with transactions (expense total and count, transactions of any type), optionally between dates and of one `category_group`. Feeds the calendar and the spending trend. |
| `GET /analytics/by-category-group?account_id=…` | Totals per category group for `type` (`EXPENSE` by default or `INCOME`), optionally between dates. |
| **Copilot** | |
| `POST /copilot/chat` | A question about an account (`message`, `account_id`, optional `conversation_id`) → the whole answer as JSON, with the model and token usage. See [Copilot](#copilot-ai-chat). |
| `POST /copilot/chat/stream` | Same body; the answer arrives while it is written, as Server-Sent Events. This is what the frontend uses. See [Streaming](#streaming-server-sent-events). |

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
docker compose up -d --wait                               # PostgreSQL 18 on localhost:5432 (this machine only)
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
# CSV export, same format (omit account_id for every account)
curl -H "$AUTH" -OJ "$API/transactions/export?account_id=$ACCOUNT&start_date=2026-09-01&end_date=2026-09-30"

# Analytics and budget
curl -H "$AUTH" "$API/analytics/summary?account_id=$ACCOUNT&start_date=2026-11-01&end_date=2026-11-30"
curl -H "$AUTH" "$API/analytics/by-category-group?account_id=$ACCOUNT&type=EXPENSE"
curl -H "$AUTH" "$API/analytics/daily?account_id=$ACCOUNT&start_date=2026-11-01&end_date=2026-11-30"
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

#### Alerts

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
| `COPILOT_MAX_LLM_CALLS` | `5` | Calls to the model for one message (the tool loop; a normal question takes 2 or 3). Above it the message stops with `502 COPILOT_CALL_LIMIT`. |
| `RATE_LIMIT_LOGIN` / `RATE_LIMIT_REFRESH` / `RATE_LIMIT_LOGOUT` | `5` / `10` / `20` | Requests per minute and client IP on the token endpoints. |
| `LOG_FILE` | `logs/financial-coach.log` | Log file besides the console (relative to `backend/`), rotated daily or every 10 MB and kept 7 days (100 MB at most). Empty: console only. |
| `RATE_LIMIT_CSV` | `10` | CSV export and import requests per user and minute. |
| `RATE_LIMIT_LOGIN_ACCOUNT_FAILURES` | `5` | Consecutive failed logins of one email, from any IP, before each new attempt must wait (see [Rate limits](#rate-limits)). |

Variables go in `backend/.env` (gitignored; copy `backend/.env.example`) or in the server's environment. Secrets (keys, passwords) always go there, never in `application.yml`. Any other property of `application.yml` can also be overridden without a placeholder, by its name in upper case with `_` instead of `.` and no dashes, e.g. `FINANCIALCOACH_COPILOT_TIMEOUT=30s` for `financial-coach.copilot.timeout`.

### Rate limits

Some endpoints count requests in **fixed windows** and answer `429 RATE_LIMIT_EXCEEDED` with a `Retry-After` header (seconds until the window ends) once the limit is reached:

| Endpoint | Counted per | Limits (default) | Why |
|---|---|---|---|
| `POST /auth/login` | client IP | 5 / minute | Slows down password guessing. |
| `POST /auth/login` | email (any IP) | after 5 failures in a row: wait 30 s, then 1, 2, 4, 8 and at most 15 min | Stops guessing one account's password from many IPs. |
| `POST /auth/refresh`, `/auth/logout` | client IP | 10 / minute, 20 / minute | Protects the token table. |
| `GET /transactions/export`, `POST /transactions/upload-csv` | user | 10 / minute each | Each request reads or writes a whole file. |
| `POST /copilot/chat`, `/copilot/chat/stream` | user (one counter for both) | 10 / minute and 100 / day | The minute limit stops scripts and loops; the daily one caps the LLM cost. |

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

The per-email login limit (`auth/LoginThrottle`) works differently: it counts consecutive failures, not requests. After 5, every attempt for that email waits, even with the right password, and each new failure doubles the wait (30 s up to 15 min). A successful login, or an hour without failures, starts again from zero. It applies to unknown emails too, so a 429 never reveals which emails have an account. It is a growing delay rather than a lockout, so an attacker can make a victim wait while they keep failing, but cannot lock the account for good.

Limitations: a client can send a full window's worth at 10:00:59 and another at 10:01:00 (fixed windows allow that burst), and the counters live in memory, so they reset on restart and each instance counts on its own. Behind a reverse proxy, set `server.forward-headers-strategy` so the real client IP is used.

To change a limit, set its variable, e.g. `COPILOT_DAILY_LIMIT=300` in `backend/.env`, and restart.

### Copilot (AI chat)

`POST /api/v1/copilot/chat` with `{"message": "...", "account_id": "...", "conversation_id": "..."}` returns `{"response", "provider", "model", "usage"}`.

- **Streaming**: `POST /api/v1/copilot/chat/stream` (same body) sends the answer while it is written; see [Streaming](#streaming-server-sent-events) below.
- **Tools**: the model cannot query the database; it calls read-only Java methods (`CopilotTools`): totals, spending by category group, transaction search, budget status, recurring charges and the month-end forecast. The forecast is computed in Java (spent so far + recurring charges still due this month + the daily average of the other expenses × days left), so the model only explains the numbers instead of doing the arithmetic. The user and account come from the request, never from the model.
- **Prompt injection**: transaction names and categories come from bank files and user input, so a row could contain instructions ("ignore your rules..."). The system prompt tells the model that tool results are data, never instructions. Even if it obeyed, the tools are read-only and scoped to the user's account.
- **Tool loop limit**: Spring AI's tool loop has no limit of its own, and the rate limit counts HTTP requests, not model calls. A model that keeps asking for tools is stopped after `COPILOT_MAX_LLM_CALLS` calls (5): `502 COPILOT_CALL_LIMIT`, logged as `outcome=call_limit`.
- **Memory**: messages with the same `conversation_id` share history, so follow-up questions work ("and in October?"). The last 10 messages go back to the model. The history lives in the server's memory: it is lost on restart, and at most 1000 conversations are kept (the least recently used are forgotten first). Without `conversation_id`, every message stands alone.
- **Frontend chat**: one conversation per account. Switching accounts shows that account's chat and coming back shows the earlier messages; an answer still streaming is cancelled. The **Stop** button (in place of Send while answering) cancels the answer too. Chats are kept while the page is open, not in localStorage, because they hold financial data.
- **Fail fast**: each LLM request has a 20 s timeout and one retry (only for 408 and 5xx). An overloaded, slow or unreachable model answers `503 COPILOT_UNAVAILABLE`. A model that returns no text (safety filters, output limit) answers `502 COPILOT_EMPTY_RESPONSE`.
- **Provider quota**: when the model's quota is used up (HTTP 429; Gemini's free tier allows a few requests per model per day, and each question with tools takes about 2), the answer is `503 COPILOT_QUOTA_EXCEEDED` (or that `error` event when streaming) with when to retry, read from the provider's error: `The AI model's usage quota is used up. Try again in 6 h 51 min.` 429 is not retried, since a daily quota would only make the user wait. Gemini's daily quota resets at midnight Pacific time; see your usage at https://ai.dev/rate-limit, or switch `GEMINI_MODEL`, since the quota is per model.
- **Token usage**: `usage` has the `input_tokens`, `output_tokens`, `total_tokens` (as billed; Gemini adds thinking tokens, so it can exceed input + output) and `llm_calls` of the message, summed over the whole tool loop. Each message also logs exactly one line, even when it fails (ids only, never the text): `Copilot usage: outcome=ok user=… account=… model=… calls=2 input=1830 output=95 total=2010`. `outcome` is `ok`, `empty`, `unknown_tool`, `failed`, `quota`, `call_limit` or `cancelled` (the client stopped a streamed answer; the tokens spent until then are counted). The chat shows the total under each answer.
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
| `error` | Failed: `code` (`COPILOT_UNAVAILABLE`, `COPILOT_QUOTA_EXCEEDED`, `COPILOT_CALL_LIMIT` or `COPILOT_EMPTY_RESPONSE`) and `detail` | Shows the error; it can arrive after some deltas (the answer stopped half-way) |

Exactly one `done` or `error` closes the stream, unless the client cancels it (closing the connection stops the model too). The response carries `X-Accel-Buffering: no` so that nginx passes each event on instead of buffering the whole answer; other proxies may need buffering turned off for this path. Errors before the stream starts (404 account, 429 rate limit, 400 validation) are normal Problem Details responses.

Where it lives:

- `CopilotService.stream()` uses Spring AI's `.stream()` instead of `.call()` and turns each chunk into an event (`ChatStreamEvents` defines the three). Tools, memory and token usage work the same as in `/chat`.
- `frontend/src/services/copilot.ts` (`streamChatMessage`) reads the response piece by piece with `fetch`, because `EventSource` (the browser's SSE reader) cannot send a POST body or the `Authorization` header. `useCopilot` grows the bubble with each delta, and aborts the request on Stop or when switching accounts.
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

What it has:

- **Sidebar** (collapsible; a drawer on phones): the Dashboard and Transactions views, and the **accounts**. Picking an account switches every view to it. Each account shows the month's spending against its budget and against last month; accounts can be created and edited there.
- **Dashboard**: metric cards, budget, recurring transactions, spending by category group and the spending trend, for a chosen period. Every figure comes already added up from the `/analytics` endpoints (the calendar too), so the browser never downloads the whole history: the page stays as fast with years of transactions as with a month.
- **Tags**: a field with suggestions in the transaction form; each row shows its tags (click one to filter), the most used tags appear as chips above the list, and the search box also finds tags.
- **Transactions**: a calendar (each day shaded by what was spent; click a day to filter) above the list, with search, a category filter and an amount range (e.g. $50–$200). The list asks the server for one page of 10 with every filter applied, so it does not load the whole history; typing waits 300 ms before sending the request. Each row can be viewed, edited, deleted or made recurring.
- **Header**: add a transaction (optionally repeating), import a CSV, and export one (a dialog to pick one account or all, and a period).
- **Copilot panel**: the account's alerts with their actions (make recurring, update the amount, stop) and the AI chat, streamed, with a Stop button and one conversation per account.

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
- Health: `GET /api/v1/health` and `/health/public` are public; `GET /api/v1/health/protected` (any signed-in user) and `/health/admin` (`ADMIN` only) demonstrate the security levels.

Access tokens are HS256 JWTs validated without a database lookup (issuer, audience, expiry, token type and required claims). Only the `jti` of refresh tokens is stored; expired ones are deleted daily.

## 5. Architecture

Modular monolith, one package per feature under `backend/src/main/java/com/financialcoach/`: `auth`, `users`, `accounts`, `transactions` (including CSV import and recurring transactions), `categorization`, `analytics` (summary, budget, alerts), `copilot` (AI chat), `health` and `shared` (errors, money, time, rate limiter). Entities and repositories are package-private; modules talk through public services and records. Errors follow RFC 9457 Problem Details with a stable `code`.

## 6. CI

GitHub Actions on push and pull request:

- `backend`: `./mvnw verify` on Java 25 (unit and integration tests, Flyway migrations against PostgreSQL with Testcontainers).
- `frontend`: `npm ci`, ESLint, type check and build.

Dependabot ([.github/dependabot.yml](.github/dependabot.yml)) opens weekly pull requests for Maven, npm and GitHub Actions updates (minor and patch grouped per ecosystem). For immediate pull requests on security advisories, enable *Dependabot alerts* and *Dependabot security updates* in the repository settings.

## 7. Production Security Requirements

- Set a strong, unique `JWT_SECRET_KEY` (never the `dev` one) and real database credentials.
- Never use wildcard CORS for authenticated APIs.
- Never log passwords, JWTs or secrets.
- The rate limiter (login, refresh, logout, copilot) is in memory: with several instances, move it to a shared store (e.g. Redis).
- Keep the copilot's daily limit and `max-output-tokens` low if billing is enabled on the LLM account, and set a quota there: budget alerts alone do not stop spending.
- With real users, use a paid LLM tier (or get their consent): on Gemini's free tier Google may use prompts, which carry merchants, amounts and questions, to improve its products.
- Do not run with the `dev` profile: it logs Spring AI at DEBUG, which can include tool arguments and results (financial data).
- Enforce TLS and set HSTS behind HTTPS.
- Keep dependencies updated and scanned.
