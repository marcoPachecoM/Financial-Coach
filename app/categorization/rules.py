from typing import Dict, List


MCC_MAP: Dict[int, str] = {
    # Food & Dining
    5812: "Food & Dining",       # Restaurants
    5411: "Food & Dining",       # Supermarkets / Grocery
    5499: "Food & Dining",       # Misc Food Stores / Specialty Markets
    5814: "Food & Dining",       # Fast Food Restaurants
    5815: "Entertainment",       # Digital Goods / Media
    
    # Utilities & Telecom
    4900: "Utilities",           # Electric, Gas, Water
    4814: "Telecom",             # Telecommunications
    4899: "Telecom",             # Cable & Internet
    
    # Transportation & Automotive
    4121: "Transport",           # Taxis / Rideshare
    5541: "Automotive & Fuel",   # Service Stations
    3722: "Transport",           # Bus Lines
    4511: "Transport",           # Airlines
    4722: "Transport",           # Travel Agencies
    7538: "Automotive & Fuel",   # Auto Repair
    
    # Shopping & Retail
    5300: "Shopping",            # Wholesale Clubs / General Merchandise
    5311: "Shopping",            # Department Stores
    5310: "Shopping",            # Discount Stores
    5211: "Home & Hardware",     # Building Materials
    5251: "Home & Hardware",     # Hardware Stores
    5261: "Home & Hardware",     # Lawn & Garden
    5719: "Shopping",            # Home Furnishings
    5942: "Shopping",            # Book Stores
    5651: "Shopping",            # Clothing Stores
    5661: "Shopping",            # Shoe Stores
    
    # Healthcare & Wellness
    5912: "Healthcare",          # Pharmacies
    8041: "Healthcare",          # Chiropractors
    8062: "Healthcare",          # Hospitals
    8021: "Healthcare",          # Dentists
    
    # Entertainment & Services
    7832: "Entertainment",       # Theaters
    7349: "Personal & Business", # Maintenance
    9402: "Government",          # Postal Services
    6300: "Insurance",           # Insurance Premiums
}

# Regex Pattern Rules
REGEX_RULES: Dict[str, List[str]] = {
    "Healthcare": [
        r"pharmacy", r"medical", r"clinic", r"walgreens", r"cvs", r"san pablo pharmacy"
    ],
    "Food & Dining": [
        r"tesco", r"starbucks", r"restaurant", r"diner", r"mcdonald", 
        r"vips", r"burger king", r"dominos pizza", r"chipotle", 
        r"el port[oó]n", r"cheesecake factory", r"kfc", r"italiannis"
    ],
    "Automotive & Fuel": [
        r"oxxo gas", r"pemex", r"g500", r"bp\b", r"gas station"
    ],
    "Utilities": [
        r"city power & light", r"metro water services", r"national gas", r"waste managment"
    ],
    "Telecom": [
        r"t-mobile", r"verizon", r"at&t"
    ],
    "Entertainment": [
        r"netflix"
    ],
    "Transport": [
        r"uber", r"lyft", r"transit", r"primera plus", r"vivaaerobus"
    ],
    "Shopping": [
        r"convenience", r"target", r"walmart", r"amazon", r"ross", 
        r"sears", r"suburbia", r"nordstrom", r"lidl"
    ]
}