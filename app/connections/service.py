from app.connections.models import Connection
from app.connections.repository import ConnectionRepository


class ConnectionService:
    def __init__(self, connection_repository: ConnectionRepository | None = None):
        self.connection_repository = connection_repository or ConnectionRepository()

    def create_connection(self, connection_data: Connection) -> Connection:
        return self.connection_repository.create(connection_data)

    def get_connection(self, connection_id: int) -> Connection | None:
        return self.connection_repository.get_by_id(connection_id)

    def get_user_connections(self, user_id: str) -> list[Connection]:
        return self.connection_repository.get_by_user_id(user_id)

