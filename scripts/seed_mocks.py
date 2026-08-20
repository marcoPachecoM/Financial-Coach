from app.transactions.ingestion.csv import CSVTransactionIngestor
from app.transactions.service import TransactionService

# Mapping dataset file names to user IDs from your seed script
MOCK_USER_MAPPING = {
    "mocks/client1_clean_transactions.csv": "user-uuid-1",
    "mocks/client2_clean_transactions.csv": "user-uuid-2",
    "mocks/client3_clean_transactions.csv": "user-uuid-3",
}

def seed_transactions():
    service = TransactionService()
    for file_path, user_id in MOCK_USER_MAPPING.items():
        ingestor = CSVTransactionIngestor(file_path)
        raw_txs = ingestor.parse()
        service.ingest_client_mock_data(user_id=user_id, raw_transactions=raw_txs)

if __name__ == "__main__":
    seed_transactions()