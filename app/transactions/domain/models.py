from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Optional
from app.extensions import db

# 1. Raw ingested transaction from CSV
@dataclass
class Transaction:
    id: str
    date: datetime
    provider_name: str
    amount: Decimal
    mcc: int