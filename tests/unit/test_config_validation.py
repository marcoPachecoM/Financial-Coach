import os

import pytest

from app.config.settings import ConfigError, ProductionConfig


@pytest.mark.parametrize(
    "missing_key", ["SECRET_KEY", "JWT_SECRET_KEY", "DATABASE_URL", "CORS_ALLOWED_ORIGINS"]
)
def test_production_config_requires_critical_env_vars(monkeypatch, missing_key):
    monkeypatch.setenv("SECRET_KEY", "secure-secret")
    monkeypatch.setenv("JWT_SECRET_KEY", "secure-jwt-secret")
    monkeypatch.setenv("DATABASE_URL", "sqlite:///prod.db")
    monkeypatch.setenv("CORS_ALLOWED_ORIGINS", "https://example.com")
    monkeypatch.delenv(missing_key, raising=False)

    with pytest.raises(ConfigError):
        ProductionConfig.validate()


def test_no_wildcard_cors_with_credentials(monkeypatch):
    monkeypatch.setenv("CORS_ALLOWED_ORIGINS", "*")
    monkeypatch.setenv("CORS_SUPPORTS_CREDENTIALS", "true")
    os.environ["FLASK_ENV"] = "production"
    with pytest.raises(ConfigError):
        ProductionConfig.validate()
