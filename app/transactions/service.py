from datetime import datetime
from pathlib import Path

from werkzeug.datastructures import FileStorage
from werkzeug.utils import secure_filename

from app.categorization.service import CategorizerService
from app.common.exceptions import ForbiddenError, NotFoundError, ValidationError
from app.connections.models import Connection, Status
from app.connections.repository import ConnectionRepository
from app.transactions.cleaner import TransactionCleaner
from app.transactions.ingestion.registry import Providers, provider_registry

ROOT_DIR = Path(__file__).resolve().parents[2]


class TransactionService:
    def __init__(
        self,
        connection_repository: ConnectionRepository | None = None,
        cleaner: TransactionCleaner | None = None,
        categorizer_service: CategorizerService | None = None,
    ) -> None:
        self.connection_repository = connection_repository or ConnectionRepository()
        self.cleaner = cleaner or TransactionCleaner()
        self.categorizer_service = categorizer_service or CategorizerService()

    async def fetch_transactions(
        self,
        user_id: str,
        connection_id: int,
        page: int = 1,
        size: int = 20,
        mcc: int | None = None,
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
            mcc=mcc,
            start_date=start_date,
            end_date=end_date,
        )

        total_pages = (total + size - 1) // size if total else 0
        content = [
            {
                "id": t.id,
                "user_id": connection.user_id,
                "connection_id": connection.id,
                "date": t.date,
                "amount": t.amount,
                "mcc": t.mcc,
                "provider_name": t.provider_name,
                "category": await self.categorizer_service.categorize(t.provider_name, t.mcc),
                "status": "verified",
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

        stem = Path(file.filename).stem
        safe_stem = secure_filename(stem) or "uploaded_dataset"

        output_path = mocks_dir / f"{safe_stem}.csv"
        if output_path.exists():
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            output_path = mocks_dir / f"{safe_stem}_{timestamp}.csv"

        base_name = safe_stem
        if base_name.endswith("_clean_transactions"):
            base_name = base_name[: -len("_clean_transactions")]
        elif base_name.endswith("_transactions"):
            base_name = base_name[: -len("_transactions")]

        if not base_name.strip():
            base_name = "Dataset"

        if not base_name.lower().endswith("connection"):
            connection_name = f"{base_name} Connection"
        else:
            connection_name = base_name

        dataframe.to_csv(
            output_path,
            index=False,
        )

        created_connection = self.connection_repository.create(
            Connection(
                user_id=user_id,
                provider=Providers.CSV,
                external_id=str(output_path.resolve()),
                name=connection_name,
                status=Status.ACTIVE,
            )
        )

        return {
            "records_processed": len(dataframe),
            "connection_id": created_connection.id,
            "connection": {
                "id": created_connection.id,
                "user_id": created_connection.user_id,
                "provider": created_connection.provider,
                "name": created_connection.name,
                "external_id": created_connection.external_id,
                "status": (
                    created_connection.status.value
                    if hasattr(created_connection.status, "value")
                    else str(created_connection.status)
                ),
            },
            "message": "Transactions imported successfully.",
        }
