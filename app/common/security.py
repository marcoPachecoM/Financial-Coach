import uuid
from datetime import UTC, datetime

from flask import Flask, g, request

SECURITY_HEADERS = {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "geolocation=(), microphone=(), camera=()",
    "Cache-Control": "no-store",
    "Pragma": "no-cache",
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'; base-uri 'none';",
}


def register_security_middleware(app: Flask) -> None:
    @app.before_request
    def attach_request_context() -> None:
        g.request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        g.request_started_at = datetime.now(UTC)

    @app.after_request
    def apply_security_headers(response):
        for key, value in SECURITY_HEADERS.items():
            response.headers.setdefault(key, value)

        if request.is_secure:
            response.headers.setdefault(
                "Strict-Transport-Security",
                "max-age=63072000; includeSubDomains; preload",
            )

        response.headers.setdefault("X-Request-ID", g.get("request_id", ""))
        return response
