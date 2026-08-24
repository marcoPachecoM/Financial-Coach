import pandas as pd
import pytest

from app.common.exceptions import ValidationError
from app.transactions.cleaner import TransactionCleaner


@pytest.fixture
def cleaner() -> TransactionCleaner:
    return TransactionCleaner()


def test_clean_valid_csv(cleaner):
    csv = """date,amount,mcc,provider_name
2026-08-01,45.50,5693,Lunch
2026-08-02,$120.00,5441,Supermarket
"""

    result = cleaner.clean(csv.encode())

    assert len(result) == 2

    assert result["amount"].tolist() == [45.50, 120.00]

    assert result["mcc"].tolist() == [
        5693,
        5441,
    ]

    assert result["provider_name"].tolist() == [
        "Lunch",
        "Supermarket",
    ]


def test_clean_requires_date_column(cleaner):
    csv = """amount,mcc
100,1234
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


def test_clean_requires_mcc_column(cleaner):
    csv = """date,amount,provider_name
2026-08-01,100,Amazon
"""

    with pytest.raises(
        ValidationError,
        match="CSV is missing required columns",
    ):
        cleaner.clean(csv.encode())


def test_clean_rejects_invalid_date(cleaner):
    csv = """date,amount,mcc,provider_name
not-a-date,100,1234,Supermarket
"""

    with pytest.raises(
        ValidationError,
        match="CSV contains invalid dates",
    ):
        cleaner.clean(csv.encode())


def test_clean_rejects_invalid_amount(cleaner):
    csv = """date,amount,mcc,provider_name
2026-08-01,not-a-number,1243,Supermarket
"""

    with pytest.raises(
        ValidationError,
        match="CSV contains invalid amounts",
    ):
        cleaner.clean(csv.encode())


def test_clean_parses_currency_symbols_and_commas(cleaner):
    csv = """date,amount,provider_name,mcc,
2026-08-01,"$1,250.50",Supermarket,1234,
2026-08-02,"2,500.75",Electronics Store,5678
2026-08-03,"$100",Bookstore,9101
"""

    result = cleaner.clean(csv.encode())

    assert result["amount"].tolist() == [
        1250.50,
        2500.75,
        100.0,
    ]


def test_clean_strips_string_fields(cleaner):
    csv = """date,amount,mcc,provider_name
2026-08-01,100,1234,New shoes
"""

    result = cleaner.clean(csv.encode())

    assert result.loc[0, "provider_name"] == "New shoes"


def test_clean_rejects_empty_csv(cleaner):
    csv = """date,amount,mcc,provider_name
"""
    with pytest.raises(
        ValidationError,
        match="CSV contains no transactions",
    ):
        cleaner.clean(csv.encode())


def test_clean_rejects_header_only_csv(cleaner):
    csv = """date,amount,mcc,provider_name
"""

    with pytest.raises(ValidationError):
        cleaner.clean(csv.encode())


def test_clean_accepts_negative_amounts(cleaner):
    csv = """date,amount,mcc,provider_name
2026-08-01,-199,1234,Shopping
"""

    result = cleaner.clean(csv.encode())

    assert result.loc[0, "amount"] == -199


def test_clean_accepts_decimal_amounts(cleaner):
    csv = """date,amount,mcc,provider_name
2026-08-01,123.456,1234,Shopping
"""

    result = cleaner.clean(csv.encode())

    assert result.loc[0, "amount"] == 123.456


def test_clean_converts_dates_to_datetime(cleaner):
    csv = """date,amount,mcc,provider_name
2026-08-01,100,1234,Shopping
"""

    result = cleaner.clean(csv.encode())

    assert pd.api.types.is_datetime64_any_dtype(result["date"])


def test_clean_keeps_extra_columns(cleaner):
    csv = """date,amount,mcc,provider_name,merchant,foo
2026-08-01,100,1234,Supermarket,Nike,something
"""

    result = cleaner.clean(csv.encode())

    assert "merchant" in result.columns
    assert "foo" in result.columns
    assert result.loc[0, "merchant"] == "Nike"


def test_clean_handles_multiple_rows(cleaner):
    csv = """date,amount,mcc,provider_name
2026-08-01,100,1234,Supermarket
2026-08-02,200,5678,Electronics Store
2026-08-03,300,9101,Bookstore
2026-08-04,400,1234,Supermarket
2026-08-05,500,5678,Electronics Store
"""

    result = cleaner.clean(csv.encode())

    assert len(result) == 5
    assert result["mcc"].tolist() == [
        1234,
        5678,
        9101,
        1234,
        5678,
    ]
