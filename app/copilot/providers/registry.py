from collections.abc import Callable
from enum import StrEnum

from gunicorn.errors import ConfigError

from app.copilot.providers.base import AIProvider
from app.copilot.providers.ollama import OllamaProvider


class AIProviders(StrEnum):
    OPENAI = "openai"
    OLLAMA = "ollama"


PROVIDER_FACTORIES: dict[
    AIProviders,
    Callable[..., AIProvider],
] = {
    AIProviders.OLLAMA: OllamaProvider,
}


class AIProviderRegistry:
    def __init__(self) -> None:
        self._provider: AIProvider | None = None

    def register(
        self,
        name: AIProviders,
        model: str,
        base_url: str | None,
        api_key: str | None,
    ) -> None:
        factory = PROVIDER_FACTORIES.get(name)

        if factory is None:
            raise ConfigError(f"Unsupported AI provider: {name}")

        self._provider = factory(
            name=name,
            model=model,
            base_url=base_url,
            api_key=api_key,
        )

    def get(self) -> AIProvider:
        if self._provider is None:
            raise ConfigError("AI provider has not been registered")

        return self._provider


provider_registry = AIProviderRegistry()
