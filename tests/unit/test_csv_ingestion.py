from decimal import Decimal
from unittest.mock import MagicMock
from app.transactions.ingestion.csv import CSVTransactionProvider
from app.transactions.domain.models import Transaction


def test_csv_transaction_provider_reads_mock_file():
    # 1. Create a dummy Connection pointing to your actual mock file
    mock_connection = MagicMock()
    mock_connection.external_id = (
        "mocks/client1_clean_transactions.csv"  # Adjust path if needed
    )

    # 2. Instantiate provider and fetch first page
    provider = CSVTransactionProvider()
    transactions, total, has_more = provider.fetch_transactions(
        connection=mock_connection, page=1, size=5
    )

    # 3. Assertions
    assert len(transactions) > 0
    first_tx = transactions[0]
    assert isinstance(first_tx, Transaction)
    assert isinstance(total, int)
    assert isinstance(has_more, bool)
    assert isinstance(first_tx.amount, Decimal)
    assert isinstance(first_tx.mcc, int)
    assert first_tx.provider_name != ""
