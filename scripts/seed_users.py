from app.connections.models import Connection
import os
from pathlib import Path

from dotenv import load_dotenv

from app import create_app
from app.common.passwords import PasswordService
from app.extensions.database import db
from app.extensions.providers import Providers

from app.users.models import User, UserRole
from app.users.repository import UserRepository

ROOT_DIR = Path(__file__).resolve().parents[1]

load_dotenv(ROOT_DIR / ".env")


DEMO_USERS = [
    {
        "username": "client1",
        "email": "client1@example.local",
        "role": UserRole.CLIENT,
        "password": "ClientPass123!",
    },
    {
        "username": "client2",
        "email": "client2@example.local",
        "role": UserRole.CLIENT,
        "password": "ClientPass123!",
    },
    {
        "username": "client3",
        "email": "client3@example.local",
        "role": UserRole.CLIENT,
        "password": "ClientPass123!",
    },
]


def seed_demo_users() -> None:
    if os.getenv("FLASK_ENV", "development").lower() not in {"development", "testing"}:
        raise RuntimeError(
            "Seeding demo users is only allowed in development/testing environments."
        )

    repository = UserRepository()

    for demo in DEMO_USERS:
        existing = repository.get_by_username(demo["username"])
        if existing:
            continue

        PasswordService.validate_password_strength(demo["password"])

        user = User(
            username=demo["username"],
            email=demo["email"],
            password_hash=PasswordService.hash_password(demo["password"]),
            role=demo["role"],
            is_active=True,
        )
        db.session.add(user)

    for demo in DEMO_USERS:
        user = repository.get_by_username(demo["username"])
        if not user:
            continue

        # Create a default connection for each demo user
        connection = Connection(
            user_id=user.public_id,
            provider=Providers.CSV,
            name=f"{user.username} Connection",
            external_id=f"{ROOT_DIR}/mocks/{user.username}_clean_transactions.csv",
        )
        db.session.add(connection)

    db.session.commit()


if __name__ == "__main__":
    app = create_app(os.getenv("FLASK_ENV", "development"))
    with app.app_context():
        db.create_all()
        seed_demo_users()
    print("Demo users ensured")
