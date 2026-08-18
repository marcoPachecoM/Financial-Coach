from app.common.exceptions import NotFoundError, UnauthorizedError
from app.connections.repository import ConnectionRepository
from app.extensions.providers import provider_registry


class TransactionService:
    def __init__(
        self,
        connection_repository=ConnectionRepository(),
    ):
        self.connection_repository = connection_repository

    def fetch_transactions(self, user_id, connection_id, page=1, page_size=10):
        connection = self.connection_repository.get_by_id(connection_id)

        if not connection:
            raise NotFoundError(f"Connection with ID {connection_id} not found.")

        if connection.user_id != user_id:
            raise UnauthorizedError("User does not have access to this connection.")

        provider = provider_registry.get(connection.provider)

        if not provider:
            raise NotFoundError(f"No provider registered for {connection.provider}")

        return provider.fetch_transactions(connection, page, page_size)
