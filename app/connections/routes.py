from flask import Blueprint, jsonify
from flask_jwt_extended import get_jwt_identity, jwt_required

from app.connections.schemas import ConnectionSchema
from app.connections.service import ConnectionService

connections_bp = Blueprint(
    "connections",
    __name__,
    url_prefix="/api/v1/connections",
)

_connection_service = ConnectionService()
_connection_schema = ConnectionSchema()


@connections_bp.get("")
@jwt_required()
def get_connections():
    user_id = get_jwt_identity()
    connections = _connection_service.get_user_connections(user_id=user_id)
    return jsonify(_connection_schema.dump(connections, many=True)), 200
