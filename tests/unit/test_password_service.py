import pytest

from app.common.exceptions import ValidationError
from app.common.passwords import PasswordService


def test_password_hash_and_verify():
    hashed = PasswordService.hash_password("StrongPass123!")
    assert hashed != "StrongPass123!"
    assert PasswordService.verify_password(hashed, "StrongPass123!") is True
    assert PasswordService.verify_password(hashed, "wrong") is False


def test_password_strength_validation():
    with pytest.raises(ValidationError):
        PasswordService.validate_password_strength("short")

    PasswordService.validate_password_strength("LongEnough123!")
