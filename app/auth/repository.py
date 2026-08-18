from datetime import UTC, datetime

from sqlalchemy import select

from app.extensions.database import db
from app.users.models import RefreshToken


class RefreshTokenRepository:
    @staticmethod
    def create(jti: str, user_id: int, expires_at: datetime) -> RefreshToken:
        token = RefreshToken(jti=jti, user_id=user_id, expires_at=expires_at)
        db.session.add(token)
        db.session.commit()
        return token

    @staticmethod
    def get_by_jti(jti: str) -> RefreshToken | None:
        stmt = select(RefreshToken).where(RefreshToken.jti == jti)
        return db.session.scalar(stmt)

    @staticmethod
    def revoke(token: RefreshToken, replaced_by_jti: str | None = None) -> None:
        token.is_revoked = True
        token.revoked_at = datetime.now(UTC)
        if replaced_by_jti:
            token.replaced_by_jti = replaced_by_jti
        db.session.commit()

    @staticmethod
    def mark_reuse(token: RefreshToken) -> None:
        token.reuse_detected_at = datetime.now(UTC)
        token.is_revoked = True
        token.revoked_at = token.reuse_detected_at
        db.session.commit()
