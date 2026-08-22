from enum import StrEnum


class Categories(StrEnum):
    FOOD_DINING = "Food & Dining"
    UTILITIES = "Utilities"
    TELECOM = "Telecom"
    TRANSPORT = "Transport"
    AUTOMOTIVE_FUEL = "Automotive & Fuel"
    HOME_HARDWARE = "Home & Hardware"
    SHOPPING = "Shopping"
    HEALTHCARE = "Healthcare"
    ENTERTAINMENT = "Entertainment"
    PERSONAL_BUSINESS = "Personal & Business"
    GOVERNMENT = "Government"
    INSURANCE = "Insurance"
    OTHER = "Other"
    ERROR = "Error"


VALID_CATEGORIES = {category.value for category in Categories if category != Categories.ERROR}
