from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal


# 1. Raw ingested transaction from CSV
@dataclass
class Transaction:
    id: str
    date: datetime
    provider_name: str
    amount: Decimal
    mcc: int
