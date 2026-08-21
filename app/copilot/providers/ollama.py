from app.copilot.domain.models import AIMessage, AIResponse
from app.copilot.providers.base import AIProvider


class OllamaProvider(AIProvider):
    def __init__(self, name: str, model: str, base_url: str | None, api_key: str | None = None):
        super().__init__(name=name, model=model, base_url=base_url, api_key=api_key)

    def chat(
        self,
        messages: list[AIMessage],
        *,
        temperature: float = 0.2,
        max_tokens: int | None = None,
    ) -> AIResponse:
        # TODO: Implement the chat logic for Ollama here
        # This is a placeholder implementation
        response_content = messages[-1].content[
            ::-1
        ]  # Just reversing the last message as a dummy response
        return AIResponse(content=response_content, model=self._model, provider=self._name)
