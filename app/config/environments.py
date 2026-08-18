from app.config.settings import CONFIG_MAP


def list_environments() -> list[str]:
    return list(CONFIG_MAP.keys())
