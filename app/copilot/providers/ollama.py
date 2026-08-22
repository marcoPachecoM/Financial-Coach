from typing import Any

import httpx

from app.copilot.domain.models import AIMessage, AIResponse
from app.copilot.providers.base import AIProvider


class OllamaProvider(AIProvider):
    def __init__(
        self,
        name: str,
        model: str,
        base_url: str = "http://localhost:11434",
        api_key: str | None = None,
    ):
        super().__init__(
            name=name,
            model=model,
            base_url=base_url,
            api_key=api_key,
        )

        self._client = httpx.AsyncClient(
            base_url=self._base_url or base_url,
            timeout=120.0,
        )

    async def chat(
        self,
        messages: list[AIMessage],
        *,
        temperature: float = 0.2,
        max_tokens: int | None = None,
    ) -> AIResponse:
        payload: dict[str, Any] = {
            "model": self._model,
            "messages": [
                {
                    "role": message.role,
                    "content": message.content,
                }
                for message in messages
            ],
            "stream": False,
            "options": {
                "temperature": temperature,
            },
        }

        if max_tokens is not None:
            payload["options"]["num_predict"] = max_tokens

        data = {}
        try:
            response = await self._client.post(
                "/api/chat",
                json=payload,
            )

            response.raise_for_status()

            data = response.json()
        except httpx.HTTPStatusError:
            data["message"] = {
                "role": "assistant",
                "content": "Error response from Ollama API",
            }
        except httpx.RequestError:
            data["message"] = {
                "role": "assistant",
                "content": "Error connecting to Ollama API",
            }

        return AIResponse(
            content=data["message"]["content"],
            model=data.get("model", self._model),
            provider=self._name,
        )

    async def close(self) -> None:
        await self._client.aclose()
