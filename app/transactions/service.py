from datetime import datetime
from pathlib import Path

from werkzeug.datastructures import FileStorage

from app.common.exceptions import ForbiddenError, NotFoundError, ValidationError
from app.connections.models import Connection
from app.connections.repository import ConnectionRepository
from app.transactions.cleaner import TransactionCleaner
from app.transactions.ingestion.registry import Providers, provider_registry

ROOT_DIR = Path(__file__).resolve().parents[2]


class TransactionService:

    def __init__(
        self,
        connection_repository: ConnectionRepository | None = None,
        cleaner: TransactionCleaner | None = None,
    ) -> None:
        self.connection_repository = connection_repository or ConnectionRepository()
        self.cleaner = cleaner or TransactionCleaner()

    def fetch_transactions(
        self,
        user_id: str,
        connection_id: int,
        page: int = 1,
        size: int = 20,
        super_category: str | None = None,
        start_date=None,
        end_date=None,
    ) -> dict:
        connection = self.connection_repository.get_by_id(connection_id)

        if connection is None:
            raise NotFoundError(f"Connection with ID {connection_id} not found.")

        if connection.user_id != user_id:
            raise ForbiddenError("User does not have access to this connection.")

        provider = provider_registry.get(connection.provider)

        if provider is None:
            raise NotFoundError(f"No provider registered for {connection.provider}.")

        transactions, total, has_more = provider.fetch_transactions(
            connection=connection,
            page=page,
            size=size,
            super_category=super_category,
            start_date=start_date,
            end_date=end_date,
        )

        total_pages = (total + size - 1) // size if total else 0
        content = [
            {
                "id": t.external_id,
                "date": t.date,
                "amount": t.amount,
                "category": t.category,
                "super_category": t.super_category,
            }
            for t in transactions
        ]

        return {
            "content": content,
            "page": page,
            "size": len(content),
            "total_elements": total,
            "total_pages": total_pages,
            "has_more": has_more,
        }

    def upload_csv(
        self,
        user_id: str,
        file: FileStorage,
    ) -> dict:
        if not file.filename:
            raise ValidationError("File is required.")

        if not file.filename.lower().endswith(".csv"):
            raise ValidationError("Only CSV files are supported.")

        dataframe = self.cleaner.clean(file.read())

        mocks_dir = ROOT_DIR / "mocks"

        mocks_dir.mkdir(
            parents=True,
            exist_ok=True,
        )

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

        output_path = mocks_dir / f"{user_id}_{timestamp}_clean_transactions.csv"

        dataframe.to_csv(
            output_path,
            index=False,
        )

        self.connection_repository.create(
            Connection(
                user_id=user_id,
                provider=Providers.CSV,
                external_id=str(output_path),
                name=f"CSV Upload - {timestamp}",
            )
        )

        return {
            "records_processed": len(dataframe),
            "message": "Transactions imported successfully.",
        }
