import http
import re
from typing import Optional
from flask import current_app

from app.categorization.rules import MCC_MAP, REGEX_RULES


class CategorizerService:
    @staticmethod
    def categorize(provider_name: str, mcc: Optional[int] = None) -> str:
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

        # 3. Fallback to Ollama classification
        return CategorizerService._classify_with_ollama(clean_provider)

    @staticmethod
    def _classify_with_ollama(provider_name: str) -> str:
        ollama_host = current_app.config.get("OLLAMA_HOST", "http://localhost:11434")
        ollama_model = current_app.config.get("OLLAMA_MODEL", "llama3.2")
        prompt = f"Categorize this financial provider: '{provider_name}'. Output strictly one category name."

        try:
            with http.Client(timeout=3.0) as client:
                res = client.post(
                    f"{ollama_host}/api/generate",
                    json={"model": ollama_model, "prompt": prompt, "stream": False},
                )
                if res.status_code == 200:
                    val = res.json().get("response", "").strip()
                    return val if 0 < len(val) < 25 else "Other"
        except Exception:
            # Fallback if Ollama service is unavailable or times out
            pass

        return "Other"

