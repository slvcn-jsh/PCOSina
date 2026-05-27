import re
import os
import time
import datetime
from dataclasses import dataclass, field
from typing import Any, List, Dict, Optional, Tuple
import database


@dataclass
class PriceRule:
    keywords: List[str]
    price_php: int
    category: str
    unit: Optional[str] = None  # kg, l, piece
    source: str = "static"
    source_label: str = "Static PCOSina baseline"
    confidence: str = "medium"


@dataclass
class PriceEstimate:
    price_php: int
    category: str
    source: str
    source_label: str
    confidence: str
    base_price_php: int
    target_unit: str
    quantity_value: Optional[float]
    quantity_unit: Optional[str]
    quantity_factor: float
    market_multiplier: float
    tingi_multiplier: float
    safety_buffer_multiplier: float
    matched_keywords: List[str]


def _normalize_month_index(month_index: Optional[int]) -> int:
    try:
        month = int(month_index or datetime.datetime.now().month)
    except Exception:
        month = datetime.datetime.now().month
    return month if 1 <= month <= 12 else datetime.datetime.now().month


def _normalize_category_key(category: str) -> str:
    value = str(category or "").strip()
    if not value:
        return "Others"
    known_categories = globals().get("_CATEGORY_DEFAULT_UNIT", {})
    if isinstance(known_categories, dict):
        for known in known_categories.keys():
            if value.casefold() == str(known).casefold():
                return str(known)
    return value


@dataclass
class PricingContext:
    month_index: Optional[int] = None
    _market_multiplier_cache: Dict[Tuple[str, int], float] = field(default_factory=dict)
    _ingredient_price_cache: Dict[Tuple[str, str, bool, int], PriceEstimate] = field(default_factory=dict)
    _preloaded_months: set[int] = field(default_factory=set)
    _preload_failed_months: set[int] = field(default_factory=set)
    market_multiplier_db_calls: int = 0
    market_multiplier_cache_hits: int = 0
    market_multiplier_cache_misses: int = 0
    ingredient_price_cache_hits: int = 0
    ingredient_price_cache_misses: int = 0
    recipe_cost_estimates: int = 0
    ingredient_cost_estimates: int = 0
    price_cost_estimation_ms: int = 0

    def _default_market_multiplier(self, category_key: str, month: int) -> float:
        return _DEFAULT_SEASONAL_MULTIPLIER.get(category_key, {}).get(month, 1.0)

    def _preload_market_multipliers(self, month: int) -> bool:
        if month in self._preloaded_months:
            return True
        if month in self._preload_failed_months:
            return False
        try:
            self.market_multiplier_db_calls += 1
            rows = database.list_market_multipliers_for_month(month)
        except Exception:
            self._preload_failed_months.add(month)
            return False
        for raw_category, raw_multiplier in (rows or {}).items():
            category_key = _normalize_category_key(raw_category)
            try:
                multiplier = float(raw_multiplier or 1.0)
            except Exception:
                multiplier = 1.0
            self._market_multiplier_cache[(category_key, month)] = multiplier
        self._preloaded_months.add(month)
        return True

    def market_multiplier(self, category: str, month_index: Optional[int] = None) -> float:
        month = _normalize_month_index(month_index or self.month_index)
        category_key = _normalize_category_key(category)
        cache_key = (category_key, month)
        if cache_key in self._market_multiplier_cache:
            self.market_multiplier_cache_hits += 1
            return self._market_multiplier_cache[cache_key]

        self.market_multiplier_cache_misses += 1
        if self._preload_market_multipliers(month):
            if cache_key in self._market_multiplier_cache:
                return self._market_multiplier_cache[cache_key]
            multiplier = self._default_market_multiplier(category_key, month)
            self._market_multiplier_cache[cache_key] = multiplier
            return multiplier

        self.market_multiplier_db_calls += 1
        try:
            db_multiplier = float(database.get_market_multiplier(category_key, month))
        except Exception:
            db_multiplier = 1.0
        multiplier = db_multiplier if db_multiplier != 1.0 else self._default_market_multiplier(category_key, month)
        self._market_multiplier_cache[cache_key] = multiplier
        return multiplier

    def record_recipe_cost_elapsed(self, started_at: float) -> None:
        self.recipe_cost_estimates += 1
        self.price_cost_estimation_ms += max(0, int((time.time() - started_at) * 1000))

    def snapshot(self) -> Dict[str, int]:
        return {
            "marketMultiplierDbCalls": int(self.market_multiplier_db_calls),
            "marketMultiplierCacheHits": int(self.market_multiplier_cache_hits),
            "marketMultiplierCacheMisses": int(self.market_multiplier_cache_misses),
            "ingredientPriceCacheHits": int(self.ingredient_price_cache_hits),
            "ingredientPriceCacheMisses": int(self.ingredient_price_cache_misses),
            "priceCostEstimationMs": int(self.price_cost_estimation_ms),
            "recipeCostEstimateCount": int(self.recipe_cost_estimates),
            "ingredientPriceEstimateCount": int(self.ingredient_cost_estimates),
            "recipeCostEstimates": int(self.recipe_cost_estimates),
            "ingredientCostEstimates": int(self.ingredient_cost_estimates),
            "distinctMarketMultiplierKeys": int(len(self._market_multiplier_cache)),
            "distinctIngredientPriceKeys": int(len(self._ingredient_price_cache)),
        }


def create_pricing_context(month_index: Optional[int] = None) -> PricingContext:
    return PricingContext(month_index=_normalize_month_index(month_index))


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
    "clove": "clove",
    "cloves": "clove",
    "bunch": "bunch",
    "bunches": "bunch",
    "tali": "bunch",
    "stalk": "stalk",
    "stalks": "stalk",
    "can": "can",
    "cans": "can",
    "pack": "pack",
    "packs": "pack",
    "head": "head",
    "heads": "head",
}

_CATEGORY_DEFAULT_UNIT = {
    "Meat/Seafood": "kg",
    "Produce": "kg",
    "Dry Goods": "kg",
    "Eggs & Dairy": "piece",
    "Spices & Condiments": "piece",
    "Canned/Packaged": "piece",
    "Beverages": "piece",
    "Others": "piece",
}

_CATEGORY_MULTIPLIER = {
    "Meat/Seafood": 0.85,
    "Produce": 0.75,
    "Dry Goods": 0.8,
    "Eggs & Dairy": 0.85,
    "Spices & Condiments": 0.7,
    "Canned/Packaged": 0.8,
    "Beverages": 0.8,
    "Others": 0.75,
}

_DEFAULT_SEASONAL_MULTIPLIER = {
    "Produce": {
        6: 1.06,
        7: 1.10,
        8: 1.12,
        9: 1.12,
        10: 1.08,
        11: 1.04,
    },
    "Meat/Seafood": {
        7: 1.04,
        8: 1.06,
        9: 1.06,
        10: 1.04,
    },
}

_VOLATILE_INGREDIENT_TOKENS = {
    "chili",
    "sili",
    "calamansi",
    "tomato",
    "kamatis",
    "onion",
    "sibuyas",
    "garlic",
    "bawang",
    "fish",
    "tilapia",
    "bangus",
    "galunggong",
}

_PIECE_WEIGHT_KG = {
    "Meat/Seafood": 0.15,
    "Produce": 0.12,
    "Eggs & Dairy": 0.06,
    "Dry Goods": 0.10,
    "Spices & Condiments": 0.05,
    "Canned/Packaged": 0.18,
    "Beverages": 0.25,
    "Others": 0.10,
}

_INGREDIENT_PIECE_WEIGHT_KG = {
    ("garlic", "clove"): 0.005,
    ("garlic", "piece"): 0.005,
    ("garlic", "head"): 0.045,
    ("onion", "piece"): 0.11,
    ("tomato", "piece"): 0.09,
    ("ginger", "piece"): 0.02,
    ("egg", "piece"): 0.055,
    ("pechay", "bunch"): 0.18,
    ("kangkong", "bunch"): 0.18,
    ("malunggay", "bunch"): 0.08,
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
    r"(?P<num>\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*(?P<unit>kg|kilo|kilogram|g|gram|grams|lb|lbs|pound|pounds|oz|ml|l|liter|litre|cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|piece|pieces|pc|pcs|clove|cloves|bunch|bunches|tali|stalk|stalks|can|cans|pack|packs|head|heads)"
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


def _parse_rule_notes(notes: Any) -> Dict[str, str]:
    raw = str(notes or "").strip()
    if not raw:
        return {}
    parsed: Dict[str, str] = {}
    for chunk in raw.replace("\n", ";").split(";"):
        if "=" not in chunk:
            continue
        key, value = chunk.split("=", 1)
        key = key.strip().lower()
        value = value.strip()
        if key and value:
            parsed[key] = value
    return parsed


def _rule_metadata_from_notes(notes: Any) -> Tuple[str, str, str]:
    parsed = _parse_rule_notes(notes)
    source = parsed.get("source", "").strip() or "database"
    effective = parsed.get("effective", "").strip()
    confidence = parsed.get("confidence", "").strip().lower()
    raw_notes = str(notes or "")
    if "dti" in raw_notes.lower() or "srp" in raw_notes.lower():
        source = "dti_srp"
        confidence = confidence or "high"
    confidence = confidence if confidence in {"high", "medium", "low"} else "medium"
    if source == "dti_srp":
        source_label = "DTI SRP baseline"
    elif source == "admin_market":
        source_label = "Admin market override"
    else:
        source_label = "Database price rule"
    if effective:
        source_label = f"{source_label} ({effective})"
    return source, source_label, confidence


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


def _ingredient_weight_key(name: str) -> str:
    lower = (name or "").lower()
    if "garlic" in lower or "bawang" in lower:
        return "garlic"
    if "onion" in lower or "sibuyas" in lower:
        return "onion"
    if "tomato" in lower or "kamatis" in lower:
        return "tomato"
    if "ginger" in lower or "luya" in lower:
        return "ginger"
    if "egg" in lower or "itlog" in lower:
        return "egg"
    if "pechay" in lower:
        return "pechay"
    if "kangkong" in lower:
        return "kangkong"
    if "malunggay" in lower:
        return "malunggay"
    return ""


def _piece_weight_for(name: str, category: str, unit: str) -> float:
    ingredient = _ingredient_weight_key(name)
    return _INGREDIENT_PIECE_WEIGHT_KG.get((ingredient, unit), _PIECE_WEIGHT_KG.get(category, 0.1))


def _quantity_factor(value: Optional[float], unit: Optional[str], target_unit: str, category: str, name: str = "") -> float:
    if value is None or unit is None:
        return 1.0
    unit = _UNIT_ALIASES.get(unit, unit)
    if target_unit == "kg":
        kg = _unit_to_kg(value, unit)
        if kg is None and unit in {"piece", "clove", "bunch", "stalk", "head"}:
            kg = value * _piece_weight_for(name, category, unit)
        if kg is None:
            return 1.0
        return kg
    if target_unit == "l":
        liters = _unit_to_l(value, unit)
        if liters is None:
            return 1.0
        return liters
    if target_unit == "piece":
        if unit in {"piece", "clove", "bunch", "stalk", "can", "pack", "head"}:
            return value
        kg = _unit_to_kg(value, unit)
        if kg is None:
            return 1.0
        piece_weight = _piece_weight_for(name, category, "piece")
        return max(0.1, kg / piece_weight)
    return 1.0


def _clamp_factor(value: float, category: str) -> float:
    min_factor = 0.02 if category in {"Produce", "Spices & Condiments"} else 0.1
    max_factor = 2.5 if category in ("Meat/Seafood", "Dry Goods") else 2.0
    return max(min_factor, min(max_factor, value))


def _rule_for_name(name: str) -> Optional[PriceRule]:
    lower = (name or "").lower()
    for rule in _active_rules():
        if any(keyword in lower for keyword in rule.keywords):
            return rule
    return None


def _fallback_rule_for_category(category: str) -> PriceRule:
    return PriceRule(
        keywords=[],
        price_php=_CATEGORY_AVG.get(category, 50),
        category=category,
        unit=_CATEGORY_DEFAULT_UNIT.get(category, "piece"),
        source="category_average",
        source_label="Category average fallback",
        confidence="low",
    )


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
            source, source_label, confidence = _rule_metadata_from_notes(item.get("notes"))
            rules.append(
                PriceRule(
                    keywords=keywords,
                    price_php=max(1, int(item.get("pricePhp") or 0)),
                    category=str(item.get("category") or "Others"),
                    unit=(str(item.get("unit") or "").strip() or None),
                    source=source,
                    source_label=source_label,
                    confidence=confidence,
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


def _market_multiplier(
    category: str,
    month_index: Optional[int],
    *,
    pricing_context: Optional[PricingContext] = None,
) -> float:
    if pricing_context is not None:
        return pricing_context.market_multiplier(category, month_index)
    month = _normalize_month_index(month_index)
    category_key = _normalize_category_key(category)
    db_multiplier = 1.0
    try:
        db_multiplier = float(database.get_market_multiplier(category_key, month))
    except Exception:
        db_multiplier = 1.0
    if db_multiplier != 1.0:
        return db_multiplier
    return _DEFAULT_SEASONAL_MULTIPLIER.get(category_key, {}).get(month, 1.0)


def _tingi_multiplier(unit: Optional[str], target_unit: str, quantity_value: Optional[float]) -> float:
    normalized_unit = _UNIT_ALIASES.get(str(unit or ""), str(unit or ""))
    if normalized_unit in {"piece", "clove", "bunch", "stalk", "can", "pack", "head"} and target_unit in {"kg", "l"}:
        return 1.12
    if quantity_value is not None and quantity_value > 0 and quantity_value < 0.25 and target_unit in {"kg", "l"}:
        return 1.08
    return 1.0


def _confidence_for_rule(rule: PriceRule, name: str) -> str:
    if rule.confidence == "high" and any(tok in (name or "").lower() for tok in _VOLATILE_INGREDIENT_TOKENS):
        return "medium"
    return rule.confidence


def estimate_price_explained(
    name: str,
    quantity_text: str = "",
    *,
    month_index: Optional[int] = None,
    include_safety_buffer: bool = False,
    pricing_context: Optional[PricingContext] = None,
) -> PriceEstimate:
    rule = _rule_for_name(name)
    category = rule.category if rule else infer_category(name)
    resolved_rule = rule or _fallback_rule_for_category(category)
    base_price = resolved_rule.price_php
    target_unit = resolved_rule.unit or _CATEGORY_DEFAULT_UNIT.get(category, "piece")
    qty_value, qty_unit = _parse_quantity(f"{quantity_text} {name}".strip())

    factor = _quantity_factor(qty_value, qty_unit, target_unit, category, name)
    factor = _clamp_factor(factor, category)

    seasonal_multiplier = _market_multiplier(category, month_index, pricing_context=pricing_context)
    tingi = _tingi_multiplier(qty_unit, target_unit, qty_value)
    safety = 1.10 if include_safety_buffer else 1.0

    price = base_price * factor
    price *= _CATEGORY_MULTIPLIER.get(category, 0.7)
    price *= seasonal_multiplier
    price *= tingi
    price *= safety

    price = max(5.0, price)
    return PriceEstimate(
        price_php=int(round(price)),
        category=category,
        source=resolved_rule.source,
        source_label=resolved_rule.source_label,
        confidence=_confidence_for_rule(resolved_rule, name),
        base_price_php=base_price,
        target_unit=target_unit,
        quantity_value=qty_value,
        quantity_unit=qty_unit,
        quantity_factor=factor,
        market_multiplier=seasonal_multiplier,
        tingi_multiplier=tingi,
        safety_buffer_multiplier=safety,
        matched_keywords=resolved_rule.keywords,
    )


def estimate_price_detail(name: str, quantity_text: str = "") -> Tuple[int, str]:
    estimate = estimate_price_explained(name, quantity_text)
    return estimate.price_php, estimate.category



def estimate_price(name: str) -> int:
    price, _ = estimate_price_detail(name)
    return price


def estimate_recipe_cost(
    ingredients: List[dict],
    *,
    pricing_context: Optional[PricingContext] = None,
) -> int:
    if not ingredients:
        return 0
    started_at = time.time()
    total = 0.0
    try:
        for ing in ingredients:
            name = ""
            qty = ""
            if isinstance(ing, dict):
                name = str(ing.get("name", ""))
                qty = str(ing.get("quantity", "") or "")
            else:
                name = str(ing)
            if name.strip():
                if pricing_context is not None:
                    pricing_context.ingredient_cost_estimates += 1
                    month = _normalize_month_index(pricing_context.month_index)
                    cache_key = (
                        str(name).strip().casefold(),
                        str(qty).strip().casefold(),
                        True,
                        month,
                    )
                    estimate = pricing_context._ingredient_price_cache.get(cache_key)
                    if estimate is not None:
                        pricing_context.ingredient_price_cache_hits += 1
                        pricing_context.market_multiplier_cache_hits += 1
                    else:
                        pricing_context.ingredient_price_cache_misses += 1
                        estimate = estimate_price_explained(
                            name,
                            qty,
                            include_safety_buffer=True,
                            pricing_context=pricing_context,
                        )
                        pricing_context._ingredient_price_cache[cache_key] = estimate
                else:
                    estimate = estimate_price_explained(
                        name,
                        qty,
                        include_safety_buffer=True,
                        pricing_context=pricing_context,
                    )
                total += estimate.price_php
        total *= 0.90  # recipe-level yield/portion calibration after ingredient-level pricing
        total = max(30.0, min(450.0, total))
        return int(round(total))
    finally:
        if pricing_context is not None:
            pricing_context.record_recipe_cost_elapsed(started_at)
