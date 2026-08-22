import asyncio
from datetime import date
from decimal import Decimal

import pandas as pd

from app.categorization.service import CategorizerService
from app.transactions.domain.models import Transaction
from app.transactions.ingestion.base import TransactionProvider


class CSVTransactionProvider(TransactionProvider):
    COLUMNS = [
        "id",
        "date",
        "amount",
        "mcc",
        "provider_name",
    ]

    def fetch_transactions(
        self,
        connection,
        page=1,
        size=10,
        mcc=None,
        start_date=None,
        end_date=None,
    ) -> tuple[list[Transaction], int, bool]:
        dataframe = pd.read_csv(
            connection.external_id,
            usecols=self.COLUMNS,
        )

        dataframe = clean_dataframe(dataframe, start_date, end_date)

        if mcc is not None:
            dataframe = dataframe[dataframe["mcc"] == mcc]

        total = len(dataframe)

        offset = (page - 1) * size
        limit = size

        paginated = dataframe.iloc[offset : offset + limit]

        transactions = [self._to_transaction(row) for row in paginated.itertuples(index=False)]

        has_more = offset + limit < total

        return transactions, total, has_more

    def _to_transaction(self, row) -> Transaction:
        return Transaction(
            id=str(row.id),
            date=row.date,
            amount=Decimal(str(row.amount)),
            mcc=row.mcc,
            provider_name=row.provider_name,
        )

    def get_summary(
        self,
        connection,
        start_date=None,
        end_date=None,
    ) -> dict:
        dataframe = pd.read_csv(connection.external_id)

        dataframe = clean_dataframe(dataframe, start_date, end_date)

        if dataframe.empty:
            return {
                "total_spent": Decimal("0"),
                "transaction_count": 0,
                "average_transaction": Decimal("0"),
                "largest_transaction": Decimal("0"),
            }

        amounts = dataframe["amount"]

        return {
            "total_spent": Decimal(str(amounts.sum())),
            "transaction_count": int(amounts.count()),
            "average_transaction": Decimal(str(amounts.mean())),
            "largest_transaction": Decimal(str(amounts.max())),
        }

    async def get_category_breakdown(
        self,
        connection,
        group_by="category",
        start_date=None,
        end_date=None,
    ) -> list[dict]:
        dataframe = pd.read_csv(connection.external_id)
        dataframe = clean_dataframe(dataframe, start_date, end_date)

        if dataframe.empty:
            return []

        dataframe["category"] = await asyncio.gather(
            *(categorize_row(row) for _, row in dataframe.iterrows())
        )

        grouped = (
            dataframe.groupby(group_by, dropna=False)
            .agg(
                amount=("amount", "sum"),
                count=("amount", "size"),
            )
            .sort_values("amount", ascending=False)
        )

        total_amount = dataframe["amount"].sum()

        return [
            {
                "category": category,
                "amount": Decimal(str(amount)),
                "percentage": Decimal(
                    str((amount / total_amount) * 100 if total_amount != 0 else 0)
                ),
                "count": int(count),
            }
            for category, row in grouped.iterrows()
            for amount, count in [(row["amount"], row["count"])]
        ]


async def categorize_row(row):
    return await CategorizerService.categorize(
        row["provider_name"],
        row["mcc"],
    )


def clean_dataframe(
    dataframe: pd.DataFrame,
    start_date: date | None = None,
    end_date: date | None = None,
) -> pd.DataFrame:
    dataframe["date"] = pd.to_datetime(
        dataframe["date"],
        errors="coerce",
    )

    dataframe = dataframe.dropna(subset=["date"])

    dataframe["amount"] = pd.to_numeric(
        dataframe["amount"],
        errors="coerce",
        downcast="float",
    )

    dataframe = dataframe.dropna(subset=["amount"])

    dataframe["mcc"] = pd.to_numeric(
        dataframe["mcc"],
        errors="coerce",
        downcast="integer",
    )

    dataframe = dataframe.dropna(subset=["mcc"])

    if start_date is not None:
        dataframe = dataframe[dataframe["date"].dt.date >= start_date]

    if end_date is not None:
        dataframe = dataframe[dataframe["date"].dt.date <= end_date]

    return dataframe
