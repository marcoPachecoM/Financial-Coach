import json
import logging
from datetime import UTC, datetime

from flask import Flask, g, has_request_context, request

SENSITIVE_HEADERS = {
    "authorization",
    "cookie",
    "x-api-key",
}


RESET = "\033[0m"
DIM = "\033[2m"
BOLD = "\033[1m"

COLORS = {
    "DEBUG": "\033[36m",  # cyan
    "INFO": "\033[32m",  # green
    "WARNING": "\033[33m",  # yellow
    "ERROR": "\033[31m",  # red
    "CRITICAL": "\033[35m",  # magenta
}


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

        record_extra = getattr(record, "extra", None)

        if isinstance(record_extra, dict):
            payload.update(record_extra)

        return json.dumps(payload, default=str)


class ConsoleFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        timestamp = datetime.fromtimestamp(
            record.created,
        ).strftime("%Y-%m-%d %H:%M:%S")

        level = record.levelname
        level_color = COLORS.get(level, "")

        logger = record.name

        message = record.getMessage()

        request_info = ""

        if has_request_context():
            method = request.method
            path = request.path

            request_info = f" {DIM}{method} {path}{RESET}"

        return (
            f"{DIM}{timestamp}{RESET} "
            f"{level_color}{BOLD}{level:<8}{RESET} "
            f"{DIM}[{logger}]{RESET}"
            f"{request_info} "
            f"{message}"
        )


def configure_logging(app: Flask) -> None:
    root = logging.getLogger()

    root.handlers.clear()

    handler = logging.StreamHandler()

    if app.debug:
        handler.setFormatter(ConsoleFormatter())
    else:
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
            "duration_ms": (round(duration_ms, 2) if duration_ms is not None else None),
        }

        logging.getLogger("app.request").info(
            "request_complete",
            extra={"extra": extra},
        )

        return response


def log_security_event(
    event: str,
    details: dict | None = None,
    level: int = logging.WARNING,
) -> None:
    safe_details = details or {}

    sanitized = {
        key: value
        for key, value in safe_details.items()
        if str(key).lower() not in SENSITIVE_HEADERS
    }

    logging.getLogger("app.security").log(
        level,
        event,
        extra={"extra": sanitized},
    )
