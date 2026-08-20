import pandas as pd
import pytest

from app.common.exceptions import ValidationError
from app.transactions.cleaner import TransactionCleaner


@pytest.fixture
def cleaner() -> TransactionCleaner:
    return TransactionCleaner()


def test_clean_valid_csv(cleaner):
    csv = """date,amount,category,description,currency
2026-08-01,45.50,Eating Places and Restaurants,Lunch,usd
2026-08-02,$120.00,Grocery Stores,Supermarket,MXN
"""

    result = cleaner.clean(csv.encode())

    assert len(result) == 2

    assert result["amount"].tolist() == [45.50, 120.00]

    assert result["category"].tolist() == [
        "Eating Places and Restaurants",
        "Grocery Stores",
    ]

    assert result["super_category"].tolist() == [
        "Food & Dining",
        "Food & Dining",
    ]

    assert result["currency"].tolist() == [
        "USD",
        "MXN",
    ]


def test_clean_requires_date_column(cleaner):
    csv = """amount,category
100,Shopping
"""

    with pytest.raises(
        ValidationError,
        match="CSV is missing required columns",
    ):
        cleaner.clean(csv.encode())


def test_clean_requires_amount_column(cleaner):
    csv = """date,category
2026-08-01,Shopping
"""

    with pytest.raises(
        ValidationError,
        match="CSV is missing required columns",
    ):
        cleaner.clean(csv.encode())


def test_clean_requires_category_column(cleaner):
    csv = """date,amount
2026-08-01,100
"""

    with pytest.raises(
        ValidationError,
        match="CSV is missing required columns",
    ):
        cleaner.clean(csv.encode())


def test_clean_rejects_invalid_date(cleaner):
    csv = """date,amount,category
not-a-date,100,Shopping
"""

    with pytest.raises(
        ValidationError,
        match="CSV contains invalid dates",
    ):
        cleaner.clean(csv.encode())


def test_clean_rejects_invalid_amount(cleaner):
    csv = """date,amount,category
2026-08-01,not-a-number,Shopping
"""

    with pytest.raises(
        ValidationError,
        match="CSV contains invalid amounts",
    ):
        cleaner.clean(csv.encode())


def test_clean_parses_currency_symbols_and_commas(cleaner):
    csv = """date,amount,category
2026-08-01,"$1,250.50",Shopping
2026-08-02,"2,500.75",Shopping
2026-08-03,"$100",Shopping
"""

    result = cleaner.clean(csv.encode())

    assert result["amount"].tolist() == [
        1250.50,
        2500.75,
        100.0,
    ]


def test_clean_strips_string_fields(cleaner):
    csv = """date,amount,category,description
2026-08-01,100,  Shopping  ,  New shoes
"""

    result = cleaner.clean(csv.encode())

    assert result.loc[0, "category"] == "Shopping"
    assert result.loc[0, "description"] == "New shoes"


def test_clean_normalizes_currency_to_uppercase(cleaner):
    csv = """date,amount,category,currency
2026-08-01,100,Shopping,usd
2026-08-02,200,Shopping,mxn
2026-08-03,300,Shopping,eur
"""

    result = cleaner.clean(csv.encode())

    assert result["currency"].tolist() == [
        "USD",
        "MXN",
        "EUR",
    ]


def test_clean_adds_super_category(cleaner):
    csv = """date,amount,category
2026-08-01,100,Eating Places and Restaurants
2026-08-02,200,Grocery Stores
2026-08-03,300,Gas Stations
2026-08-04,400,Rent
"""

    result = cleaner.clean(csv.encode())

    assert result["super_category"].tolist() == [
        "Food & Dining",
        "Food & Dining",
        "Transportation",
        "Housing",
    ]


def test_clean_unknown_category_becomes_other(cleaner):
    csv = """date,amount,category
2026-08-01,100,Something Completely Unknown
"""

    result = cleaner.clean(csv.encode())

    assert result.loc[0, "super_category"] == "Other"


def test_clean_preserves_existing_super_category(cleaner):
    csv = """date,amount,category,super_category
2026-08-01,100,Eating Places and Restaurants,Custom Category
"""

    result = cleaner.clean(csv.encode())

    assert result.loc[0, "super_category"] == "Custom Category"


def test_clean_rejects_empty_csv(cleaner):
    csv = """date,amount,category
"""

    with pytest.raises(
        ValidationError,
        match="CSV contains no transactions",
    ):
        cleaner.clean(csv.encode())


def test_clean_rejects_header_only_csv(cleaner):
    csv = """date,amount,category
"""

    with pytest.raises(ValidationError):
        cleaner.clean(csv.encode())


def test_clean_accepts_negative_amounts(cleaner):
    csv = """date,amount,category
2026-08-01,-100,Shopping
"""

    result = cleaner.clean(csv.encode())

    assert result.loc[0, "amount"] == -100


def test_clean_accepts_decimal_amounts(cleaner):
    csv = """date,amount,category
2026-08-01,123.456,Shopping
"""

    result = cleaner.clean(csv.encode())

    assert result.loc[0, "amount"] == 123.456


def test_clean_converts_dates_to_datetime(cleaner):
    csv = """date,amount,category
2026-08-01,100,Shopping
"""

    result = cleaner.clean(csv.encode())

    assert pd.api.types.is_datetime64_any_dtype(result["date"])


def test_clean_keeps_extra_columns(cleaner):
    csv = """date,amount,category,merchant,foo
2026-08-01,100,Shopping,Nike,something
"""

    result = cleaner.clean(csv.encode())

    assert "merchant" in result.columns
    assert "foo" in result.columns
    assert result.loc[0, "merchant"] == "Nike"


def test_clean_handles_multiple_rows(cleaner):
    csv = """date,amount,category
2026-08-01,100,Shopping
2026-08-02,200,Entertainment
2026-08-03,300,Gas Stations
2026-08-04,400,Rent
2026-08-05,500,Utilities
"""

    result = cleaner.clean(csv.encode())

    assert len(result) == 5
    assert result["super_category"].tolist() == [
        "Shopping",
        "Entertainment",
        "Transportation",
        "Housing",
        "Bills & Utilities",
    ]
