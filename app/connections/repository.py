from sqlalchemy import select

from app.connections.models import Connection
from app.extensions.database import db


class ConnectionRepository:
    @staticmethod
    def get_by_id(connection_id: int) -> Connection | None:
        stmt = select(Connection).where(Connection.id == connection_id)
        return db.session.scalar(stmt)

    @staticmethod
    def get_by_user_id(user_id: int):
        stmt = select(Connection).where(Connection.user_id == user_id)
        return db.session.scalars(stmt).all()

    @staticmethod
    def create(connection: Connection) -> Connection:
        db.session.add(connection)
        db.session.commit()
        return connection
