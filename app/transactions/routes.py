from flask import Blueprint, jsonify, request
from flask_jwt_extended import get_jwt_identity, jwt_required

from app.auth.decorators import roles_required

from app.transactions.service import TransactionService

transactions_bp = Blueprint("transactions", __name__, url_prefix="/api/v1/transactions")

transaction_service = TransactionService()


@transactions_bp.get("")
@jwt_required()
def get_transactions():
    params = request.args
    connection_id = params.get("connection_id")
    if not connection_id:
        return jsonify({"status": "error", "message": "connection_id is required"}), 400
    page = int(params.get("page", 1))
    page_size = int(params.get("page_size", 10))
    current_user_id = get_jwt_identity()

    transactions = transaction_service.fetch_transactions(
        user_id=current_user_id,
        connection_id=connection_id,
        page=page,
        page_size=page_size,
    )

    return jsonify({"status": "ok", "transactions": transactions}), 200
