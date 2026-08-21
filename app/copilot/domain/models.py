from dataclasses import dataclass
from enum import StrEnum
from typing import Literal


@dataclass(frozen=True)
class AIMessage:
    role: Literal["system", "user", "assistant"]
    content: str


@dataclass(frozen=True)
class AIResponse:
    content: str
    model: str
    provider: str
    input_tokens: int | None = None
    output_tokens: int | None = None


class InsightType(StrEnum):
    OPTIMIZATION = "OPTIMIZATION"
    BUDGET_ALERT = "BUDGET_ALERT"
    SUBSCRIPTION_DETECTED = "SUBSCRIPTION_DETECTED"


@dataclass(frozen=True)
class CopilotInsight:
    id: str
    type: InsightType
    title: str
    message: str
    action_required: bool
    suggested_action: str | None = None
