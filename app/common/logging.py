import json
import logging
from datetime import UTC, datetime

from flask import Flask, g, has_request_context, request

SENSITIVE_HEADERS = {"authorization", "cookie", "x-api-key"}


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }

        if has_request_context():
            payload.update(
                {
                    "request_id": g.get("request_id"),
                    "method": request.method,
                    "path": request.path,
                    "remote_addr": request.remote_addr,
                }
            )

        if hasattr(record, "extra") and isinstance(record.extra, dict):
            payload.update(record.extra)

        return json.dumps(payload, default=str)


def configure_logging(app: Flask) -> None:
    root = logging.getLogger()
    root.handlers.clear()

    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root.addHandler(handler)
    root.setLevel(app.config["LOG_LEVEL"])

    @app.after_request
    def log_request(response):
        started = g.get("request_started_at")
        duration_ms = None
        if started is not None:
            duration_ms = (datetime.now(UTC) - started).total_seconds() * 1000

        extra = {
            "status": response.status_code,
            "duration_ms": round(duration_ms, 2) if duration_ms is not None else None,
        }
        logging.getLogger("app.request").info("request_complete", extra={"extra": extra})
        return response


def log_security_event(
    event: str, details: dict | None = None, level: int = logging.WARNING
) -> None:
    safe_details = details or {}
    sanitized = {
        key: value
        for key, value in safe_details.items()
        if str(key).lower() not in SENSITIVE_HEADERS
    }
    logging.getLogger("app.security").log(level, event, extra={"extra": sanitized})
