import json

from app.categorization.categories import VALID_CATEGORIES
from app.copilot.domain.models import AIMessage, AIResponse
from app.copilot.providers.registry import provider_registry

BATCH_SCHEMA = {
    "type": "array",
    "items": {
        "type": "object",
        "properties": {
            "provider": {"type": "string"},
            "mcc": {"type": ["integer", "null"]},
            "category": {"type": "string"},
        },
        "required": ["provider", "category"],
    },
}


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
                response_format=BATCH_SCHEMA,
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

Respond strictly in raw JSON format.
Example array format:
[
  {{"provider": "STARBUCKS", "mcc": 5814, "category": "Food & Dining"}}
]

Do not explain your reasoning.
Do not wrap response in conversational text.
""".strip()

        return f"""
You categorize a financial transaction.

Allowed categories:
{categories}

Respond strictly in raw JSON format:
{{"category": "Utilities"}}
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

    # Removed @staticmethod so Python automatically binds 'self'
    def _parse_batch_response(self, response: AIResponse) -> list[dict]:
        raw_content = response.content

        try:
            data = json.loads(raw_content)
            # Handle cases where the model wraps the array in a dict key like {"items": [...]}
            if isinstance(data, dict):
                for key in ["transactions", "items", "data", "results"]:
                    if key in data and isinstance(data[key], list):
                        data = data[key]
                        break

            if not isinstance(data, list):
                print(f"Warning: Expected list, got {type(data)}")
                return []

            # Validate category names
            validated_results = []
            for item in data:
                raw_cat = item.get("category", "")
                clean_cat = raw_cat.strip()

                if clean_cat not in VALID_CATEGORIES:
                    print(f" Category Rejected: '{raw_cat}' is not in VALID_CATEGORIES!")
                    item["category"] = "Other"  # Fallback
                else:
                    item["category"] = clean_cat

                validated_results.append(item)

            return validated_results

        except json.JSONDecodeError as exc:
            print(f" JSON Decode Error: {exc}")
            return []
