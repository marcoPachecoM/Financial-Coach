import re

from app.categorization.rules import MCC_MAP, REGEX_RULES
from app.copilot.service import CopilotService


class CategorizerService:
    @staticmethod
    async def categorize(provider_name: str, mcc: int | None = None) -> str:
        clean_provider = provider_name.strip()
        mcc_int = int(mcc) if mcc is not None else None

        # 1. Match against MCC Map
        if mcc_int and mcc_int in MCC_MAP:
            return MCC_MAP[mcc_int]

        # 2. Match against Regex Rules
        prov_lower = clean_provider.lower()
        for category, patterns in REGEX_RULES.items():
            for pattern in patterns:
                if re.search(pattern, prov_lower):
                    return category

        copilot_service = CopilotService()
        response = await copilot_service.classify_transaction(
            info={
                "provider": clean_provider,
                "mcc": mcc_int,
            }
        )
        result: str | None = response.get("category")
        return result if isinstance(result, str) else "Uncategorized"
