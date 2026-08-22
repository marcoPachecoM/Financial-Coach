import re

from app.categorization.categories import Categories

MCC_MAP: dict[int, str] = {
    # Food & Dining
    5812: Categories.FOOD_DINING,  # Restaurants
    5411: Categories.FOOD_DINING,  # Supermarkets / Grocery
    5499: Categories.FOOD_DINING,  # Misc Food Stores / Specialty Markets
    5814: Categories.FOOD_DINING,  # Fast Food Restaurants
    5815: Categories.ENTERTAINMENT,  # Digital Goods / Media
    # Utilities & Telecom
    4900: Categories.UTILITIES,  # Electric, Gas, Water
    4814: Categories.TELECOM,  # Telecommunications
    4899: Categories.TELECOM,  # Cable & Internet
    # Transportation & Automotive
    4121: Categories.TRANSPORT,  # Taxis / Rideshare
    5541: Categories.AUTOMOTIVE_FUEL,  # Service Stations
    3722: Categories.TRANSPORT,  # Bus Lines
    4511: Categories.TRANSPORT,  # Airlines
    4722: Categories.TRANSPORT,  # Travel Agencies
    7538: Categories.AUTOMOTIVE_FUEL,  # Auto Repair
    # Shopping & Retail
    5300: Categories.SHOPPING,  # Wholesale Clubs / General Merchandise
    5311: Categories.SHOPPING,  # Department Stores
    5310: Categories.SHOPPING,  # Discount Stores
    5211: Categories.HOME_HARDWARE,  # Building Materials
    5251: Categories.HOME_HARDWARE,  # Hardware Stores
    5261: Categories.HOME_HARDWARE,  # Lawn & Garden
    5719: Categories.SHOPPING,  # Home Furnishings
    5942: Categories.SHOPPING,  # Book Stores
    5651: Categories.SHOPPING,  # Clothing Stores
    5661: Categories.SHOPPING,  # Shoe Stores
    # Healthcare & Wellness
    5912: Categories.HEALTHCARE,  # Pharmacies
    8041: Categories.HEALTHCARE,  # Chiropractors
    8062: Categories.HEALTHCARE,  # Hospitals
    8021: Categories.HEALTHCARE,  # Dentists
    # Entertainment & Services
    7832: Categories.ENTERTAINMENT,  # Theaters
    7349: Categories.PERSONAL_BUSINESS,  # Maintenance
    9402: Categories.GOVERNMENT,  # Postal Services
    6300: Categories.INSURANCE,  # Insurance Premiums
}

REGEX_RULES: dict[str, list[str]] = {
    Categories.HEALTHCARE: [
        r"\bpharmacy\b",
        r"\bmedical\b",
        r"\bclinic\b",
        r"\bwalgreens\b",
        r"\bcvs\b",
        r"\bsan pablo pharmacy\b",
    ],
    Categories.FOOD_DINING: [
        r"\btesco\b",
        r"\bstarbucks\b",
        r"\brestaurant\b",
        r"\bdiner\b",
        r"\bmcdonald",
        r"\bvips\b",
        r"\bburger king\b",
        r"\bdomino'?s?\s+pizza\b",
        r"\bchipotle\b",
        r"\bel porton\b",
        r"\bcheesecake factory\b",
        r"\bkfc\b",
        r"\bitaliannis\b",
    ],
    Categories.AUTOMOTIVE_FUEL: [
        r"\boxxo gas\b",
        r"\bpemex\b",
        r"\bg500\b",
        r"\bbp\b",
        r"\bgas station\b",
    ],
    Categories.UTILITIES: [
        r"\bcity power\s*&\s*light\b",
        r"\bmetro water services\b",
        r"\bnational gas\b",
        r"\bwaste management\b",
    ],
    Categories.TELECOM: [
        r"\bt-mobile\b",
        r"\bverizon\b",
        r"\bat&t\b",
    ],
    Categories.ENTERTAINMENT: [
        r"\bnetflix\b",
    ],
    Categories.TRANSPORT: [
        r"\buber\b",
        r"\blyft\b",
        r"\btransit\b",
        r"\bprimera plus\b",
        r"\bvivaaerobus\b",
    ],
    Categories.SHOPPING: [
        r"\bconvenience\b",
        r"\btarget\b",
        r"\bwalmart\b",
        r"\bamazon\b",
        r"\bross\b",
        r"\bsears\b",
        r"\bsuburbia\b",
        r"\bnordstrom\b",
        r"\blidl\b",
    ],
}

COMPILED_REGEX_RULES: dict[str, list[re.Pattern[str]]] = {
    category: [re.compile(pattern, re.IGNORECASE) for pattern in patterns]
    for category, patterns in REGEX_RULES.items()
}
