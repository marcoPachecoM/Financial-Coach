from functools import wraps

from flask_jwt_extended import get_jwt, verify_jwt_in_request

from app.common.exceptions import ForbiddenError


def roles_required(*allowed_roles: str):
    allowed = {role.upper() for role in allowed_roles}

    def decorator(fn):
        @wraps(fn)
        def wrapper(*args, **kwargs):
            verify_jwt_in_request()
            claims = get_jwt()
            role = str(claims.get("role", "")).upper()
            if role not in allowed:
                raise ForbiddenError("Insufficient role permissions.", code="ROLE_FORBIDDEN")
            return fn(*args, **kwargs)

        return wrapper

    return decorator
