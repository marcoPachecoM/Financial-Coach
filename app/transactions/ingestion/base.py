from abc import ABC, abstractmethod
from app.connections.models import Connection
from app.transactions.domain.models import Transaction


class TransactionProvider(ABC):
    def __init__(self):
        pass

    @abstractmethod
    def fetch_transactions(
        self,
        connection: Connection,
        page: int = 1,
        page_size: int = 10,
    ) -> list[Transaction]:
        pass
