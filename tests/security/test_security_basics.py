from datetime import UTC, datetime

import jwt

from app.users.models import TokenType


def test_security_headers_present(client):
    response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.headers.get("X-Content-Type-Options") == "nosniff"
    assert response.headers.get("X-Frame-Options") == "DENY"
    assert response.headers.get("Referrer-Policy") == "no-referrer"
    assert response.headers.get("Permissions-Policy") is not None
    assert response.headers.get("Content-Security-Policy") is not None
    assert response.headers.get("Cache-Control") == "no-store"


def test_oversized_request_rejected(app, client):
    app.config["MAX_CONTENT_LENGTH"] = 128
    payload = {"username": "x" * 200, "password": "y" * 200}

    response = client.post("/api/v1/auth/login", json=payload)
    assert response.status_code in {400, 413}


def test_unexpected_fields_rejected(client, admin_user):
    response = client.post(
        "/api/v1/auth/login",
        json={
            "username": admin_user.username,
            "password": "AdminPass123!",
            "role": "ADMIN",
            "is_active": True,
        },
    )
    assert response.status_code == 400


def test_sql_injection_string_in_login(client):
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin' OR '1'='1", "password": "irrelevant"},
    )
    assert response.status_code == 400


def test_protected_endpoint_requires_auth(client):
    response = client.get("/api/v1/health/protected")
    assert response.status_code == 401


def test_cors_not_wildcard(app):
    assert "*" not in app.config["CORS_ALLOWED_ORIGINS"]


def test_sensitive_data_not_exposed_on_login(client, admin_user):
    response = client.post(
        "/api/v1/auth/login",
        json={"username": admin_user.username, "password": "AdminPass123!"},
    )
    body = response.get_json()
    assert "password_hash" not in str(body)


def test_jwt_alg_none_rejected(app, client, admin_user):
    payload = {
        "sub": admin_user.public_id,
        "jti": "none-alg-jti",
        "type": TokenType.ACCESS,
        "iat": int(datetime.now(UTC).timestamp()),
        "nbf": int(datetime.now(UTC).timestamp()),
        "exp": int(datetime.now(UTC).timestamp()) + 300,
        "iss": app.config["JWT_ISSUER"],
        "aud": app.config["JWT_AUDIENCE"],
        "role": "ADMIN",
    }
    token = jwt.encode(payload, key="", algorithm="none")
    response = client.get(
        "/api/v1/health/protected",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 401
