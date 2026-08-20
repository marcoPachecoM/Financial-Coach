from io import BytesIO

import pandas as pd

from app.common.exceptions import ValidationError


class TransactionCleaner:
    REQUIRED_COLUMNS = {
        "date",
        "amount",
        "category",
    }

    CATEGORY_MAP = {
        "Eating Places and Restaurants": "Food & Dining",
        "Grocery Stores": "Food & Dining",
        "Transportation": "Transportation",
        "Gas Stations": "Transportation",
        "Entertainment": "Entertainment",
        "Shopping": "Shopping",
        "Utilities": "Bills & Utilities",
        "Rent": "Housing",
    }

    def clean(self, file_bytes: bytes) -> pd.DataFrame:
        dataframe = pd.read_csv(BytesIO(file_bytes))

        self._validate_columns(dataframe)

        dataframe = self._clean_dates(dataframe)
        dataframe = self._clean_amounts(dataframe)
        dataframe = self._clean_strings(dataframe)
        dataframe = self._normalize_currency(dataframe)
        dataframe = self._add_super_category(dataframe)

        self._validate_result(dataframe)

        return dataframe

    def _validate_columns(
        self,
        dataframe: pd.DataFrame,
    ) -> None:
        missing = self.REQUIRED_COLUMNS - set(dataframe.columns)

        if missing:
            raise ValidationError(f"CSV is missing required columns: {', '.join(missing)}.")

    def _clean_dates(
        self,
        dataframe: pd.DataFrame,
    ) -> pd.DataFrame:
        dataframe["date"] = pd.to_datetime(
            dataframe["date"],
            errors="coerce",
        )

        if dataframe["date"].isna().any():
            raise ValidationError("CSV contains invalid dates.")

        return dataframe

    def _clean_amounts(
        self,
        dataframe: pd.DataFrame,
    ) -> pd.DataFrame:
        dataframe["amount"] = (
            dataframe["amount"]
            .astype(str)
            .str.replace("$", "", regex=False)
            .str.replace(",", "", regex=False)
            .str.strip()
        )

        dataframe["amount"] = pd.to_numeric(
            dataframe["amount"],
            errors="coerce",
        )

        if dataframe["amount"].isna().any():
            raise ValidationError("CSV contains invalid amounts.")

        return dataframe

    def _clean_strings(
        self,
        dataframe: pd.DataFrame,
    ) -> pd.DataFrame:
        columns = [
            "description",
            "category",
        ]

        for column in columns:
            if column in dataframe.columns:
                dataframe[column] = dataframe[column].astype(str).str.strip()

        return dataframe

    def _normalize_currency(
        self,
        dataframe: pd.DataFrame,
    ) -> pd.DataFrame:
        if "currency" in dataframe.columns:
            dataframe["currency"] = dataframe["currency"].str.upper()

        return dataframe

    def _add_super_category(
        self,
        dataframe: pd.DataFrame,
    ) -> pd.DataFrame:
        if "super_category" not in dataframe.columns:
            dataframe["super_category"] = (
                dataframe["category"].map(self.CATEGORY_MAP).fillna("Other")
            )
        return dataframe

    def _validate_result(
        self,
        dataframe: pd.DataFrame,
    ) -> None:
        if dataframe.empty:
            raise ValidationError("CSV contains no transactions.")
