from flask import Blueprint, jsonify, request
from flask_jwt_extended import get_jwt_identity, jwt_required

from app.copilot.schemas import (
    ChatBodySchema,
    ChatResponseSchema,
    validate_no_unknown_fields,
)
from app.copilot.service import CopilotService

copilot_bp = Blueprint(
    "copilot",
    __name__,
    url_prefix="/api/v1/copilot",
)

copilot_service = CopilotService()
_chat_body_schema = ChatBodySchema()
_chat_response_schema = ChatResponseSchema()


@copilot_bp.post("/chat")
@jwt_required()
def chat():
    body = request.get_json(silent=True) or {}

    validate_no_unknown_fields(
        _chat_body_schema,
        body,
    )

    body = _chat_body_schema.load(body)

    if not isinstance(body, dict):
        return jsonify({"error": "Invalid request body."}), 400

    result = copilot_service.chat(
        user_id=get_jwt_identity(),
        connection_id=body["connection_id"],
        message=body["message"].strip(),
    )
    return jsonify(_chat_response_schema.dump(result)), 200


@copilot_bp.post("/analyze")
@jwt_required()
def analyze():
    result = copilot_service.analyze(
        user_id=get_jwt_identity(),
    )
    return jsonify(result), 200
