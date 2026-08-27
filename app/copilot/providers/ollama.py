import re
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

        clean_base_url = (self._base_url or base_url).rstrip("/")
        if clean_base_url.endswith("/api/chat"):
            clean_base_url = clean_base_url[:-9]
        elif clean_base_url.endswith("/api"):
            clean_base_url = clean_base_url[:-4]

        self._client = httpx.AsyncClient(
            base_url=clean_base_url,
            timeout=120.0,
        )

    async def chat(
        self,
        messages: list[AIMessage],
        *,
        temperature: float = 0.2,
        max_tokens: int | None = None,
        response_format: str | dict | None = None,
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

        if response_format is not None:
            payload["format"] = response_format

        content = ""
        clean_base_url = (self._base_url or "http://localhost:11434").rstrip("/")
        if clean_base_url.endswith("/api/chat"):
            clean_base_url = clean_base_url[:-9]
        elif clean_base_url.endswith("/api"):
            clean_base_url = clean_base_url[:-4]

        async with httpx.AsyncClient(base_url=clean_base_url, timeout=120.0) as client:
            try:
                response = await client.post("/api/chat", json=payload)
                response.raise_for_status()
                data = response.json()
                content = data.get("message", {}).get("content", "")

                if response_format == "json" or isinstance(response_format, dict):
                    content = self._clean_json_output(content)

            except httpx.HTTPStatusError as exc:
                content = f"Error response from Ollama API: {exc.response.status_code}"
            except httpx.RequestError as exc:
                content = f"Error connecting to Ollama API: {exc}"

        return AIResponse(
            content=content,
            model=self._model,
            provider=self._name,
        )

    @staticmethod
    def _clean_json_output(text: str) -> str:
        """Strips markdown code blocks to ensure valid JSON string output."""
        text = text.strip()
        if text.startswith("```"):
            # Remove ```json or ``` at start and ``` at end
            text = re.sub(r"^```(?:json)?\s*", "", text, flags=re.IGNORECASE)
            text = re.sub(r"\s*```$", "", text)
        return text.strip()

    async def close(self) -> None:
        await self._client.aclose()
