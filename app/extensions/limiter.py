from flask import request
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address

limiter = Limiter(
    key_func=get_remote_address,
    default_limits=["5000 per hour", "1000 per minute"],
    storage_uri="memory://",
)


@limiter.request_filter
def exempt_options():
    return request.method == "OPTIONS"
