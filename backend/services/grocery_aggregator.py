import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import price_catalog  # noqa: E402
from database import _normalize_token  # noqa: E402

_parse_quantity = price_catalog._parse_quantity


LOCAL_SYNONYMS = {
    "bawang": "garlic",
    "itlog": "egg",
    "eggs": "egg",
    "sibuyas": "onion",
    "kamatis": "tomato",
    "luya": "ginger",
    "manok": "chicken",
    "baboy": "pork",
    "baka": "beef",
    "hipon": "shrimp",
    "shrimps": "shrimp",
    "crabs": "crab",
    "alimasag": "crab",
    "clams": "clam",
    "bigas": "rice",
    "patatas": "potato",
    "sitaw": "string beans",
    "tanglad": "lemongrass",
    "laurel": "bay leaves",
    "gabi": "taro",
    "bayabas": "guava",
    "kamias": "kamias",
    "pusit": "squid",
    "tahong": "mussels",
    "tokwa": "tofu",
    "sampalok": "tamarind",
    "atsuete": "annatto",
    "paminta": "black pepper",
    "munggo": "monggo",
    "mung": "monggo",
    "mungbean": "monggo",
    "mung bean": "monggo",
    "mung beans": "monggo",
    "monggo beans": "monggo",
    "moringa": "malunggay",
    "moringa leaves": "malunggay leaves",
    "kalabasa squash": "kalabasa",
    "toyo": "soy sauce",
    "suka": "vinegar",
    "patis": "fish sauce",
    "gata": "coconut milk",
    "kakang gata": "coconut milk",
    "sinigang sa sampaloc": "sinigang mix",
    "sinigang na sampaloc": "sinigang mix",
    "sinigang powder": "sinigang mix",
    "tamarind soup base mix": "sinigang mix",
    "tamarind base powder": "sinigang mix",
    "annatto oil": "annatto oil",
    "atsuete oil": "annatto oil",
    "achuete oil": "annatto oil",
    "anatto": "annatto",
    "bay leaf": "bay leaves",
    "long green beans": "string beans",
    "snake beans": "string beans",
    "baguio beans": "green beans",
    "dahon ng laurel": "bay leaves",
    "bangus milkfish": "bangus",
    "milkfish bangus": "bangus",
    "siling haba": "siling mahaba",
    "long green peppers": "long green chili",
    "long green pepper": "long green chili",
}

DESCRIPTORS = {
    "fresh",
    "raw",
    "cooked",
    "minced",
    "chopped",
    "sliced",
    "diced",
    "cubed",
    "cube",
    "cubes",
    "chunked",
    "chunks",
    "crushed",
    "ground",
    "whole",
    "small",
    "medium",
    "large",
    "lean",
    "skinless",
    "boneless",
    "optional",
    "about",
    "approx",
    "approximately",
    "peeled",
    "grated",
    "shredded",
    "thinly",
    "finely",
    "ripe",
    "dried",
    "drained",
    "canned",
    "native",
    "leaf",
    "leaves",
    "cut",
    "into",
    "inch",
    "inches",
    "lengths",
    "length",
    "serving",
    "serve",
    "and",
    "or",
    "to",
    "of",
    "for",
    "taste",
    "cleaned",
    "quartered",
    "halved",
    "removed",
    "seeded",
    "seeds",
    "wedges",
    "strips",
    "strip",
    "rounds",
    "rings",
    "trimmed",
    "pounded",
    "cracked",
    "crosswise",
    "diagonally",
    "garnish",
    "half",
    "thick",
    "thin",
    "core",
    "cored",
    "gutted",
    "deboned",
    "butterflied",
    "cooled",
    "boiled",
    "steamed",
    "fried",
    "deep",
    "julienned",
    "lightly",
    "rinsed",
    "torn",
    "deveined",
    "each",
    "only",
    "in",
    "well",
    "wedged",
    "bias",
    "ends",
    "topping",
    "parts",
    "sticks",
    "squares",
    "florets",
    "s",
}

COUNT_UNIT_WORDS = {
    "piece",
    "pieces",
    "piraso",
    "pc",
    "pcs",
    "clove",
    "cloves",
    "bunch",
    "bunches",
    "tali",
    "stalk",
    "stalks",
    "head",
    "heads",
    "can",
    "cans",
    "pack",
    "packs",
    "packet",
    "packets",
    "tray",
    "trays",
    "bundle",
    "bundles",
    "thumb",
    "thumbs",
    "slice",
    "slices",
    "fillet",
    "fillets",
    "sachet",
    "sachets",
    "package",
    "packages",
    "block",
    "blocks",
    "square",
    "squares",
}

KNOWN_INGREDIENT_PHRASES = [
    "soy sauce",
    "fish sauce",
    "oyster sauce",
    "tomato sauce",
    "worcestershire sauce",
    "hot sauce",
    "hoisin sauce",
    "spaghetti sauce",
    "barbecue sauce",
    "anchovy sauce",
    "shrimp paste",
    "tomato paste",
    "coconut milk",
    "coconut cream",
    "fresh milk",
    "evaporated milk",
    "olive oil",
    "coconut oil",
    "cooking oil",
    "canola oil",
    "vegetable oil",
    "sesame oil",
    "annatto oil",
    "atsuete oil",
    "achuete oil",
    "neutral oil",
    "corn oil",
    "liquid seasoning",
    "knorr seasoning sauce",
    "maggi savor seasoning sauce",
    "pork belly",
    "pork ribs",
    "chicken breast",
    "chicken thigh",
    "brown rice",
    "red rice",
    "white rice",
    "bay leaves",
    "bay leaf",
    "laurel leaves",
    "dahon ng laurel",
    "malunggay leaves",
    "moringa leaves",
    "mung beans",
    "mung bean",
    "monggo beans",
    "kalabasa squash",
    "string beans",
    "long green beans",
    "snake beans",
    "green beans",
    "baguio beans",
    "bok choy",
    "bell pepper",
    "black pepper",
    "peppercorn",
    "siling haba",
    "siling labuyo",
    "siling pangsigang",
    "siling mahaba",
    "finger chilies",
    "long green chilies",
    "long green chili",
    "long green peppers",
    "long green pepper",
    "bird eye chilies",
    "birds eye chilies",
    "thai chilies",
    "lemongrass",
    "banana blossom",
    "sweet potato",
    "bangus milkfish",
    "milkfish bangus",
    "bangus belly",
    "bangus fillet",
    "tilapia fillet",
    "sinigang mix",
    "sinigang sa sampaloc",
    "sinigang na sampaloc",
    "sinigang powder",
    "tamarind soup base mix",
    "tamarind base powder",
    "magic sarap",
    "maggi magic sarap",
    "knorr sinigang sa sampaloc",
    "knorr sinigang na sampaloc",
    "knorr ginataang gulay mix",
    "ginataang gulay mix",
    "knorr liquid seasoning",
    "miso paste",
    "soybean paste",
    "tamarind paste",
    "green curry paste",
    "curry paste",
    "curry powder",
    "turmeric powder",
    "baking powder",
    "five spice powder",
    "annatto powder",
    "annatto seeds",
    "anatto powder",
    "anatto seeds",
    "whole cloves",
    "ground cloves",
]

KNOWN_INGREDIENTS = {
    "garlic",
    "egg",
    "onion",
    "tomato",
    "ginger",
    "chicken",
    "pork",
    "beef",
    "fish",
    "bangus",
    "tilapia",
    "squid",
    "pompano",
    "galunggong",
    "salmon",
    "mussels",
    "tahong",
    "crab",
    "alimasag",
    "clam",
    "clams",
    "shrimp",
    "shrimps",
    "rice",
    "monggo",
    "munggo",
    "lentils",
    "lentil",
    "milk",
    "cheese",
    "cabbage",
    "pechay",
    "spinach",
    "kale",
    "malunggay",
    "moringa",
    "kangkong",
    "bok choy",
    "okra",
    "ampalaya",
    "eggplant",
    "talong",
    "sayote",
    "taro",
    "gabi",
    "upo",
    "bottle gourd",
    "squash",
    "kalabasa",
    "chili",
    "sili",
    "siling haba",
    "siling labuyo",
    "siling pangsigang",
    "siling mahaba",
    "carrot",
    "potato",
    "sweet potato",
    "banana",
    "saba",
    "apple",
    "orange",
    "calamansi",
    "guava",
    "kamias",
    "jackfruit",
    "oat",
    "bread",
    "flour",
    "pasta",
    "noodles",
    "tuna",
    "sardines",
    "string beans",
    "green beans",
    "bay leaves",
    "laurel leaves",
    "lemongrass",
    "black pepper",
    "peppercorn",
    "sugar",
    "annatto",
    "curry powder",
    "miso paste",
    "sinigang mix",
    "liquid seasoning",
    "oyster sauce",
    "tomato sauce",
    "tomato paste",
    "shrimp paste",
    "worcestershire sauce",
    "hot sauce",
    "hoisin sauce",
    "spaghetti sauce",
    "barbecue sauce",
    "anchovy sauce",
    "cooking oil",
    "canola oil",
    "vegetable oil",
    "sesame oil",
    "annatto oil",
    "neutral oil",
    "corn oil",
    "coconut cream",
    "fresh milk",
    "evaporated milk",
    "tofu",
    "tokwa",
    "papaya",
    "basil",
    "sausage",
    "chorizo",
    "flour",
    "whole cloves",
    "ground cloves",
    "tamarind paste",
    "green curry paste",
    "curry paste",
    "turmeric powder",
    "baking powder",
    "water",
    "coffee",
    "tea",
    "vinegar",
    "soy",
    "sauce",
    "oil",
    "salt",
    "pepper",
}

QTY_RE = re.compile(
    r"(?P<num>\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*"
    r"(?P<unit>kilograms|kilogram|kilo|kg|grams|gram|g|pounds|pound|lbs|lb|ounces|ounce|oz|"
    r"milliliters|milliliter|ml|liters|liter|litres|litre|l|quarts|quart|"
    r"tablespoons|tablespoon|tbsp|teaspoons|teaspoon|tsp|cups|cup|pieces|piece|piraso|pcs|pc|"
    r"cloves|clove|bunches|bunch|bundles|bundle|tali|stalks|stalk|heads|head|"
    r"fillets|fillet|slices|slice|thumbs|thumb|cans|can|sachets|sachet|"
    r"packages|package|packets|packet|packs|pack|blocks|block|squares|square|trays|tray)"
    r"\.?(?![a-z])",
    re.IGNORECASE,
)

LEADING_COUNT_RE = re.compile(
    r"^\s*(?P<first>\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)"
    r"(?:\s*(?:-|to|or)\s*(?P<second>\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?))?",
    re.IGNORECASE,
)

UNIT_ALIASES = {
    "kilo": "kg",
    "kilogram": "kg",
    "gram": "g",
    "grams": "g",
    "lbs": "lb",
    "pound": "lb",
    "pounds": "lb",
    "ounce": "oz",
    "ounces": "oz",
    "milliliter": "ml",
    "milliliters": "ml",
    "liter": "l",
    "litre": "l",
    "liters": "l",
    "litres": "l",
    "quart": "quart",
    "quarts": "quart",
    "cups": "cup",
    "tablespoon": "tbsp",
    "tablespoons": "tbsp",
    "teaspoon": "tsp",
    "teaspoons": "tsp",
    "pieces": "piece",
    "piraso": "piece",
    "pc": "piece",
    "pcs": "piece",
    "cloves": "clove",
    "bunches": "bunch",
    "tali": "bunch",
    "stalks": "stalk",
    "heads": "head",
    "bundle": "bunch",
    "bundles": "bunch",
    "thumb": "thumb",
    "thumbs": "thumb",
    "slice": "slice",
    "slices": "slice",
    "fillet": "fillet",
    "fillets": "fillet",
    "cans": "can",
    "sachet": "pack",
    "sachets": "pack",
    "package": "pack",
    "packages": "pack",
    "packs": "pack",
    "packet": "pack",
    "packets": "pack",
    "block": "piece",
    "blocks": "piece",
    "square": "piece",
    "squares": "piece",
    "trays": "tray",
}

PIECE_GRAMS = {
    ("garlic", "clove"): 5.0,
    ("garlic", "head"): 45.0,
    ("garlic", "piece"): 5.0,
    ("egg", "piece"): 55.0,
    ("onion", "piece"): 110.0,
    ("tomato", "piece"): 90.0,
    ("ginger", "piece"): 20.0,
    ("ginger", "thumb"): 18.0,
    ("chicken", "piece"): 180.0,
    ("pork", "piece"): 150.0,
    ("fish", "piece"): 180.0,
    ("potato", "piece"): 150.0,
    ("sweet potato", "piece"): 150.0,
    ("calamansi", "piece"): 20.0,
    ("lemon", "piece"): 80.0,
    ("banana", "piece"): 100.0,
    ("saba", "piece"): 100.0,
    ("eggplant", "piece"): 150.0,
    ("talong", "piece"): 150.0,
    ("sayote", "piece"): 200.0,
    ("taro", "piece"): 150.0,
    ("gabi", "piece"): 150.0,
    ("upo", "piece"): 500.0,
    ("bottle gourd", "piece"): 500.0,
    ("okra", "piece"): 15.0,
    ("ampalaya", "piece"): 250.0,
    ("string beans", "piece"): 10.0,
    ("green beans", "piece"): 10.0,
    ("bay leaves", "piece"): 0.25,
    ("laurel leaves", "piece"): 0.25,
    ("lemongrass", "stalk"): 25.0,
    ("lemongrass", "piece"): 25.0,
    ("bok choy", "bunch"): 250.0,
    ("bok choy", "piece"): 180.0,
    ("siling haba", "piece"): 8.0,
    ("siling mahaba", "piece"): 8.0,
    ("siling pangsigang", "piece"): 8.0,
    ("siling labuyo", "piece"): 3.0,
    ("chili", "piece"): 8.0,
    ("sili", "piece"): 8.0,
    ("bangus", "piece"): 450.0,
    ("tilapia", "piece"): 350.0,
    ("squid", "piece"): 150.0,
    ("mussels", "piece"): 20.0,
    ("clam", "piece"): 20.0,
    ("crab", "piece"): 180.0,
    ("tofu", "piece"): 250.0,
    ("papaya", "piece"): 600.0,
    ("sausage", "piece"): 40.0,
    ("pechay", "bunch"): 180.0,
    ("kangkong", "bunch"): 180.0,
    ("malunggay leaves", "bunch"): 80.0,
    ("malunggay", "bunch"): 80.0,
}

CUP_GRAMS = {
    "malunggay leaves": 30.0,
    "malunggay": 30.0,
    "moringa leaves": 30.0,
    "kangkong": 45.0,
    "pechay": 70.0,
    "kalabasa": 140.0,
    "squash": 140.0,
    "monggo": 80.0,
    "mung beans": 80.0,
    "monggo beans": 80.0,
    "lentils": 80.0,
    "lentil": 80.0,
    "rice": 55.0,
    "brown rice": 55.0,
    "red rice": 55.0,
    "white rice": 55.0,
    "string beans": 100.0,
    "green beans": 100.0,
    "bok choy": 70.0,
    "lemongrass": 35.0,
    "tomato": 180.0,
    "onion": 160.0,
    "potato": 150.0,
    "sweet potato": 150.0,
    "miso paste": 260.0,
    "tomato sauce": 245.0,
    "tomato paste": 260.0,
    "shrimp paste": 260.0,
    "curry powder": 120.0,
    "annatto powder": 120.0,
    "black pepper": 120.0,
    "peppercorn": 120.0,
    "peppercorns": 120.0,
    "turmeric powder": 120.0,
    "baking powder": 120.0,
    "tofu": 250.0,
}

LIQUID_VOLUME_KEYS = {
    "annatto oil",
    "apple cider vinegar",
    "canola oil",
    "coconut cream",
    "coconut milk",
    "coconut oil",
    "cooking oil",
    "fish sauce",
    "fresh milk",
    "evaporated milk",
    "liquid seasoning",
    "milk",
    "neutral oil",
    "oil",
    "olive oil",
    "corn oil",
    "sesame oil",
    "soy sauce",
    "vegetable oil",
    "vinegar",
    "water",
}

COOKED_DRY_EQUIVALENT_FACTOR = {
    "monggo": 1.0 / 3.0,
    "mung beans": 1.0 / 3.0,
    "monggo beans": 1.0 / 3.0,
    "lentils": 1.0 / 3.0,
    "lentil": 1.0 / 3.0,
    "rice": 1.0 / 3.0,
    "brown rice": 1.0 / 3.0,
    "red rice": 1.0 / 3.0,
    "white rice": 1.0 / 3.0,
}

COOKED_DRY_MARKERS = {"cooked", "steamed", "boiled"}

DISPLAY_NAME_OVERRIDES = {
    "egg": "Eggs",
    "malunggay": "Malunggay Leaves",
    "malunggay leaves": "Malunggay Leaves",
    "moringa leaves": "Malunggay Leaves",
    "mung beans": "Monggo",
    "monggo beans": "Monggo",
    "string beans": "String Beans",
    "green beans": "Green Beans",
    "bay leaves": "Bay Leaves",
    "laurel leaves": "Bay Leaves",
    "black pepper": "Black Pepper",
    "cooking oil": "Cooking Oil",
    "canola oil": "Canola Oil",
    "vegetable oil": "Vegetable Oil",
    "sesame oil": "Sesame Oil",
    "coconut cream": "Coconut Cream",
    "liquid seasoning": "Liquid Seasoning",
    "annatto oil": "Annatto Oil",
    "neutral oil": "Neutral Oil",
    "corn oil": "Corn Oil",
    "fresh milk": "Fresh Milk",
    "evaporated milk": "Evaporated Milk",
    "siling haba": "Siling Haba",
    "siling labuyo": "Siling Labuyo",
    "siling pangsigang": "Siling Pangsigang",
    "siling mahaba": "Siling Mahaba",
    "bok choy": "Bok Choy",
    "miso paste": "Miso Paste",
    "sinigang mix": "Sinigang Mix",
    "tofu": "Tofu",
}


def aggregate_grocery_list(plan: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    """Consolidate a planned week into canonical grocery buckets."""
    aggregated: Dict[str, Dict[str, Any]] = {}
    for day in plan or []:
        for meal in day.get("meals", []) or []:
            for ingredient in meal.get("ingredients", []) or []:
                name = str(ingredient.get("name", "") if isinstance(ingredient, dict) else ingredient)
                quantity = str(ingredient.get("quantity", "") if isinstance(ingredient, dict) else "")
                scale = _quantity_scale(ingredient)
                key, display_name = _canonical_name(name)
                if not key:
                    continue
                value, unit = _parse_source_quantity(name, quantity)
                value *= scale
                base_value, base_unit = _to_base_quantity(key, value, unit, name)
                bucket = aggregated.setdefault(
                    key,
                    {
                        "name": display_name,
                        "totalValue": 0.0,
                        "unit": base_unit,
                        "originalNames": [],
                    },
                )
                if bucket["unit"] == base_unit:
                    bucket["totalValue"] += base_value
                else:
                    bucket.setdefault("unmergedQuantities", []).append({"value": value, "unit": unit})
                if name and name not in bucket["originalNames"]:
                    bucket["originalNames"].append(name)
    for bucket in aggregated.values():
        bucket["displayQuantity"] = _format_quantity(bucket["totalValue"], bucket["unit"])
        bucket["totalValue"] = round(bucket["totalValue"], 2)
    return aggregated


def price_grocery_buckets(
    buckets: Dict[str, Dict[str, Any]],
    *,
    weekly_budget_php: Optional[float] = None,
    pricing_context: Optional[price_catalog.PricingContext] = None,
) -> Dict[str, Any]:
    """Price an already aggregated grocery list with the backend price catalog."""
    context = pricing_context or price_catalog.create_pricing_context()
    priced_items: List[Dict[str, Any]] = []
    total_php = 0
    for key, bucket in sorted((buckets or {}).items(), key=lambda item: str(item[1].get("name") or item[0]).lower()):
        name = str(bucket.get("name") or key).strip()
        quantity = str(bucket.get("displayQuantity") or "").strip()
        estimate = price_catalog.estimate_price_explained(
            name,
            quantity,
            include_safety_buffer=True,
            pricing_context=context,
            clamp_quantity=False,
        )
        price_php = int(estimate.price_php)
        total_php += price_php
        priced_items.append(
            {
                "key": key,
                "name": name,
                "quantity": quantity,
                "estimatedCostPhp": price_php,
                "category": estimate.category,
                "source": estimate.source,
                "sourceLabel": estimate.source_label,
                "confidence": estimate.confidence,
                "originalNames": list(bucket.get("originalNames") or []),
            }
        )

    budget_value = float(weekly_budget_php or 0.0)
    has_budget = budget_value > 0
    budget_delta = int(round(budget_value - total_php)) if has_budget else None
    return {
        "authority": "backend_aggregated_grocery",
        "budgetAuthority": "backend_aggregated_grocery",
        "pricingAuthority": "reviewed_market_price_rules",
        "estimatedTotalPhp": int(total_php),
        "finalGroceryEstimatePhp": int(total_php),
        "weeklyBudgetPhp": int(round(budget_value)) if has_budget else None,
        "userBudgetPhp": int(round(budget_value)) if has_budget else None,
        "withinBudget": bool(total_php <= budget_value) if has_budget else None,
        "budgetDeltaPhp": budget_delta,
        "budgetGapPhp": budget_delta,
        "displayedEstimateSource": "backend_aggregated_grocery",
        "itemCount": len(priced_items),
        "items": priced_items,
    }


def _quantity_scale(ingredient: Any) -> float:
    if not isinstance(ingredient, dict):
        return 1.0
    raw = ingredient.get("scale", ingredient.get("quantityScale", 1.0))
    try:
        scale = float(raw)
    except Exception:
        scale = 1.0
    return max(0.0, scale)


def _canonical_name(raw: str) -> Tuple[str, str]:
    cleaned = QTY_RE.sub(" ", raw or "").lower()
    raw_tokens = [
        _normalize_token(tok)
        for tok in re.sub(r"[^a-z0-9]+", " ", cleaned).split()
        if tok
    ]
    normalized_all = [LOCAL_SYNONYMS.get(tok, tok) for tok in raw_tokens]
    joined_all = " ".join(normalized_all)
    for phrase in sorted(KNOWN_INGREDIENT_PHRASES, key=lambda item: (len(item.split()), len(item)), reverse=True):
        if re.search(rf"(^|\s){re.escape(phrase)}(\s|$)", joined_all):
            selected_phrase = LOCAL_SYNONYMS.get(phrase, phrase)
            return selected_phrase, _display_name(selected_phrase)
    tokens = [
        tok
        for tok in normalized_all
        if tok and not tok.isdigit() and tok not in DESCRIPTORS and tok not in COUNT_UNIT_WORDS
    ]
    if not tokens:
        return "", ""
    selected = next((tok for tok in tokens if tok in KNOWN_INGREDIENTS), tokens[-1])
    selected = LOCAL_SYNONYMS.get(selected, selected)
    return selected, _display_name(selected)


def _parse_source_quantity(name: str, quantity: str) -> Tuple[float, str]:
    text = f"{quantity} {name}".strip()
    value, unit = _parse_quantity(text)
    if value is not None and unit:
        return float(value), UNIT_ALIASES.get(unit, unit)
    match = QTY_RE.search(text)
    if match:
        parsed, parsed_unit = _parse_quantity(match.group(0))
        if parsed is not None and parsed_unit:
            return float(parsed), UNIT_ALIASES.get(parsed_unit, parsed_unit)
    leading = LEADING_COUNT_RE.search(text)
    if leading:
        first = price_catalog._parse_number(leading.group("first"))
        second = price_catalog._parse_number(leading.group("second") or "")
        candidates = [value for value in (first, second) if value is not None]
        if candidates:
            return float(max(candidates)), "piece"
    return 1.0, "piece"


def _to_base_quantity(key: str, value: float, unit: str, raw_name: str = "") -> Tuple[float, str]:
    unit = UNIT_ALIASES.get(unit, unit)
    raw_lower = (raw_name or "").lower()
    cooked_factor = (
        COOKED_DRY_EQUIVALENT_FACTOR.get(key)
        if any(marker in raw_lower for marker in COOKED_DRY_MARKERS)
        else None
    )
    if unit == "kg":
        grams = value * 1000.0
        return _apply_cooked_dry_equivalent(key, grams, cooked_factor), "g"
    if unit == "g":
        return _apply_cooked_dry_equivalent(key, value, cooked_factor), "g"
    if unit == "lb":
        grams = value * 453.592
        return _apply_cooked_dry_equivalent(key, grams, cooked_factor), "g"
    if unit == "oz":
        grams = value * 28.3495
        return _apply_cooked_dry_equivalent(key, grams, cooked_factor), "g"
    if unit == "l":
        return value * 1000.0, "ml"
    if unit == "quart":
        return value * 946.353, "ml"
    if unit == "ml":
        return value, "ml"
    if unit == "cup":
        if _is_liquid_volume_key(key):
            return value * 240.0, "ml"
        grams_per_cup = _cup_grams_for_key(key, cooked=bool(cooked_factor))
        grams = value * grams_per_cup
        return grams, "g"
    if unit == "tbsp":
        if _is_liquid_volume_key(key):
            return value * 15.0, "ml"
        grams = value * _spoon_grams_for_key(key, 15.0, cooked=bool(cooked_factor))
        return grams, "g"
    if unit == "tsp":
        if _is_liquid_volume_key(key):
            return value * 5.0, "ml"
        grams = value * _spoon_grams_for_key(key, 5.0, cooked=bool(cooked_factor))
        return grams, "g"
    grams = PIECE_GRAMS.get((key, unit), 100.0)
    return value * grams, "g"


def _format_quantity(value: float, unit: str) -> str:
    if unit == "g" and value >= 1000:
        return f"{_pretty_number(value / 1000.0)} kg"
    if unit == "ml" and value >= 1000:
        return f"{_pretty_number(value / 1000.0)} L"
    return f"{_pretty_number(value)} {unit}"


def _pretty_number(value: float) -> str:
    rounded = round(value, 1 if value >= 10 else 2)
    return str(int(rounded)) if rounded == int(rounded) else str(rounded)


def _display_name(key: str) -> str:
    return DISPLAY_NAME_OVERRIDES.get(key, key.title())


def _is_liquid_volume_key(key: str) -> bool:
    return key in LIQUID_VOLUME_KEYS


def _cup_grams_for_key(key: str, *, cooked: bool) -> float:
    if cooked and key in COOKED_DRY_EQUIVALENT_FACTOR:
        return CUP_GRAMS.get(key, 80.0)
    return CUP_GRAMS.get(key, 240.0)


def _spoon_grams_for_key(key: str, default_grams: float, *, cooked: bool) -> float:
    if cooked and key in COOKED_DRY_EQUIVALENT_FACTOR:
        return _cup_grams_for_key(key, cooked=True) * (default_grams / 240.0)
    grams_per_cup = CUP_GRAMS.get(key)
    if grams_per_cup is None:
        return default_grams
    return grams_per_cup * (default_grams / 240.0)


def _apply_cooked_dry_equivalent(key: str, grams: float, cooked_factor: Optional[float]) -> float:
    if cooked_factor is None:
        return grams
    return grams * cooked_factor
