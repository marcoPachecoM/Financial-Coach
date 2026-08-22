from datetime import date
from decimal import Decimal

import pandas as pd

from app.categorization.categories import Categories
from app.categorization.normalization import normalize_provider
from app.categorization.service import CategorizerService
from app.connections.models import Connection
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

    def __init__(
        self,
        categorizer: CategorizerService | None = None,
    ) -> None:
        self._categorizer = categorizer or CategorizerService()

    def _load_dataframe(
        self,
        connection: Connection,
    ) -> pd.DataFrame:
        dataframe = pd.read_csv(
            connection.external_id,
            usecols=self.COLUMNS,
        )

        return clean_dataframe(dataframe)

    def fetch_transactions(
        self,
        connection,
        page: int = 1,
        size: int = 10,
        mcc: int | None = None,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> tuple[list[Transaction], int, bool]:
        dataframe = self._load_dataframe(connection)

        dataframe = filter_dataframe(
            dataframe,
            start_date=start_date,
            end_date=end_date,
        )

        if mcc is not None:
            dataframe = dataframe[dataframe["mcc"] == mcc]

        total = len(dataframe)

        offset = (page - 1) * size

        paginated = dataframe.iloc[offset : offset + size]

        transactions = [self._to_transaction(row) for row in paginated.itertuples(index=False)]

        has_more = offset + size < total

        return transactions, total, has_more

    def _to_transaction(self, row) -> Transaction:
        return Transaction(
            id=str(row.id),
            date=row.date,
            amount=Decimal(str(row.amount)),
            mcc=(
                int(row.mcc)
                if row.mcc is not None and pd.notna(row.mcc) and str(row.mcc).isdigit()
                else 0
            ),
            provider_name=str(row.provider_name),
        )

    def get_summary(
        self,
        connection,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> dict:
        dataframe = self._load_dataframe(connection)

        dataframe = filter_dataframe(
            dataframe,
            start_date=start_date,
            end_date=end_date,
        )

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
        group_by: str = "category",
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> list[dict]:
        dataframe = self._load_dataframe(connection)

        dataframe = filter_dataframe(
            dataframe,
            start_date=start_date,
            end_date=end_date,
        )

        if dataframe.empty:
            return []

        if group_by != "category":
            raise ValueError("Category breakdown only supports group_by='category'")

        categories = await self._categorize_dataframe(dataframe)

        dataframe["category"] = categories

        grouped = (
            dataframe.groupby(
                "category",
                dropna=False,
            )
            .agg(
                amount=("amount", "sum"),
                count=("amount", "size"),
            )
            .sort_values(
                "amount",
                ascending=False,
            )
        )

        total_amount = dataframe["amount"].sum()

        return [
            {
                "category": category,
                "amount": Decimal(str(row["amount"])),
                "percentage": Decimal(
                    str((row["amount"] / total_amount) * 100 if total_amount != 0 else 0)
                ),
                "count": int(row["count"]),
            }
            for category, row in grouped.iterrows()
        ]

    async def _categorize_dataframe(
        self,
        dataframe: pd.DataFrame,
    ) -> list[str]:
        unique_transactions = dataframe[["provider_name", "mcc"]].drop_duplicates()

        pairs = [
            (
                normalize_provider(str(row.provider_name)),
                (
                    int(str(row.mcc))
                    if row.mcc is not None and pd.notna(row.mcc) and str(row.mcc).isdigit()
                    else None
                ),
            )
            for row in unique_transactions.itertuples(index=False)
        ]

        categories = await self._categorizer.categorize_many(pairs)

        return [
            categories.get(
                (
                    normalize_provider(str(row.provider_name)),
                    (
                        int(str(row.mcc))
                        if row.mcc is not None and pd.notna(row.mcc) and str(row.mcc).isdigit()
                        else None
                    ),
                ),
                Categories.ERROR,
            )
            for row in dataframe.itertuples(index=False)
        ]


def clean_dataframe(
    dataframe: pd.DataFrame,
) -> pd.DataFrame:
    dataframe = dataframe.copy()

    dataframe["date"] = pd.to_datetime(
        dataframe["date"],
        errors="coerce",
    )

    dataframe["amount"] = pd.to_numeric(
        dataframe["amount"],
        downcast="float",
        errors="coerce",
    )

    dataframe["mcc"] = pd.to_numeric(
        dataframe["mcc"],
        downcast="integer",
        errors="coerce",
    )

    dataframe = dataframe.dropna(
        subset=["date", "amount", "mcc"],
    )

    return dataframe


def filter_dataframe(
    dataframe: pd.DataFrame,
    start_date: date | None = None,
    end_date: date | None = None,
) -> pd.DataFrame:
    if start_date is not None:
        dataframe = dataframe[dataframe["date"].dt.date >= start_date]

    if end_date is not None:
        dataframe = dataframe[dataframe["date"].dt.date <= end_date]

    return dataframe
