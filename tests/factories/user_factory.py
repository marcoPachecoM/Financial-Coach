from app.common.passwords import PasswordService
from app.users.models import User, UserRole


def build_user(
    username: str, email: str, role: UserRole, password: str = "StrongPass123!"
) -> User:
    return User(
        username=username,
        email=email,
        role=role,
        is_active=True,
        password_hash=PasswordService.hash_password(password),
    )
