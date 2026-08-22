from collections.abc import Iterable

from app.categorization.categories import (
    VALID_CATEGORIES,
    Categories,
)
from app.categorization.model import CategoriesCache, Source
from app.categorization.normalization import normalize_provider
from app.categorization.repository import CategoriesCacheRepository
from app.categorization.rules import (
    COMPILED_REGEX_RULES,
    MCC_MAP,
)
from app.copilot.service import CopilotService

Transaction = tuple[str, int | None]
CategorizationResults = dict[Transaction, Categories]


class CategorizerService:
    def __init__(
        self,
        copilot_service: CopilotService | None = None,
    ) -> None:
        self._copilot_service = copilot_service or CopilotService()

    async def categorize(
        self,
        provider_name: str,
        mcc: int | None = None,
    ) -> str:
        provider = normalize_provider(provider_name)

        category = self._categorize_by_mcc(mcc)

        if category is not None:
            return category

        category = self._categorize_by_provider(provider)

        if category is not None:
            return category

        response = await self._copilot_service.classify_transaction(
            info={
                "provider": provider,
                "mcc": mcc,
            }
        )

        category = response.get("category")

        if category not in VALID_CATEGORIES:
            category = Categories.OTHER

        self.save_categorization_results(
            source=Source.LLM,
            results={
                (provider, mcc): category,
            },
        )

        return category

    async def categorize_many(
        self,
        transactions: Iterable[Transaction],
    ) -> CategorizationResults:
        unique_transactions = list(
            dict.fromkeys(
                (
                    normalize_provider(provider_name),
                    self._normalize_mcc(mcc),
                )
                for provider_name, mcc in transactions
            )
        )

        if not unique_transactions:
            return {}

        results: CategorizationResults = {}
        unknown: list[Transaction] = []

        for provider, mcc in unique_transactions:
            category = self._categorize_by_mcc(mcc)

            if category is not None:
                results[(provider, mcc)] = Categories(category)
                continue

            category = self._categorize_by_provider(provider)

            if category is not None:
                results[(provider, mcc)] = Categories(category)
                continue

            unknown.append((provider, mcc))

        if unknown:
            ai_results = await self._categorize_with_ai(unknown)
            results.update(ai_results)

        return results

    def _categorize_by_mcc(
        self,
        mcc: int | None,
    ) -> str | None:
        if mcc is None:
            return None

        return MCC_MAP.get(mcc)

    def _categorize_by_provider(
        self,
        provider: str,
    ) -> str | None:
        cache = CategoriesCacheRepository.get_category_by_provider(provider)

        category = cache.category if cache else Categories.ERROR

        if category in VALID_CATEGORIES:
            return category

        for category, patterns in COMPILED_REGEX_RULES.items():
            if any(pattern.search(provider) for pattern in patterns):
                self.save_categorization_results(
                    source=Source.RULE,
                    results={
                        (provider, None): Categories(category),
                    },
                )

                return category

        return None

    async def _categorize_with_ai(
        self,
        transactions: list[Transaction],
    ) -> CategorizationResults:
        if not transactions:
            return {}

        requested = set(transactions)

        response = await self._copilot_service.classify_transactions(transactions)

        results: CategorizationResults = {}

        for item in response:
            if not isinstance(item, dict):
                continue

            provider = item.get("provider")
            raw_mcc = item.get("mcc")
            category = item.get("category")

            if not isinstance(provider, str):
                continue

            provider = normalize_provider(provider)
            mcc = self._normalize_mcc(raw_mcc)

            key = (provider, mcc)

            if key not in requested:
                continue

            if category not in VALID_CATEGORIES:
                continue

            results[key] = Categories(category) or Categories.ERROR

        for transaction in transactions:
            if transaction not in results:
                results[transaction] = Categories.ERROR

        self.save_categorization_results(
            source=Source.LLM,
            results=results,
        )

        return results

    def save_categorization_results(
        self,
        source: Source,
        results: CategorizationResults,
    ) -> None:
        categories: list[CategoriesCache] = []

        for (provider_name, _), category in results.items():
            if category not in VALID_CATEGORIES:
                continue

            provider_name = normalize_provider(provider_name)

            if not provider_name:
                continue

            categories.append(
                CategoriesCache(
                    provider_name=provider_name,
                    category=category.value,
                    source=source,
                )
            )

        if not categories:
            return

        CategoriesCacheRepository.save_many(categories)

    @staticmethod
    def _normalize_mcc(
        mcc: int | str | None,
    ) -> int | None:
        if mcc is None:
            return None

        try:
            return int(mcc)
        except TypeError, ValueError:
            return None
