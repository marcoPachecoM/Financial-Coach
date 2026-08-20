from abc import ABC, abstractmethod
from datetime import date

from app.connections.models import Connection
from app.transactions.domain.models import Transaction


class TransactionProvider(ABC):
    @abstractmethod
    def fetch_transactions(
        self,
        connection: Connection,
        page: int = 1,
        size: int = 10,
        super_category: str | None = None,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> tuple[list[Transaction], int, bool]: ...
