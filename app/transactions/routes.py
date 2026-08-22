from flask import Blueprint, jsonify, request
from flask_jwt_extended import get_jwt_identity, jwt_required

from app.common.exceptions import ValidationError
from app.transactions.schemas import (
    PaginatedTransactionSchema,
    TransactionQuerySchema,
    validate_no_unknown_fields,
)
from app.transactions.service import TransactionService

transactions_bp = Blueprint(
    "transactions",
    __name__,
    url_prefix="/api/v1/transactions",
)

_transaction_service = TransactionService()
_transaction_query_schema = TransactionQuerySchema()
_paginated_transaction_schema = PaginatedTransactionSchema()


@transactions_bp.get("")
@jwt_required()
async def get_transactions():
    payload = request.args.to_dict()
    validate_no_unknown_fields(_transaction_query_schema, payload)
    params = _transaction_query_schema.load(payload)

    if not isinstance(params, dict):
        raise ValidationError("Invalid query parameters.")

    result = await _transaction_service.fetch_transactions(
        user_id=get_jwt_identity(),
        **params,
    )

    return jsonify(_paginated_transaction_schema.dump(result)), 200


@transactions_bp.post("/upload-csv")
@jwt_required()
def upload_csv():
    file = request.files.get("file")

    if file is None:
        raise ValidationError("file is required.")

    result = _transaction_service.upload_csv(
        user_id=get_jwt_identity(),
        file=file,
    )

    return jsonify(result), 201
