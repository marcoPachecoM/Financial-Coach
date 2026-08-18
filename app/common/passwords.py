from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError

from app.common.exceptions import ValidationError

_password_hasher = PasswordHasher()


class PasswordService:
    @staticmethod
    def hash_password(password: str) -> str:
        return _password_hasher.hash(password)

    @staticmethod
    def verify_password(password_hash: str, candidate_password: str) -> bool:
        try:
            return _password_hasher.verify(password_hash, candidate_password)
        except VerifyMismatchError:
            return False

    @staticmethod
    def validate_password_strength(password: str) -> None:
        if len(password) < 10:
            raise ValidationError("Password must be at least 10 characters long.")
