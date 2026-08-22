import logging

from flask import Flask
from flask_jwt_extended.exceptions import JWTExtendedException
from flask_limiter.errors import RateLimitExceeded
from marshmallow import ValidationError as MarshmallowValidationError
from werkzeug.exceptions import HTTPException

from app.common.exceptions import AppError
from app.common.responses import error_response


def register_error_handlers(app: Flask) -> None:
    @app.errorhandler(AppError)
    def handle_app_error(error: AppError):
        return error_response(error.code, error.message, int(error.status_code))

    @app.errorhandler(MarshmallowValidationError)
    def handle_validation_error(error: MarshmallowValidationError):
        return error_response(
            "VALIDATION_ERROR",
            (error.messages if isinstance(error.messages, dict) else "Validation error."),
            400,
        )

    @app.errorhandler(RateLimitExceeded)
    def handle_rate_limit_error(_: RateLimitExceeded):
        return error_response("RATE_LIMIT_EXCEEDED", "Too many requests.", 429)

    @app.errorhandler(JWTExtendedException)
    def handle_jwt_extended(_: JWTExtendedException):
        return error_response("UNAUTHORIZED", "Invalid authentication token.", 401)

    @app.errorhandler(HTTPException)
    def handle_http_exception(error: HTTPException):
        message = error.description if isinstance(error.description, str) else "HTTP error."
        return error_response("HTTP_ERROR", message, error.code or 400)

    @app.errorhandler(Exception)
    def handle_unexpected(error: Exception):
        logging.getLogger(__name__).exception(f"unhandled_exception {error}", exc_info=error)
        return error_response("INTERNAL_SERVER_ERROR", "Unexpected server error.", 500)
