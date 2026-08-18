from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal


@dataclass
class Transaction:
    external_id: str
    date: datetime
    amount: Decimal
    category: str | None
    super_category: str | None
