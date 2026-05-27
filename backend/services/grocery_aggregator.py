import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from price_catalog import _parse_quantity  # noqa: E402
from database import _normalize_token  # noqa: E402


LOCAL_SYNONYMS = {
    "bawang": "garlic",
    "itlog": "egg",
    "sibuyas": "onion",
    "kamatis": "tomato",
    "luya": "ginger",
    "manok": "chicken",
    "baboy": "pork",
    "bigas": "rice",
    "toyo": "soy sauce",
    "suka": "vinegar",
    "patis": "fish sauce",
}

DESCRIPTORS = {
    "fresh",
    "minced",
    "chopped",
    "sliced",
    "diced",
    "crushed",
    "whole",
    "small",
    "medium",
    "large",
    "peeled",
    "grated",
}

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
    "shrimp",
    "rice",
    "milk",
    "vinegar",
    "soy",
    "sauce",
    "oil",
    "salt",
    "pepper",
}

QTY_RE = re.compile(
    r"(?P<num>\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*"
    r"(?P<unit>kg|kilo|kilogram|g|gram|grams|lb|lbs|oz|ml|l|liter|litre|cup|cups|tbsp|tablespoon|tablespoons|"
    r"tsp|teaspoon|teaspoons|piece|pieces|piraso|pc|pcs|clove|cloves|bunch|bunches|tali|stalk|stalks|"
    r"can|cans|pack|packs|head|heads)",
    re.IGNORECASE,
)

UNIT_ALIASES = {
    "kilo": "kg",
    "kilogram": "kg",
    "gram": "g",
    "grams": "g",
    "lbs": "lb",
    "liter": "l",
    "litre": "l",
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
    "cans": "can",
    "packs": "pack",
    "heads": "head",
}

PIECE_GRAMS = {
    ("garlic", "clove"): 5.0,
    ("garlic", "head"): 45.0,
    ("garlic", "piece"): 5.0,
    ("egg", "piece"): 55.0,
    ("onion", "piece"): 110.0,
    ("tomato", "piece"): 90.0,
    ("ginger", "piece"): 20.0,
    ("banana", "piece"): 100.0,
    ("chicken", "piece"): 180.0,
    ("pork", "piece"): 150.0,
    ("fish", "piece"): 180.0,
    ("pechay", "bunch"): 180.0,
    ("kangkong", "bunch"): 180.0,
    ("malunggay", "bunch"): 80.0,
}


def aggregate_grocery_list(plan: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    """Consolidate a planned week into canonical grocery buckets."""
    aggregated: Dict[str, Dict[str, Any]] = {}
    for day in plan or []:
        for meal in day.get("meals", []) or []:
            for ingredient in meal.get("ingredients", []) or []:
                name = str(ingredient.get("name", "") if isinstance(ingredient, dict) else ingredient)
                quantity = str(ingredient.get("quantity", "") if isinstance(ingredient, dict) else "")
                key, display_name = _canonical_name(name)
                if not key:
                    continue
                value, unit = _parse_source_quantity(name, quantity)
                base_value, base_unit = _to_base_quantity(key, value, unit)
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


def _canonical_name(raw: str) -> Tuple[str, str]:
    cleaned = QTY_RE.sub(" ", raw or "").lower()
    tokens = [tok for tok in re.sub(r"[^a-z0-9]+", " ", cleaned).split() if tok and tok not in DESCRIPTORS]
    if not tokens:
        return "", ""
    normalized = [LOCAL_SYNONYMS.get(_normalize_token(tok), _normalize_token(tok)) for tok in tokens]
    joined = " ".join(normalized)
    for phrase in ("soy sauce", "fish sauce", "coconut milk"):
        if phrase in joined:
            return phrase, phrase.title()
    selected = next((tok for tok in normalized if tok in KNOWN_INGREDIENTS), normalized[-1])
    selected = LOCAL_SYNONYMS.get(selected, selected)
    return selected, selected.title()


def _parse_source_quantity(name: str, quantity: str) -> Tuple[float, str]:
    value, unit = _parse_quantity(f"{quantity} {name}".strip())
    if value is not None and unit:
        return float(value), UNIT_ALIASES.get(unit, unit)
    match = QTY_RE.search(f"{quantity} {name}")
    if match:
        parsed, parsed_unit = _parse_quantity(match.group(0))
        if parsed is not None and parsed_unit:
            return float(parsed), UNIT_ALIASES.get(parsed_unit, parsed_unit)
    return 1.0, "piece"


def _to_base_quantity(key: str, value: float, unit: str) -> Tuple[float, str]:
    unit = UNIT_ALIASES.get(unit, unit)
    if unit == "kg":
        return value * 1000.0, "g"
    if unit == "g":
        return value, "g"
    if unit == "lb":
        return value * 453.592, "g"
    if unit == "oz":
        return value * 28.3495, "g"
    if unit == "l":
        return value * 1000.0, "ml"
    if unit == "ml":
        return value, "ml"
    if unit == "cup":
        return value * 240.0, "g"
    if unit == "tbsp":
        return value * 15.0, "g"
    if unit == "tsp":
        return value * 5.0, "g"
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
