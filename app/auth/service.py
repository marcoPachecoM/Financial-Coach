from datetime import UTC, datetime
from uuid import UUID

from flask import current_app
from flask_jwt_extended import create_access_token, create_refresh_token, decode_token

from app.auth.repository import RefreshTokenRepository
from app.common.exceptions import UnauthorizedError
from app.common.logging import log_security_event
from app.common.passwords import PasswordService
from app.users.models import User
from app.users.repository import UserRepository


class AuthService:
    def __init__(
        self,
        user_repository: UserRepository | None = None,
        refresh_repository: RefreshTokenRepository | None = None,
    ) -> None:
        self.user_repository = user_repository or UserRepository()
        self.refresh_repository = refresh_repository or RefreshTokenRepository()

    def authenticate(self, email: str, password: str) -> User:

        user = self.user_repository.get_by_email(email)
        if user is None:
            log_security_event(
                "auth_failed", {"reason": "invalid_credentials", "email": email}
            )
            raise UnauthorizedError("Invalid credentials.", code="INVALID_CREDENTIALS")

        if not user.is_active:
            log_security_event(
                "auth_failed", {"reason": "inactive_user", "user_id": user.public_id}
            )
            raise UnauthorizedError("Invalid credentials.", code="INVALID_CREDENTIALS")

        if not PasswordService.verify_password(user.password_hash, password):
            log_security_event(
                "auth_failed",
                {"reason": "invalid_credentials", "user_id": user.public_id},
            )
            raise UnauthorizedError("Invalid credentials.", code="INVALID_CREDENTIALS")

        log_security_event("auth_success", {"user_id": user.public_id}, level=20)
        return user

    def _additional_claims(self, user: User, token_type: str) -> dict[str, str]:
        return {
            "role": user.role.value,
            "type": token_type,
            "iss": current_app.config["JWT_ISSUER"],
            "aud": current_app.config["JWT_AUDIENCE"],
        }

    def issue_tokens(self, user: User) -> dict[str, str]:
        access_token = create_access_token(
            identity=user.public_id,
            additional_claims=self._additional_claims(user, "access"),
        )
        refresh_token = create_refresh_token(
            identity=user.public_id,
            additional_claims=self._additional_claims(user, "refresh"),
        )

        refresh_payload = decode_token(refresh_token, allow_expired=False)
        refresh_jti = str(refresh_payload["jti"])
        refresh_exp = datetime.fromtimestamp(float(refresh_payload["exp"]), tz=UTC)
        self.refresh_repository.create(
            jti=refresh_jti, user_id=user.id, expires_at=refresh_exp
        )

        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
        }

    def refresh_tokens(self, refresh_token: str) -> dict[str, str]:
        payload = decode_token(refresh_token, allow_expired=False)

        if payload.get("type") != "refresh":
            raise UnauthorizedError("Invalid token type.", code="INVALID_TOKEN_TYPE")

        user_public_id = str(payload.get("sub", ""))
        old_jti = str(payload.get("jti", ""))

        if not old_jti or not user_public_id:
            raise UnauthorizedError(
                "Invalid token claims.", code="INVALID_TOKEN_CLAIMS"
            )

        user = self.user_repository.get_by_public_id(user_public_id)
        if user is None or not user.is_active:
            raise UnauthorizedError("Invalid credentials.", code="INVALID_CREDENTIALS")

        token_state = self.refresh_repository.get_by_jti(old_jti)
        if token_state is None:
            log_security_event(
                "refresh_failed", {"reason": "unknown_jti", "user_id": user_public_id}
            )
            raise UnauthorizedError(
                "Invalid refresh token.", code="INVALID_REFRESH_TOKEN"
            )

        if token_state.is_revoked:
            self.refresh_repository.mark_reuse(token_state)
            log_security_event(
                "refresh_reuse_detected", {"jti": old_jti, "user_id": user_public_id}
            )
            raise UnauthorizedError(
                "Refresh token reuse detected.", code="REFRESH_TOKEN_REUSE"
            )

        new_tokens = self.issue_tokens(user)
        new_payload = decode_token(new_tokens["refresh_token"], allow_expired=False)
        new_jti = str(new_payload["jti"])

        self.refresh_repository.revoke(token_state, replaced_by_jti=new_jti)
        return new_tokens

    def revoke_refresh_token(self, refresh_token: str) -> None:
        payload = decode_token(refresh_token, allow_expired=True)
        jti = str(payload.get("jti", ""))
        if not jti:
            raise UnauthorizedError(
                "Invalid refresh token.", code="INVALID_REFRESH_TOKEN"
            )

        token_state = self.refresh_repository.get_by_jti(jti)
        if token_state and not token_state.is_revoked:
            self.refresh_repository.revoke(token_state)

    def is_refresh_token_revoked(self, jti: str) -> bool:
        token_state = self.refresh_repository.get_by_jti(jti)
        if token_state is None:
            return True

        if token_state.is_revoked:
            return True

        if token_state.expires_at <= datetime.now(UTC):
            return True

        return False

    @staticmethod
    def parse_user_uuid(user_id: str) -> UUID:
        return UUID(user_id)
