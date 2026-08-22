# Financial Coach API

## 1. Requirements

- Python 3.14.7
- virtual environment via standard venv
- SQLite for local development by default

## 2. Local Setup (.venv)

```bash
python3.14 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
```

## 3. Environment Variables

Copy and edit:

```bash
cp .env.example .env
```

Important variables:

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

## 4. Database initialization

Run the script to create the database and seed the initial data:

```bash
python -m scripts.seed_users
```

The script is idempotent and stores only Argon2id password hashes.

## 5. Run the API

Local development server:

```bash
flask run
```

Production container/server uses Gunicorn (not flask run).

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
