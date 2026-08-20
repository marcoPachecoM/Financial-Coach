from datetime import date

import pandas as pd

from app.transactions.domain.models import Transaction
from app.transactions.ingestion.base import TransactionProvider


class CSVTransactionProvider(TransactionProvider):
    COLUMNS = [
        "id",
        "date",
        "amount",
        "category",
        "super_category",
    ]

    def fetch_transactions(
        self,
        connection,
        page: int = 1,
        size: int = 10,
        super_category: str | None = None,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> tuple[list[Transaction], int, bool]:
        dataframe = pd.read_csv(
            connection.external_id,
            usecols=self.COLUMNS,
        )

        dataframe["date"] = pd.to_datetime(
            dataframe["date"],
            errors="coerce",
        )

        dataframe = dataframe.dropna(subset=["date"])

        if start_date:
            dataframe = dataframe[dataframe["date"] >= pd.Timestamp(start_date)]

        if end_date:
            dataframe = dataframe[dataframe["date"] <= pd.Timestamp(end_date)]

        if super_category:
            dataframe = dataframe[dataframe["super_category"] == super_category]

        total = len(dataframe)

        offset = (page - 1) * size
        limit = size

        paginated = dataframe.iloc[offset : offset + limit]

        transactions = [self._to_transaction(row) for row in paginated.itertuples(index=False)]

        has_more = offset + limit < total

        return transactions, total, has_more

    @staticmethod
    def _to_transaction(row) -> Transaction:
        return Transaction(
            external_id=str(row.id),
            date=row.date,
            amount=row.amount,
            category=str(row.category),
            super_category=str(row.super_category),
        )
