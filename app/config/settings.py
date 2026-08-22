import os
from datetime import timedelta
from typing import Any


class ConfigError(Exception):
    pass


def _parse_bool(value: str | None, default: bool = False) -> bool:
    if value is None:
        return default
    return value.lower() in {"1", "true", "yes", "on"}


def _parse_csv(value: str | None) -> list[str]:
    if not value:
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


def _parse_int(value: str | None, default: int) -> int:
    if value is None:
        return default
    return int(value)


def _require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise ConfigError(f"Missing required environment variable: {name}")
    return value


class BaseConfig:
    TESTING = False
    DEBUG = False

    ENV_NAME = os.getenv("FLASK_ENV", "development")
    SECRET_KEY = os.getenv("SECRET_KEY", "dev-only-secret-change-me")
    JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "dev-only-jwt-secret-change-me")

    OLLAMA_HOST: str = os.getenv("OLLAMA_HOST", "http://localhost:11434")
    OLLAMA_MODEL: str = os.getenv("OLLAMA_MODEL", "llama3.2")

    SQLALCHEMY_DATABASE_URI = os.getenv("DATABASE_URL", "sqlite:///app.db")
    SQLALCHEMY_TRACK_MODIFICATIONS = False
    SQLALCHEMY_EXPIRE_ON_COMMIT = False

    JWT_ALGORITHM = "HS256"
    JWT_DECODE_ALGORITHMS = ["HS256"]
    JWT_ISSUER = os.getenv("JWT_ISSUER", "financial-coach-api")
    JWT_AUDIENCE = os.getenv("JWT_AUDIENCE", "financial-coach-clients")
    JWT_ACCESS_TOKEN_EXPIRES = timedelta(
        minutes=_parse_int(os.getenv("JWT_ACCESS_TOKEN_EXPIRES"), 15)
    )
    JWT_REFRESH_TOKEN_EXPIRES = timedelta(
        days=_parse_int(os.getenv("JWT_REFRESH_TOKEN_EXPIRES"), 14)
    )
    JWT_DECODE_ISSUER = JWT_ISSUER
    JWT_ENCODE_ISSUER = JWT_ISSUER
    JWT_DECODE_AUDIENCE = JWT_AUDIENCE
    JWT_ENCODE_AUDIENCE = JWT_AUDIENCE
    JWT_REQUIRED_CLAIMS = ["exp", "iat", "nbf", "iss", "aud", "jti", "type", "sub"]

    RATELIMIT_HEADERS_ENABLED = True
    RATELIMIT_STORAGE_URI = os.getenv("RATE_LIMIT_STORAGE_URI", "memory://")
    RATELIMIT_DEFAULT = os.getenv("RATE_LIMIT_DEFAULT", "100 per hour")

    CORS_ALLOWED_ORIGINS = _parse_csv(
        os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:3000")
    )
    CORS_SUPPORTS_CREDENTIALS = _parse_bool(
        os.getenv("CORS_SUPPORTS_CREDENTIALS"), True
    )

    MAX_CONTENT_LENGTH = _parse_int(os.getenv("MAX_CONTENT_LENGTH"), 1024 * 1024)
    JSON_SORT_KEYS = False

    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")

    AI_PROVIDER = os.getenv(
        "AI_PROVIDER",
        "ollama",
    )

    AI_MODEL = os.getenv(
        "AI_MODEL",
        "llama3",
    )

    REFRESH_TOKEN_REUSE_BLOCK_SECONDS = _parse_int(
        os.getenv("REFRESH_TOKEN_REUSE_BLOCK_SECONDS"), 3600
    )

    @classmethod
    def validate(cls) -> None:
        if cls.CORS_SUPPORTS_CREDENTIALS and "*" in cls.CORS_ALLOWED_ORIGINS:
            raise ConfigError("CORS wildcard origin cannot be used with credentials.")


class DevelopmentConfig(BaseConfig):
    DEBUG = True

    OLLAMA_BASE_URL = os.getenv(
        "OLLAMA_BASE_URL",
        "http://localhost:11434",
    )


class TestingConfig(BaseConfig):
    TESTING = True
    DEBUG = False
    SQLALCHEMY_DATABASE_URI = os.getenv("TEST_DATABASE_URL", "sqlite:///:memory:")
    RATELIMIT_ENABLED = False
    CORS_ALLOWED_ORIGINS = ["http://localhost:3000"]
    SQLALCHEMY_EXPIRE_ON_COMMIT = False
    OLLAMA_BASE_URL = os.getenv(
        "OLLAMA_BASE_URL",
        "http://localhost:11434",
    )


class ProductionConfig(BaseConfig):
    DEBUG = False

    OPENAI_API_KEY = os.getenv(
        "OPENAI_API_KEY",
    )

    @classmethod
    def validate(cls) -> None:
        _require_env("SECRET_KEY")
        _require_env("JWT_SECRET_KEY")
        _require_env("DATABASE_URL")
        _require_env("CORS_ALLOWED_ORIGINS")
        _require_env("OPENAI_API_KEY")

        super().validate()

        insecure_defaults = {
            "dev-only-secret-change-me",
            "dev-only-jwt-secret-change-me",
            "...",
        }
        if (
            cls.SECRET_KEY in insecure_defaults
            or cls.JWT_SECRET_KEY in insecure_defaults
            or cls.OPENAI_API_KEY in insecure_defaults
        ):
            raise ConfigError("Insecure default secrets are not allowed in production.")


CONFIG_MAP: dict[str, type[BaseConfig]] = {
    "development": DevelopmentConfig,
    "testing": TestingConfig,
    "production": ProductionConfig,
}


def get_config(env_name: str | None) -> type[BaseConfig]:
    resolved = (env_name or os.getenv("FLASK_ENV") or "development").lower()
    return CONFIG_MAP.get(resolved, DevelopmentConfig)


def config_to_dict(config_class: type[BaseConfig]) -> dict[str, Any]:
    values: dict[str, Any] = {}
    for key in dir(config_class):
        if key.isupper():
            values[key] = getattr(config_class, key)
    return values
