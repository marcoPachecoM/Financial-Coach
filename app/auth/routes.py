from flask import Blueprint, jsonify, request
from flask_jwt_extended import get_jwt, get_jwt_identity, jwt_required

from app.auth.decorators import roles_required
from app.auth.schemas import (
    LoginSchema,
    UserOutputSchema,
    validate_no_unknown_fields,
)
from app.auth.service import AuthService
from app.common.exceptions import UnauthorizedError
from app.extensions.limiter import limiter

auth_bp = Blueprint("auth", __name__, url_prefix="/api/v1/auth")
health_bp = Blueprint("health", __name__, url_prefix="/api/v1/health")

_auth_service = AuthService()
_login_schema = LoginSchema()
_user_output_schema = UserOutputSchema()


@auth_bp.post("/login")
@limiter.limit("5 per minute")
def login():
    payload = request.get_json(silent=True) or {}
    validate_no_unknown_fields(_login_schema, payload)
    data = _login_schema.load(payload)

    if not isinstance(data, dict):
        raise UnauthorizedError("Invalid credentials.", code="INVALID_CREDENTIALS")

    user = _auth_service.authenticate(data.get("email", ""), data.get("password", ""))
    tokens = _auth_service.issue_tokens(user)

    response_payload = {
        "access_token": tokens["access_token"],
        "refresh_token": tokens["refresh_token"],
    }
    return jsonify(response_payload), 200


@auth_bp.post("/refresh")
@limiter.limit("10 per minute")
@jwt_required(refresh=True)
def refresh():
    tokens = _auth_service.refresh_tokens(get_jwt())
    return jsonify(tokens), 200


@auth_bp.post("/logout")
@limiter.limit("20 per minute")
@jwt_required(refresh=True)
def logout():
    _auth_service.revoke_refresh_token(get_jwt())
    return jsonify({"message": "Logged out."}), 200


@auth_bp.get("/me")
@jwt_required()
def me():
    user_id = get_jwt_identity()
    if user_id is None:
        raise UnauthorizedError("Invalid authentication token.")

    user = _auth_service.user_repository.get_by_public_id(str(user_id))
    if user is None or not user.is_active:
        raise UnauthorizedError("Invalid credentials.", code="INVALID_CREDENTIALS")

    return jsonify({"user": _user_output_schema.dump(user)}), 200


@health_bp.get("/public")
def health_public():
    return jsonify({"status": "ok"}), 200


@health_bp.get("/protected")
@jwt_required()
def health_protected():
    claims = get_jwt()
    return jsonify({"status": "ok", "role": claims.get("role")}), 200


@health_bp.get("/admin")
@roles_required("ADMIN")
def health_admin():
    return jsonify({"status": "ok", "scope": "admin"}), 200
