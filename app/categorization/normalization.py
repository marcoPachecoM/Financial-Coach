import re
import unicodedata


def normalize_provider(provider: str) -> str:
    value = unicodedata.normalize("NFKD", provider)

    value = value.encode(
        "ascii",
        "ignore",
    ).decode("ascii")

    value = value.lower()

    value = re.sub(r"\d+", " ", value)
    value = re.sub(r"[*#/_-]+", " ", value)
    value = re.sub(r"\s+", " ", value)

    return value.strip()
