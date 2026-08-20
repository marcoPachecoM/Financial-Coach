from collections import defaultdict
from decimal import Decimal

from app.transactions.domain.models import Transaction


class TransactionAnalyticsService:

    @staticmethod
    def get_spending_by_category(
        transactions: list[Transaction],
    ) -> list[dict]:

        category_totals: dict[str, Decimal] = defaultdict(Decimal)
        category_counts: dict[str, int] = defaultdict(int)

        for transaction in transactions:
            category = transaction.category or "Uncategorized"

            category_totals[category] += transaction.amount
            category_counts[category] += 1

        total_spending = sum(category_totals.values(), Decimal("0"))

        categories = []

        for category, amount in category_totals.items():
            percentage = (
                (amount / total_spending) * 100
                if total_spending > 0
                else Decimal("0")
            )

            categories.append(
                {
                    "category": category,
                    "amount": round(float(amount), 2),
                    "percentage": round(float(percentage), 1),
                    "count": category_counts[category],
                }
            )

        return sorted(
            categories,
            key=lambda item: item["amount"],
            reverse=True,
        )

    @staticmethod
    def get_spending_trends(
        transactions: list[Transaction],
    ) -> dict:

        categories = TransactionAnalyticsService.get_spending_by_category(
            transactions
        )

        total_spent = sum(
            category["amount"]
            for category in categories
        )

        return {
            "total_spent": round(total_spent, 2),
            "categories": categories,
        }