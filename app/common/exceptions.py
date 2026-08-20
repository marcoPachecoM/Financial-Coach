from http import HTTPStatus


class AppError(Exception):
    code = "APP_ERROR"
    message = "An application error occurred."
    status_code = HTTPStatus.BAD_REQUEST

    def __init__(
        self,
        message: str | None = None,
        code: str | None = None,
    ) -> None:
        self.message = message or self.message
        self.code = code or self.code

        super().__init__(self.message)


class UnauthorizedError(AppError):
    code = "UNAUTHORIZED"
    message = "Authentication is required."
    status_code = HTTPStatus.UNAUTHORIZED


class ForbiddenError(AppError):
    code = "FORBIDDEN"
    message = "You are not allowed to perform this action."
    status_code = HTTPStatus.FORBIDDEN


class ValidationError(AppError):
    code = "VALIDATION_ERROR"
    message = "Invalid request payload."
    status_code = HTTPStatus.BAD_REQUEST


class NotFoundError(AppError):
    code = "NOT_FOUND"
    message = "Resource was not found."
    status_code = HTTPStatus.NOT_FOUND


class ConflictError(AppError):
    code = "CONFLICT"
    message = "Resource conflict."
    status_code = HTTPStatus.CONFLICT


class TooManyRequestsError(AppError):
    code = "RATE_LIMIT_EXCEEDED"
    message = "Too many requests."
    status_code = HTTPStatus.TOO_MANY_REQUESTS
