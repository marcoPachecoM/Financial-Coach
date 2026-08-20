import sys
from collections.abc import Iterator
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest
from flask import Flask
from flask.testing import FlaskClient
from flask_jwt_extended import create_access_token, create_refresh_token, decode_token

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import create_app
from app.extensions.database import db
from app.users.models import RefreshToken, TokenType, User, UserRole


@dataclass(frozen=True)
class TestUser:
    id: int
    public_id: str
    username: str
    email: str
    role: UserRole
    is_active: bool


@pytest.fixture()
def app() -> Iterator[Flask]:
    application = create_app("testing")

    with application.app_context():
        db.create_all()
        yield application
        db.session.remove()
        db.drop_all()
        db.engine.dispose()


@pytest.fixture()
def client(app: Flask) -> FlaskClient:
    return app.test_client()


@pytest.fixture()
def admin_user(app: Flask) -> TestUser:
    from app.common.passwords import PasswordService

    with app.app_context():
        user = User(
            username="admin",
            email="admin@test.local",
            password_hash=PasswordService.hash_password("AdminPass123!"),
            role=UserRole.ADMIN,
            is_active=True,
        )
        db.session.add(user)
        db.session.commit()
        return TestUser(
            id=user.id,
            public_id=user.public_id,
            username=user.username,
            email=user.email,
            role=user.role,
            is_active=user.is_active,
        )


@pytest.fixture()
def client_user(app: Flask) -> TestUser:
    from app.common.passwords import PasswordService

    with app.app_context():
        user = User(
            username="client",
            email="client@test.local",
            password_hash=PasswordService.hash_password("ClientPass123!"),
            role=UserRole.CLIENT,
            is_active=True,
        )
        db.session.add(user)
        db.session.commit()
        return TestUser(
            id=user.id,
            public_id=user.public_id,
            username=user.username,
            email=user.email,
            role=user.role,
            is_active=user.is_active,
        )


@pytest.fixture()
def inactive_user(app: Flask) -> TestUser:
    from app.common.passwords import PasswordService

    with app.app_context():
        user = User(
            username="inactive",
            email="inactive@test.local",
            password_hash=PasswordService.hash_password("InactivePass123!"),
            role=UserRole.CLIENT,
            is_active=False,
        )
        db.session.add(user)
        db.session.commit()
        return TestUser(
            id=user.id,
            public_id=user.public_id,
            username=user.username,
            email=user.email,
            role=user.role,
            is_active=user.is_active,
        )


@pytest.fixture()
def access_token(app: Flask, admin_user: TestUser) -> str:
    with app.app_context():
        return create_access_token(
            identity=admin_user.public_id,
            additional_claims={
                "role": admin_user.role.value,
                "type": TokenType.ACCESS,
                "iss": app.config["JWT_ISSUER"],
                "aud": app.config["JWT_AUDIENCE"],
            },
        )


@pytest.fixture()
def refresh_token(app: Flask, admin_user: TestUser) -> str:
    with app.app_context():
        token = create_refresh_token(
            identity=admin_user.public_id,
            additional_claims={
                "role": admin_user.role.value,
                "iss": app.config["JWT_ISSUER"],
                "aud": app.config["JWT_AUDIENCE"],
            },
        )
        payload = decode_token(token, allow_expired=False)
        db.session.add(
            RefreshToken(
                jti=str(payload["jti"]),
                user_id=admin_user.id,
                expires_at=datetime.fromtimestamp(float(payload["exp"]), tz=UTC),
                is_revoked=False,
            )
        )
        db.session.commit()
        return token


@pytest.fixture()
def expired_access_token(app: Flask, admin_user: TestUser) -> str:
    with app.app_context():
        return create_access_token(
            identity=admin_user.public_id,
            additional_claims={
                "role": admin_user.role.value,
                "type": TokenType.ACCESS,
                "iss": app.config["JWT_ISSUER"],
                "aud": app.config["JWT_AUDIENCE"],
            },
            expires_delta=timedelta(seconds=-1),
        )
