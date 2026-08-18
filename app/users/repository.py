from sqlalchemy import select

from app.extensions.database import db
from app.users.models import User


class UserRepository:
    @staticmethod
    def get_by_username(username: str) -> User | None:
        stmt = select(User).where(User.username == username)
        return db.session.scalar(stmt)

    @staticmethod
    def get_by_email(email: str) -> User | None:
        stmt = select(User).where(User.email == email)
        return db.session.scalar(stmt)

    @staticmethod
    def get_by_public_id(public_id: str) -> User | None:
        stmt = select(User).where(User.public_id == public_id)
        return db.session.scalar(stmt)

    @staticmethod
    def create(user: User) -> User:
        db.session.add(user)
        db.session.commit()
        return user
