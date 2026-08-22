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

# Regex Pattern Rules
REGEX_RULES: dict[str, list[str]] = {
    Categories.HEALTHCARE: [
        r"pharmacy",
        r"medical",
        r"clinic",
        r"walgreens",
        r"cvs",
        r"san pablo pharmacy",
    ],
    Categories.FOOD_DINING: [
        r"tesco",
        r"starbucks",
        r"restaurant",
        r"diner",
        r"mcdonald",
        r"vips",
        r"burger king",
        r"dominos pizza",
        r"chipotle",
        r"el port[oó]n",
        r"cheesecake factory",
        r"kfc",
        r"italiannis",
    ],
    Categories.AUTOMOTIVE_FUEL: [
        r"oxxo gas",
        r"pemex",
        r"g500",
        r"bp\b",
        r"gas station",
    ],
    Categories.UTILITIES: [
        r"city power & light",
        r"metro water services",
        r"national gas",
        r"waste managment",
    ],
    Categories.TELECOM: [r"t-mobile", r"verizon", r"at&t"],
    Categories.ENTERTAINMENT: [r"netflix"],
    Categories.TRANSPORT: [
        r"uber",
        r"lyft",
        r"transit",
        r"primera plus",
        r"vivaaerobus",
    ],
    Categories.SHOPPING: [
        r"convenience",
        r"target",
        r"walmart",
        r"amazon",
        r"ross",
        r"sears",
        r"suburbia",
        r"nordstrom",
        r"lidl",
    ],
}
