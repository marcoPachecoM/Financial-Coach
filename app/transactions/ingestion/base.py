from abc import ABC, abstractmethod
from datetime import date

from app.connections.models import Connection
from app.transactions.domain.models import Transaction


class TransactionProvider(ABC):
    @abstractmethod
    def fetch_transactions(
        self,
        connection,
        page: int = 1,
        size: int = 10,
        mcc: int | None = None,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> tuple[list[Transaction], int, bool]: ...

    @abstractmethod
    def _to_transaction(self, row) -> Transaction: ...

    @abstractmethod
    def get_summary(
        self,
        connection: Connection,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> dict: ...

    @abstractmethod
    async def get_category_breakdown(
        self,
        connection,
        group_by: str = "category",
        start_date=None,
        end_date=None,
    ) -> list[dict]: ...
