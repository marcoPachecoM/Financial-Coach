from datetime import UTC, datetime

import jwt
from flask_jwt_extended import decode_token

from app.extensions.database import db
from app.users.models import RefreshToken


def test_login_success(client, admin_user):
    response = client.post(
        "/api/v1/auth/login",
        json={"email": admin_user.email, "password": "AdminPass123!"},
    )

    assert response.status_code == 200
    body = response.get_json()
    assert "access_token" in body
    assert "refresh_token" in body


def test_login_incorrect_password(client, admin_user):
    response = client.post(
        "/api/v1/auth/login",
        json={"email": admin_user.email, "password": "wrong-pass"},
    )

    assert response.status_code == 401
    assert response.get_json()["error"]["code"] == "INVALID_CREDENTIALS"


def test_login_nonexistent_user(client):
    response = client.post(
        "/api/v1/auth/login",
        json={"email": "ghost", "password": "pass"},
    )
    assert response.status_code == 400


def test_login_inactive_user(client, inactive_user):
    response = client.post(
        "/api/v1/auth/login",
        json={"email": inactive_user.email, "password": "InactivePass123!"},
    )
    assert response.status_code == 401


def test_me_authenticated(client, access_token):
    response = client.get(
        "/api/v1/auth/me",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    assert response.status_code == 200
    assert response.get_json()["user"]["username"] == "admin"


def test_me_unauthenticated(client):
    response = client.get("/api/v1/auth/me")
    assert response.status_code == 401


def test_refresh_rotation(client, refresh_token):
    response = client.post("/api/v1/auth/refresh", json={"refresh_token": refresh_token})
    assert response.status_code == 200

    old_payload = decode_token(refresh_token, allow_expired=True)
    old_state = db.session.scalar(
        db.select(RefreshToken).where(RefreshToken.jti == str(old_payload["jti"]))
    )
    assert old_state is not None
    assert old_state.is_revoked is True


def test_refresh_reuse_detected(client, refresh_token):
    first = client.post("/api/v1/auth/refresh", json={"refresh_token": refresh_token})
    assert first.status_code == 200

    second = client.post("/api/v1/auth/refresh", json={"refresh_token": refresh_token})
    assert second.status_code == 401
    assert second.get_json()["error"]["code"] == "REFRESH_TOKEN_REUSE"


def test_logout_revokes_refresh_token(client, refresh_token):
    response = client.post("/api/v1/auth/logout", json={"refresh_token": refresh_token})
    assert response.status_code == 200

    payload = decode_token(refresh_token, allow_expired=True)
    state = db.session.scalar(
        db.select(RefreshToken).where(RefreshToken.jti == str(payload["jti"]))
    )
    assert state is not None and state.is_revoked is True


def test_role_forbidden(client, access_token):
    response = client.get(
        "/api/v1/health/admin",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    assert response.status_code == 200


def test_role_forbidden_for_client(app, client, client_user):
    from flask_jwt_extended import create_access_token

    with app.app_context():
        token = create_access_token(
            identity=client_user.public_id,
            additional_claims={
                "role": client_user.role.value,
                "type": "access",
                "iss": app.config["JWT_ISSUER"],
                "aud": app.config["JWT_AUDIENCE"],
            },
        )
    response = client.get(
        "/api/v1/health/admin",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 403


def test_refresh_rejects_access_token(client, access_token):
    response = client.post("/api/v1/auth/refresh", json={"refresh_token": access_token})
    assert response.status_code == 401


def test_expired_access_token_rejected(client, expired_access_token):
    response = client.get(
        "/api/v1/health/protected",
        headers={"Authorization": f"Bearer {expired_access_token}"},
    )
    assert response.status_code == 401


def test_missing_claims_rejected(app, client, admin_user):
    payload = {
        "sub": admin_user.public_id,
        "exp": int(datetime.now(UTC).timestamp()) + 300,
    }
    token = jwt.encode(payload, app.config["JWT_SECRET_KEY"], algorithm="HS256")

    response = client.get(
        "/api/v1/health/protected",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 401


def test_invalid_audience_rejected(app, client, admin_user):
    payload = {
        "sub": admin_user.public_id,
        "jti": "invalid-aud-jti",
        "type": "access",
        "iat": int(datetime.now(UTC).timestamp()),
        "nbf": int(datetime.now(UTC).timestamp()),
        "exp": int(datetime.now(UTC).timestamp()) + 300,
        "iss": app.config["JWT_ISSUER"],
        "aud": "wrong-audience",
        "role": "ADMIN",
    }
    token = jwt.encode(payload, app.config["JWT_SECRET_KEY"], algorithm="HS256")

    response = client.get(
        "/api/v1/health/protected",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 401


def test_invalid_issuer_rejected(app, client, admin_user):
    payload = {
        "sub": admin_user.public_id,
        "jti": "invalid-iss-jti",
        "type": "access",
        "iat": int(datetime.now(UTC).timestamp()),
        "nbf": int(datetime.now(UTC).timestamp()),
        "exp": int(datetime.now(UTC).timestamp()) + 300,
        "iss": "wrong-issuer",
        "aud": app.config["JWT_AUDIENCE"],
        "role": "ADMIN",
    }
    token = jwt.encode(payload, app.config["JWT_SECRET_KEY"], algorithm="HS256")

    response = client.get(
        "/api/v1/health/protected",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 401


def test_invalid_signature_rejected(client, access_token):
    tampered = access_token[:-1] + ("a" if access_token[-1] != "a" else "b")
    response = client.get(
        "/api/v1/health/protected",
        headers={"Authorization": f"Bearer {tampered}"},
    )
    assert response.status_code == 401


def test_malformed_jwt_rejected(client):
    response = client.get(
        "/api/v1/health/protected",
        headers={"Authorization": "Bearer not-a-jwt"},
    )
    assert response.status_code == 401


def test_refresh_token_record_contains_jti(client, admin_user):
    response = client.post(
        "/api/v1/auth/login",
        json={"email": admin_user.email, "password": "AdminPass123!"},
    )
    refresh = response.get_json()["refresh_token"]
    payload = decode_token(refresh)
    state = db.session.scalar(
        db.select(RefreshToken).where(RefreshToken.jti == str(payload["jti"]))
    )
    assert state is not None
    assert state.expires_at.timestamp() > datetime.now(UTC).timestamp()
