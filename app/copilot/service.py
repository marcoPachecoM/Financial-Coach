from app.analytics.service import AnalyticsService
from app.categorization.rules import Categories
from app.common.utils import json_to_toon
from app.copilot.domain.models import AIMessage
from app.copilot.providers.base import AIProvider
from app.copilot.providers.registry import (
    provider_registry as copilot_provider_registry,
)


class CopilotService:
    def __init__(
        self,
        analytics_service: AnalyticsService | None = None,
    ):
        self.analytics_service = analytics_service or AnalyticsService()

    async def chat(
        self,
        user_id: str,
        connection_id: int,
        message: str,
    ) -> dict:
        context = self.analytics_service.build_copilot_context(
            user_id=user_id,
            connection_id=connection_id,
        )

        messages = [
            AIMessage(
                role="system",
                content=self._build_system_prompt(),
            ),
            AIMessage(
                role="user",
                content=self._build_user_prompt(
                    message,
                    context,
                ),
            ),
        ]

        response = await self._get_ai_provider().chat(
            messages,
            temperature=0.2,
        )

        return {
            "response": response.content,
            "provider": response.provider,
            "model": response.model,
        }

    def analyze(self, user_id: str) -> dict:
        # Placeholder for future implementation of analysis functionality

        return {
            "message": "Analysis functionality is not yet implemented.",
            "user_id": user_id,
        }

    async def classify_transaction(
        self,
        info: dict,
    ) -> dict:
        messages = [
            AIMessage(
                role="system",
                content=self._build_system_prompt(),
            ),
            AIMessage(
                role="user",
                content=self._build_category_prompt(info),
            ),
        ]
        response = await self._get_ai_provider().chat(
            messages,
            temperature=0.2,
        )

        return {
            "category": response.content,
            "provider": response.provider,
            "model": response.model,
        }

    def _build_system_prompt(self) -> str:
        return (
            "You are a financial assistant that helps users understand their "
            "financial data and provides insights based on their transactions."
        )

    def _build_category_prompt(self, transaction_info: dict) -> str:
        categories = [category.value for category in Categories]
        return (
            f"Transaction details:\n"
            f"Date: {transaction_info.get('mcc')}\n"
            f"Merchant/ Provider: {transaction_info.get('provider')}\n"
            f"Available categories: {', '.join(categories)}\n"
            "Please provide a category for this transaction. Only Provide the category name without any additional text or explanation or punctuation. e.g. if I request: Restaurant you only response: Food & Dining"
        )

    def _build_user_prompt(self, user_message: str, context: dict) -> str:
        context_toon = json_to_toon(context)
        return (
            f"User message: {user_message}\n"
            f"Context:\n```\n{context_toon}\n```\n"
            "Please provide insights and suggestions based on the user's message "
            "and the provided context."
        )

    def _get_ai_provider(self) -> AIProvider:
        return copilot_provider_registry.get()
