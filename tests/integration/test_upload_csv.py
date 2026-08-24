import io
from pathlib import Path
from unittest.mock import patch

from app.connections.models import Connection
from app.extensions.database import db


def test_upload_csv_creates_file_and_connection(client, access_token, app, tmp_path):
    csv_content = """date,amount,mcc,provider_name
2026-08-01,150.00,5411,Supermarket Test
2026-08-02,50.25,5812,Restaurant Test
"""

    with patch("app.transactions.service.ROOT_DIR", tmp_path):
        data = {
            "file": (io.BytesIO(csv_content.encode("utf-8")), "custom_dataset_clean_transactions.csv"),
        }

        response = client.post(
            "/api/v1/transactions/upload-csv",
            headers={"Authorization": f"Bearer {access_token}"},
            data=data,
            content_type="multipart/form-data",
        )

        assert response.status_code == 201
        res_json = response.get_json()
        assert res_json["records_processed"] == 2
        assert "connection_id" in res_json

        connection_id = res_json["connection_id"]

        with app.app_context():
            connection = db.session.get(Connection, connection_id)
            assert connection is not None
            assert connection.name == "custom_dataset Connection"
            assert "mocks" in connection.external_id
            assert connection.external_id.endswith("custom_dataset_clean_transactions.csv")
            assert Path(connection.external_id).exists()
