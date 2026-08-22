from datetime import date

from app.common.exceptions import ForbiddenError, NotFoundError
from app.connections.repository import ConnectionRepository
from app.transactions.ingestion.registry import provider_registry


class AnalyticsService:
    def __init__(
        self,
        connection_repository: ConnectionRepository | None = None,
    ) -> None:
        self.connection_repository = connection_repository or ConnectionRepository()

    def get_summary(
        self,
        user_id: str,
        connection_id: int,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> dict:
        connection = self.connection_repository.get_by_id(connection_id)

        if connection is None:
            raise NotFoundError(f"Connection with ID {connection_id} not found.")

        if connection.user_id != user_id:
            raise ForbiddenError("User does not have access to this connection.")

        provider = provider_registry.get(connection.provider)

        if provider is None:
            raise NotFoundError(f"No provider registered for {connection.provider}.")

        if start_date is not None and end_date is not None and start_date > end_date:
            raise ValueError("start_date cannot be greater than end_date.")

        return provider.get_summary(
            connection=connection,
            start_date=start_date,
            end_date=end_date,
        )

    async def get_category_breakdown(
        self,
        user_id,
        connection_id: int,
        group_by: str = "category",
        start_date=None,
        end_date=None,
    ) -> list[dict]:
        connection = self.connection_repository.get_by_id(connection_id)

        if connection is None:
            raise NotFoundError(f"Connection with ID {connection_id} not found.")

        if connection.user_id != user_id:
            raise ForbiddenError("User does not have access to this connection.")

        provider = provider_registry.get(connection.provider)

        if provider is None:
            raise NotFoundError(f"No provider registered for {connection.provider}.")

        return await provider.get_category_breakdown(
            connection=connection,
            group_by=group_by,
            start_date=start_date,
            end_date=end_date,
        )

    def build_copilot_context(
        self,
        user_id: str,
        connection_id: int,
    ) -> dict:
        connection = self.connection_repository.get_by_id(connection_id)

        if connection is None:
            raise NotFoundError(f"Connection with ID {connection_id} not found.")

        if connection.user_id != user_id:
            raise ForbiddenError("User does not have access to this connection.")

        provider = provider_registry.get(connection.provider)

        if provider is None:
            raise NotFoundError(f"No provider registered for {connection.provider}.")

        summary = provider.get_summary(
            connection=connection,
        )

        category_breakdown = provider.get_category_breakdown(
            connection=connection,
        )

        return {
            "summary": summary,
            "category_breakdown": category_breakdown,
        }
