from flask import Flask
from flask_cors import CORS
from flask_jwt_extended.exceptions import JWTExtendedException

from app.api import register_blueprints
from app.auth.service import AuthService
from app.common.error_handlers import register_error_handlers
from app.common.logging import configure_logging, log_security_event
from app.common.responses import error_response
from app.common.security import register_security_middleware
from app.config import ConfigError, get_config
from app.config.settings import config_to_dict
from app.extensions import Providers, db, jwt, limiter, migrate, provider_registry
from app.transactions.ingestion.csv import CSVTransactionProvider


def _register_jwt_callbacks(app: Flask) -> None:
    auth_service = AuthService()

    @jwt.token_in_blocklist_loader
    def check_if_token_revoked(_jwt_header, jwt_payload):
        if jwt_payload.get("type") != "refresh":
            return False
        jti = str(jwt_payload.get("jti", ""))
        return auth_service.is_refresh_token_revoked(jti)

    @jwt.invalid_token_loader
    def invalid_token_callback(_reason: str):
        log_security_event("jwt_invalid")
        return error_response("INVALID_TOKEN", "Invalid authentication token.", 401)

    @jwt.unauthorized_loader
    def missing_token_callback(_reason: str):
        return error_response("UNAUTHORIZED", "Authentication is required.", 401)

    @jwt.expired_token_loader
    def expired_token_callback(_jwt_header, _jwt_payload):
        return error_response("TOKEN_EXPIRED", "Authentication token expired.", 401)

    @jwt.revoked_token_loader
    def revoked_token_callback(_jwt_header, _jwt_payload):
        log_security_event("jwt_revoked")
        return error_response("TOKEN_REVOKED", "Authentication token revoked.", 401)

    @jwt.token_verification_failed_loader
    def verification_failed_callback(_jwt_header, _jwt_payload):
        log_security_event("jwt_verification_failed")
        return error_response("INVALID_TOKEN", "Invalid authentication token.", 401)

    @jwt.token_verification_loader
    def custom_token_verification(_jwt_header, jwt_payload):
        required_claims = app.config["JWT_REQUIRED_CLAIMS"]
        missing_claims = [claim for claim in required_claims if claim not in jwt_payload]
        if missing_claims:
            return False

        token_type = jwt_payload.get("type")
        if token_type not in {"access", "refresh"}:
            return False

        return True

    @jwt.needs_fresh_token_loader
    def fresh_token_required(_jwt_header, _jwt_payload):
        return error_response("FRESH_TOKEN_REQUIRED", "Fresh token required.", 401)

    @app.errorhandler(JWTExtendedException)
    def handle_jwt_exception(_: JWTExtendedException):
        return error_response("INVALID_TOKEN", "Invalid authentication token.", 401)


def create_app(env_name: str | None = None) -> Flask:
    app = Flask(__name__)

    config_class = get_config(env_name)
    config_class.validate()
    app.config.from_mapping(config_to_dict(config_class))

    if app.config["CORS_SUPPORTS_CREDENTIALS"] and "*" in app.config["CORS_ALLOWED_ORIGINS"]:
        raise ConfigError("Wildcard CORS origins are forbidden when credentials are enabled.")

    db.init_app(app)
    migrate.init_app(app, db)
    jwt.init_app(app)
    limiter.init_app(app)
    provider_registry.register(Providers.CSV, CSVTransactionProvider())

    CORS(
        app,
        resources={r"/api/*": {"origins": app.config["CORS_ALLOWED_ORIGINS"]}},
        supports_credentials=app.config["CORS_SUPPORTS_CREDENTIALS"],
    )

    configure_logging(app)
    register_security_middleware(app)
    _register_jwt_callbacks(app)
    register_error_handlers(app)
    register_blueprints(app)

    @app.get("/api/v1/health")
    def healthcheck():
        return {"status": "ok", "environment": app.config["ENV_NAME"]}, 200

    return app
