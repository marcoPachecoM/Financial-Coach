import json

from app.categorization.categories import VALID_CATEGORIES
from app.copilot.domain.models import AIMessage, AIResponse
from app.copilot.providers.registry import provider_registry


class CopilotService:
    def __init__(self) -> None:
        self._provider = None

    @property
    def provider(self):
        if self._provider is None:
            self._provider = provider_registry.get()
        return self._provider

    async def classify_transaction(
        self,
        info: dict,
    ) -> dict:
        response = await self.provider.chat(
            messages=[
                AIMessage(role="system", content=self._classification_system_prompt()),
                AIMessage(role="user", content=str(info)),
            ],
        )

        return self._parse_response(response)

    async def classify_transactions(
        self,
        transactions: list[tuple[str, int | None]],
        batch_size: int = 50,
    ) -> list[dict]:
        results: list[dict] = []

        for index in range(0, len(transactions), batch_size):
            batch = transactions[index : index + batch_size]

            response = await self.provider.chat(
                messages=[
                    AIMessage(
                        role="system",
                        content=self._classification_system_prompt(batch=True),
                    ),
                    AIMessage(role="user", content=self._build_batch_prompt(batch)),
                ],
            )

            results.extend(self._parse_batch_response(response))

        return results

    @staticmethod
    def _classification_system_prompt(
        batch: bool = False,
    ) -> str:
        categories = """
Food & Dining
Utilities
Telecom
Transport
Automotive & Fuel
Home & Hardware
Shopping
Healthcare
Entertainment
Personal & Business
Government
Insurance
Other
""".strip()

        if batch:
            return f"""
You categorize financial merchants.

Allowed categories:
{categories}

Return ONLY a JSON array.

Each item must contain:
- provider
- mcc
- category

Do not explain your reasoning.
Do not add any other fields.
""".strip()

        return f"""
You categorize a financial transaction.

Allowed categories:
{categories}

Return ONLY JSON with:
- category

Do not explain your reasoning.
""".strip()

    @staticmethod
    def _build_batch_prompt(
        transactions: list[tuple[str, int | None]],
    ) -> str:
        lines = []

        for provider, mcc in transactions:
            lines.append(f'{{"provider": "{provider}", "mcc": {mcc}}}')

        return "\n".join(lines)

    @staticmethod
    def _parse_response(
        response: AIResponse,
    ) -> dict:
        try:
            data = json.loads(response.content)
        except json.JSONDecodeError:
            return {}

        if not isinstance(data, dict):
            return {}

        category = data.get("category")

        if category not in VALID_CATEGORIES:
            return {}

        return {
            "category": category,
        }

    @staticmethod
    def _parse_batch_response(
        response: AIResponse,
    ) -> list[dict]:
        try:
            data = json.loads(response.content)
        except json.JSONDecodeError:
            return []

        if not isinstance(data, list):
            return []

        results: list[dict] = []

        for item in data:
            if not isinstance(item, dict):
                continue

            provider = item.get("provider")
            mcc = item.get("mcc")
            category = item.get("category")

            if not isinstance(provider, str):
                continue

            if category not in VALID_CATEGORIES:
                continue

            if mcc is not None:
                try:
                    mcc = int(mcc)
                except TypeError, ValueError:
                    mcc = None

            results.append(
                {
                    "provider": provider,
                    "mcc": mcc,
                    "category": category,
                }
            )

        return results
