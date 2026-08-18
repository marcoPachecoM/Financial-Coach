from app.transactions.ingestion.base import TransactionProvider
from app.transactions.domain.models import Transaction
import pandas as pd


class CSVTransactionProvider(TransactionProvider):

    def fetch_transactions(
        self,
        connection,
        page: int = 1,
        page_size: int = 10,
    ) -> list[Transaction]:

        records = pd.read_csv(connection.external_id).to_dict(orient="records")

        start = (page - 1) * page_size
        end = start + page_size

        transactions = []

        for record in records[start:end]:
            transactions.append(
                Transaction(
                    external_id=str(record["id"]),
                    date=record["date"],
                    amount=record["amount"],
                    category=record["category"],
                    super_category=record["super_category"],
                )
            )

        return transactions
