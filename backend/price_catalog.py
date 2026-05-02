import re
import os
import time
import datetime
from dataclasses import dataclass
from typing import List, Dict, Optional, Tuple
import database


@dataclass
class PriceRule:
    keywords: List[str]
    price_php: int
    category: str
    unit: Optional[str] = None  # kg, l, piece


_RULES = [
    PriceRule(["egg", "itlog"], 7, "Eggs & Dairy", "piece"),
    PriceRule(["milk", "gatas"], 90, "Eggs & Dairy", "l"),
    PriceRule(["cheese", "keso"], 300, "Eggs & Dairy", "kg"),
    PriceRule(["yogurt"], 60, "Eggs & Dairy", "piece"),
    PriceRule(["gata", "coconut milk"], 70, "Eggs & Dairy", "l"),
    PriceRule(["rice", "bigas"], 60, "Dry Goods", "kg"),
    PriceRule(["oat"], 140, "Dry Goods", "kg"),
    PriceRule(["bread", "tinapay"], 80, "Dry Goods", "piece"),
    PriceRule(["pasta", "noodles", "bihon", "miki", "pancit"], 90, "Dry Goods", "kg"),
    PriceRule(["flour"], 60, "Dry Goods", "kg"),
    PriceRule(["chicken", "manok"], 180, "Meat/Seafood", "kg"),
    PriceRule(["beef"], 320, "Meat/Seafood", "kg"),
    PriceRule(["pork", "liempo", "baboy"], 260, "Meat/Seafood", "kg"),
    PriceRule(["fish", "tilapia", "bangus", "salmon", "galunggong"], 220, "Meat/Seafood", "kg"),
    PriceRule(["tuna", "sardines"], 35, "Canned/Packaged", "piece"),
    PriceRule(["shrimp", "hipon"], 300, "Meat/Seafood", "kg"),
    PriceRule(["tomato", "kamatis"], 60, "Produce", "kg"),
    PriceRule(["onion", "sibuyas"], 80, "Produce", "kg"),
    PriceRule(["garlic", "bawang"], 120, "Produce", "kg"),
    PriceRule(["carrot"], 70, "Produce", "kg"),
    PriceRule(["cabbage", "repolyo"], 55, "Produce", "kg"),
    PriceRule(["pechay", "spinach", "kale", "malunggay", "kangkong"], 60, "Produce", "kg"),
    PriceRule(["okra", "ampalaya", "talong", "sayote", "kalabasa"], 70, "Produce", "kg"),
    PriceRule(["sili", "chili"], 140, "Produce", "kg"),
    PriceRule(["banana"], 60, "Produce", "kg"),
    PriceRule(["apple"], 120, "Produce", "kg"),
    PriceRule(["orange"], 80, "Produce", "kg"),
    PriceRule(["ginger", "luya"], 140, "Produce", "kg"),
    PriceRule(["oil", "olive", "coconut"], 120, "Spices & Condiments", "l"),
    PriceRule(["soy", "toyo", "sauce", "vinegar", "suka", "patis"], 40, "Spices & Condiments", "piece"),
    PriceRule(["salt", "asin", "pepper", "paminta", "spice"], 20, "Spices & Condiments", "piece"),
    PriceRule(["coffee", "tea"], 90, "Beverages", "piece"),
    PriceRule(["juice", "soda"], 40, "Beverages", "piece"),
    PriceRule(["water"], 20, "Beverages", "piece"),
    PriceRule(["canned", "packaged", "instant"], 45, "Canned/Packaged", "piece"),
]


_UNIT_ALIASES = {
    "kg": "kg",
    "kilo": "kg",
    "kilogram": "kg",
    "g": "g",
    "gram": "g",
    "grams": "g",
    "lb": "lb",
    "lbs": "lb",
    "pound": "lb",
    "pounds": "lb",
    "oz": "oz",
    "ml": "ml",
    "l": "l",
    "liter": "l",
    "litre": "l",
    "cup": "cup",
    "cups": "cup",
    "tbsp": "tbsp",
    "tablespoon": "tbsp",
    "tablespoons": "tbsp",
    "tsp": "tsp",
    "teaspoon": "tsp",
    "teaspoons": "tsp",
    "piece": "piece",
    "pieces": "piece",
    "pc": "piece",
    "pcs": "piece",
    "clove": "piece",
    "cloves": "piece",
}

_CATEGORY_DEFAULT_UNIT = {
    "Meat/Seafood": "kg",
    "Produce": "kg",
    "Dry Goods": "kg",
    "Eggs & Dairy": "kg",
    "Spices & Condiments": "piece",
    "Canned/Packaged": "piece",
    "Beverages": "l",
    "Others": "piece",
}

_CATEGORY_MULTIPLIER = {
    "Meat/Seafood": 1.0,
    "Produce": 0.6,
    "Dry Goods": 0.8,
    "Eggs & Dairy": 0.9,
    "Spices & Condiments": 0.3,
    "Canned/Packaged": 0.8,
    "Beverages": 0.7,
    "Others": 0.7,
}

_PIECE_WEIGHT_KG = {
    "Meat/Seafood": 0.20,
    "Produce": 0.12,
    "Eggs & Dairy": 0.06,
    "Dry Goods": 0.10,
    "Spices & Condiments": 0.05,
    "Canned/Packaged": 0.20,
    "Beverages": 0.50,
    "Others": 0.10,
}


def _category_averages() -> Dict[str, int]:
    grouped: Dict[str, List[int]] = {}
    for rule in _RULES:
        grouped.setdefault(rule.category, []).append(rule.price_php)
    return {k: int(sum(v) / max(1, len(v))) for k, v in grouped.items()}


_CATEGORY_AVG = _category_averages()
_OVERRIDE_CACHE_TTL_SECONDS = int(os.getenv("PCOSINA_PRICE_RULE_CACHE_TTL_SECONDS", "30"))
_override_cache: Dict[str, object] = {"loaded_at": 0.0, "rules": None}


def infer_category(name: str) -> str:
    raw = (name or "").lower().strip()
    if not raw:
        return "Others"
    if any(tok in raw for tok in [
        "lettuce", "spinach", "cabbage", "carrot", "broccoli", "kale", "tomato", "onion", "garlic", "pepper",
        "pechay", "ampalaya", "okra", "eggplant", "talong", "sayote", "squash", "kalabasa", "ginger", "luya",
        "gabi", "kamote", "cucumber", "pipino", "malunggay", "kangkong", "sili", "chili",
        "banana", "apple", "orange", "mango", "grape", "papaya", "pineapple", "strawberry", "melon", "calamansi"
    ]):
        return "Produce"
    if any(tok in raw for tok in [
        "chicken", "manok", "beef", "pork", "baboy", "liempo", "fish", "salmon", "tuna", "shrimp", "tilapia",
        "meat", "bangus", "sardine", "galunggong", "tocino", "longganisa"
    ]):
        return "Meat/Seafood"
    if any(tok in raw for tok in ["egg", "itlog", "milk", "gatas", "cheese", "keso", "yogurt", "butter", "cream"]):
        return "Eggs & Dairy"
    if any(tok in raw for tok in [
        "rice", "bigas", "oat", "bread", "pasta", "noodles", "bihon", "miki", "pancit", "flour",
        "grains", "cereal", "quinoa", "barley", "corn", "frozen", "dried", "beans", "lentils"
    ]):
        return "Dry Goods"
    if any(tok in raw for tok in [
        "salt", "pepper", "soy", "toyo", "sauce", "vinegar", "suka", "patis", "spice", "condiment", "oil", "sugar", "honey", "bagoong"
    ]):
        return "Spices & Condiments"
    if any(tok in raw for tok in ["canned", "packaged", "instant", "biscuit", "cracker", "chips", "snack", "noodles"]):
        return "Canned/Packaged"
    if any(tok in raw for tok in ["juice", "soda", "coffee", "tea", "water", "milk tea"]):
        return "Beverages"
    return "Others"


def _parse_fraction(text: str) -> Optional[float]:
    try:
        if "/" in text:
            num, den = text.split("/", 1)
            return float(num) / float(den)
    except Exception:
        return None
    return None


def _parse_number(text: str) -> Optional[float]:
    text = text.strip()
    if not text:
        return None
    if " " in text and "/" in text:
        parts = text.split()
        if len(parts) >= 2:
            whole = float(parts[0])
            frac = _parse_fraction(parts[1])
            if frac is not None:
                return whole + frac
    if "/" in text:
        return _parse_fraction(text)
    try:
        return float(text)
    except Exception:
        return None


_QTY_PATTERN = re.compile(
    r"(?P<num>\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*(?P<unit>kg|kilo|kilogram|g|gram|grams|lb|lbs|pound|pounds|oz|ml|l|liter|litre|cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|piece|pieces|pc|pcs|clove|cloves)"
)


def _parse_quantity(text: str) -> Tuple[Optional[float], Optional[str]]:
    if not text:
        return None, None
    match = _QTY_PATTERN.search(text.lower())
    if not match:
        return None, None
    num_raw = match.group("num")
    unit_raw = match.group("unit")
    qty = _parse_number(num_raw)
    unit = _UNIT_ALIASES.get(unit_raw, unit_raw)
    return qty, unit


def _unit_to_kg(value: float, unit: str) -> Optional[float]:
    if unit == "kg":
        return value
    if unit == "g":
        return value / 1000.0
    if unit == "lb":
        return value / 2.2046
    if unit == "oz":
        return value / 35.274
    if unit == "cup":
        return value * 0.25
    if unit == "tbsp":
        return value * 0.015
    if unit == "tsp":
        return value * 0.005
    return None


def _unit_to_l(value: float, unit: str) -> Optional[float]:
    if unit == "l":
        return value
    if unit == "ml":
        return value / 1000.0
    if unit == "cup":
        return value * 0.24
    if unit == "tbsp":
        return value * 0.015
    if unit == "tsp":
        return value * 0.005
    return None


def _quantity_factor(value: Optional[float], unit: Optional[str], target_unit: str, category: str) -> float:
    if value is None or unit is None:
        return 1.0
    unit = _UNIT_ALIASES.get(unit, unit)
    if target_unit == "kg":
        kg = _unit_to_kg(value, unit)
        if kg is None and unit == "piece":
            kg = value * _PIECE_WEIGHT_KG.get(category, 0.1)
        if kg is None:
            return 1.0
        return kg
    if target_unit == "l":
        liters = _unit_to_l(value, unit)
        if liters is None:
            return 1.0
        return liters
    if target_unit == "piece":
        if unit == "piece":
            return value
        kg = _unit_to_kg(value, unit)
        if kg is None:
            return 1.0
        piece_weight = _PIECE_WEIGHT_KG.get(category, 0.1)
        return max(0.1, kg / piece_weight)
    return 1.0


def _clamp_factor(value: float, category: str) -> float:
    min_factor = 0.1
    max_factor = 2.5 if category in ("Meat/Seafood", "Dry Goods") else 2.0
    return max(min_factor, min(max_factor, value))


def _rule_for_name(name: str) -> Optional[PriceRule]:
    lower = (name or "").lower()
    for rule in _active_rules():
        if any(keyword in lower for keyword in rule.keywords):
            return rule
    return None


def invalidate_override_cache() -> None:
    _override_cache["loaded_at"] = 0.0
    _override_cache["rules"] = None


def _load_override_rules() -> List[PriceRule]:
    current = float(time.time())
    cached_rules = _override_cache.get("rules")
    loaded_at = float(_override_cache.get("loaded_at") or 0.0)
    if isinstance(cached_rules, list) and (current - loaded_at) < _OVERRIDE_CACHE_TTL_SECONDS:
        return cached_rules
    rules: List[PriceRule] = []
    try:
        import database  # noqa: WPS433

        for item in database.list_active_price_rules(limit=500):
            keywords = [str(keyword).strip().lower() for keyword in (item.get("keywords") or []) if str(keyword).strip()]
            if not keywords:
                continue
            rules.append(
                PriceRule(
                    keywords=keywords,
                    price_php=max(1, int(item.get("pricePhp") or 0)),
                    category=str(item.get("category") or "Others"),
                    unit=(str(item.get("unit") or "").strip() or None),
                )
            )
    except Exception:
        rules = []
    _override_cache["loaded_at"] = current
    _override_cache["rules"] = rules
    return rules


def _active_rules() -> List[PriceRule]:
    """
    Returns the list of price rules to be used for estimation.
    Prioritizes database-driven overrides (SRP) before falling back to
    hardcoded static rules.
    """
    overrides = _load_override_rules()
    if not overrides:
        return _RULES
    # Merge overrides first so they are checked before static defaults
    return overrides + _RULES


def estimate_price_detail(name: str, quantity_text: str = "") -> Tuple[int, str]:
    rule = _rule_for_name(name)
    category = rule.category if rule else infer_category(name)
    base_price = rule.price_php if rule else _CATEGORY_AVG.get(category, 50)
    target_unit = rule.unit or _CATEGORY_DEFAULT_UNIT.get(category, "piece")
    qty_value, qty_unit = _parse_quantity(quantity_text)

    factor = _quantity_factor(qty_value, qty_unit, target_unit, category)
    factor = _clamp_factor(factor, category)

    # Apply seasonal multiplier
    current_month = datetime.datetime.now().month
    seasonal_multiplier = database.get_market_multiplier(category, current_month)

    price = base_price * factor
    price *= _CATEGORY_MULTIPLIER.get(category, 0.7)
    price *= seasonal_multiplier

    price = max(5.0, price)
    return int(round(price)), category



def estimate_price(name: str) -> int:
    price, _ = estimate_price_detail(name)
    return price


def estimate_recipe_cost(ingredients: List[dict]) -> int:
    if not ingredients:
        return 0
    total = 0.0
    for ing in ingredients:
        name = ""
        qty = ""
        if isinstance(ing, dict):
            name = str(ing.get("name", ""))
            qty = str(ing.get("quantity", "") or "")
        else:
            name = str(ing)
        if name.strip():
            price, _ = estimate_price_detail(name, qty)
            total += price
    total *= 0.75  # scale to avoid overestimation for multi-portion recipes
    total *= 1.10  # Add 10% "Inflation/Safety Buffer" as per Advanced Pricing Plan V2
    total = max(30.0, min(450.0, total))
    return int(round(total))
