import enum

from app.transactions.ingestion.base import TransactionProvider


class Providers(str, enum.Enum):
    CSV = "csv"
    # We can add other providers here as needed


class ProviderRegistry:
    def __init__(self):
        self._providers = {}

    def register(self, provider_name, provider: TransactionProvider):
        self._providers[provider_name] = provider

    def get(self, provider_name: str) -> TransactionProvider | None:
        return self._providers.get(provider_name)


provider_registry = ProviderRegistry()
