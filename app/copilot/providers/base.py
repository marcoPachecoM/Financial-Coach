from abc import ABC, abstractmethod

from app.copilot.domain.models import AIMessage, AIResponse


class AIProvider(ABC):
    def __init__(self, name: str, model: str, base_url: str | None, api_key: str | None = None):
        self._model = model
        self._name = name
        self._base_url = base_url
        self._api_key = api_key

    @abstractmethod
    async def chat(
        self,
        messages: list[AIMessage],
        *,
        temperature: float = 0.2,
        max_tokens: int | None = None,
    ) -> AIResponse:
        raise NotImplementedError
