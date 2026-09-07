"""Canonical ingredient seed data and deterministic text resolution.

This module is additive. Planner filtering, allergen checks, and pricing keep
their current token/keyword fallbacks until canonical coverage is reviewed.
"""

from __future__ import annotations

import re
import hashlib
from dataclasses import dataclass
from fractions import Fraction
from typing import Any, Iterable


@dataclass(frozen=True)
class CanonicalIngredientSeed:
    ingredient_id: str
    canonical_name: str
    category: str
    default_unit: str
    default_form: str = "unspecified"
    quality_status: str = "seeded"


@dataclass(frozen=True)
class IngredientAliasSeed:
    ingredient_id: str
    alias_text: str
    language: str = "en"
    region: str = ""
    confidence: str = "high"
    source: str = "canonical_seed_v1"


@dataclass(frozen=True)
class IngredientPriceSeed:
    ingredient_id: str
    unit: str
    price_php: int
    category: str
    source: str = "static_pcosina_baseline"
    confidence: str = "medium"


@dataclass(frozen=True)
class IngredientMarketPriceSeed:
    ingredient_id: str
    unit: str
    price_php: float
    commodity_label: str
    confidence: str = "high"
    price_min_php: float | None = None
    price_max_php: float | None = None


@dataclass(frozen=True)
class IngredientResolution:
    raw_text: str
    normalized_text: str
    ingredient_id: str | None
    canonical_name: str | None
    status: str
    confidence: str
    matched_alias: str | None
    candidate_ids: tuple[str, ...]
    quantity_value: float | None
    quantity_unit: str | None


@dataclass(frozen=True)
class ProvisionalIngredient:
    ingredient_id: str
    canonical_name: str
    category: str
    default_unit: str
    quality_status: str


CANONICAL_INGREDIENTS: tuple[CanonicalIngredientSeed, ...] = (
    CanonicalIngredientSeed("ing_apple", "apple", "fruit", "kg"),
    CanonicalIngredientSeed("ing_banana", "banana", "fruit", "kg"),
    CanonicalIngredientSeed("ing_bangus", "bangus", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_bangus_fillet", "bangus fillet", "fish_seafood", "kg", "raw_boneless"),
    CanonicalIngredientSeed("ing_beef_generic", "beef", "meat", "kg", "raw"),
    CanonicalIngredientSeed("ing_bitter_melon", "bitter melon", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_bok_choy", "bok choy", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_bread", "bread", "dry_goods", "piece"),
    CanonicalIngredientSeed("ing_broth_generic", "broth or stock", "condiment", "l"),
    CanonicalIngredientSeed("ing_brown_sugar", "brown sugar", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_butter", "butter", "dairy", "kg"),
    CanonicalIngredientSeed("ing_cabbage", "cabbage", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_calamansi", "calamansi", "fruit", "kg", "raw"),
    CanonicalIngredientSeed("ing_calamansi_juice", "calamansi juice", "beverage", "l"),
    CanonicalIngredientSeed("ing_canola_oil", "canola oil", "condiment", "l"),
    CanonicalIngredientSeed("ing_carrot", "carrot", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_cheese_generic", "cheese", "dairy", "kg"),
    CanonicalIngredientSeed("ing_chicken_breast_raw", "chicken breast", "meat", "kg", "raw"),
    CanonicalIngredientSeed("ing_chicken_generic", "chicken", "meat", "kg", "raw"),
    CanonicalIngredientSeed("ing_chicken_thigh_raw", "chicken thigh", "meat", "kg", "raw"),
    CanonicalIngredientSeed("ing_chili", "chili pepper", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_coconut_milk", "coconut milk", "condiment", "l"),
    CanonicalIngredientSeed("ing_coconut_cream", "coconut cream", "condiment", "l"),
    CanonicalIngredientSeed("ing_cornstarch", "cornstarch", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_cooking_oil", "cooking oil", "condiment", "l"),
    CanonicalIngredientSeed("ing_crab", "crab", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_egg", "egg", "egg_dairy", "piece"),
    CanonicalIngredientSeed("ing_eggplant", "eggplant", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_fish_generic", "fish", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_fish_sauce", "fish sauce", "condiment", "piece"),
    CanonicalIngredientSeed("ing_flour", "flour", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_glutinous_rice_flour", "glutinous rice flour", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_galunggong", "galunggong", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_garlic", "garlic", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_ginger", "ginger", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_kangkong", "kangkong", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_malunggay", "malunggay", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_milk", "milk", "dairy", "l"),
    CanonicalIngredientSeed("ing_mussel", "mussel", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_oats", "oats", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_okra", "okra", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_olive_oil", "olive oil", "condiment", "l"),
    CanonicalIngredientSeed("ing_onion_generic", "onion", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_onion_red", "red onion", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_onion_white", "white onion", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_orange", "orange", "fruit", "kg"),
    CanonicalIngredientSeed("ing_papaya", "papaya", "fruit", "kg", "raw"),
    CanonicalIngredientSeed("ing_pasta", "pasta or noodles", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_pepper_black", "black pepper", "spice", "kg"),
    CanonicalIngredientSeed("ing_peppercorn_black", "black peppercorn", "spice", "kg"),
    CanonicalIngredientSeed("ing_peanut", "peanut", "nuts_seeds", "kg"),
    CanonicalIngredientSeed("ing_pork_belly_raw", "pork belly", "meat", "kg", "raw"),
    CanonicalIngredientSeed("ing_pork_generic", "pork", "meat", "kg", "raw"),
    CanonicalIngredientSeed("ing_rice_generic", "rice", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_rice_flour", "rice flour", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_rice_noodles", "rice noodles", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_salmon", "salmon", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_sayote", "sayote", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_shrimp", "shrimp", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_soy_sauce", "soy sauce", "condiment", "piece"),
    CanonicalIngredientSeed("ing_salt", "salt", "spice", "kg"),
    CanonicalIngredientSeed("ing_sugar_white", "white sugar", "dry_goods", "kg"),
    CanonicalIngredientSeed("ing_soybean", "soybean", "legume", "kg"),
    CanonicalIngredientSeed("ing_squid", "squid", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_squash", "squash", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_string_beans", "string beans", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_tilapia", "tilapia", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_tofu", "tofu", "legume", "kg"),
    CanonicalIngredientSeed("ing_tomato", "tomato", "produce", "kg", "raw"),
    CanonicalIngredientSeed("ing_tuna", "tuna", "fish_seafood", "kg", "raw"),
    CanonicalIngredientSeed("ing_vinegar", "vinegar", "condiment", "piece"),
    CanonicalIngredientSeed("ing_bay_leaf", "bay leaf", "spice", "kg"),
    CanonicalIngredientSeed("ing_oyster_sauce", "oyster sauce", "condiment", "piece"),
    CanonicalIngredientSeed("ing_water", "water", "beverage", "l"),
    CanonicalIngredientSeed("ing_yogurt", "yogurt", "dairy", "piece"),
)


def _aliases(ingredient_id: str, *values: str, language: str = "en") -> list[IngredientAliasSeed]:
    return [IngredientAliasSeed(ingredient_id, value, language=language) for value in values]


INGREDIENT_ALIASES: tuple[IngredientAliasSeed, ...] = tuple(
    alias
    for group in (
        _aliases("ing_apple", "apple"),
        _aliases("ing_banana", "banana"),
        _aliases("ing_bangus", "bangus", "milkfish", "milk fish"),
        _aliases(
            "ing_bangus_fillet",
            "bangus fillet",
            "milkfish fillet",
            "boneless bangus",
            "boneless milkfish",
            "deboned bangus",
            "deboned milkfish",
        ),
        _aliases("ing_beef_generic", "beef", "baka", language="fil"),
        _aliases("ing_bitter_melon", "bitter melon", "bitter gourd", "ampalaya"),
        _aliases("ing_bok_choy", "bok choy", "pechay", "napa cabbage", "chinese cabbage"),
        _aliases("ing_bread", "bread", "tinapay"),
        _aliases("ing_broth_generic", "broth", "stock", "sabaw"),
        _aliases("ing_brown_sugar", "brown sugar", "asukal na pula"),
        _aliases("ing_butter", "butter"),
        _aliases("ing_cabbage", "cabbage", "repolyo"),
        _aliases("ing_calamansi", "calamansi"),
        _aliases("ing_calamansi_juice", "calamansi juice"),
        _aliases("ing_canola_oil", "canola oil"),
        _aliases("ing_carrot", "carrot"),
        _aliases("ing_cheese_generic", "cheese", "keso"),
        _aliases("ing_chicken_breast_raw", "chicken breast"),
        _aliases("ing_chicken_generic", "chicken", "manok", "lechon manok"),
        _aliases("ing_chicken_thigh_raw", "chicken thigh", "chicken thighs"),
        _aliases("ing_chili", "chili", "chili pepper", "banana chili", "banana pepper", "sili"),
        _aliases("ing_coconut_milk", "coconut milk", "gata"),
        _aliases("ing_coconut_cream", "coconut cream", "kakang gata"),
        _aliases("ing_cornstarch", "cornstarch"),
        _aliases("ing_cooking_oil", "cooking oil", "vegetable oil"),
        _aliases("ing_crab", "crab", "alimango", "alimasag"),
        _aliases("ing_egg", "egg", "eggs", "itlog"),
        _aliases("ing_eggplant", "eggplant", "talong"),
        _aliases("ing_fish_generic", "fish", "isda"),
        _aliases("ing_fish_sauce", "fish sauce", "patis"),
        _aliases("ing_flour", "flour", "all purpose flour"),
        _aliases("ing_glutinous_rice_flour", "glutinous rice flour", "galapong"),
        _aliases("ing_galunggong", "galunggong"),
        _aliases("ing_garlic", "garlic", "bawang"),
        _aliases("ing_ginger", "ginger", "luya"),
        _aliases("ing_kangkong", "kangkong", "water spinach", "ong choy", "ongchoy"),
        _aliases("ing_malunggay", "malunggay"),
        _aliases("ing_milk", "milk", "gatas"),
        _aliases("ing_mussel", "mussel", "mussels", "tahong"),
        _aliases("ing_oats", "oat", "oats"),
        _aliases("ing_okra", "okra"),
        _aliases("ing_olive_oil", "olive oil"),
        _aliases("ing_onion_generic", "onion", "sibuyas"),
        _aliases("ing_onion_red", "red onion"),
        _aliases("ing_onion_white", "white onion"),
        _aliases("ing_orange", "orange"),
        _aliases("ing_papaya", "papaya"),
        _aliases("ing_pasta", "pasta", "noodles", "egg noodles", "flour stick noodles", "bihon", "miki", "pancit"),
        _aliases("ing_pepper_black", "black pepper", "ground black pepper", "pepper", "paminta"),
        _aliases("ing_peppercorn_black", "black peppercorn", "black peppercorns", "peppercorn", "peppercorns"),
        _aliases("ing_peanut", "peanut", "peanuts", "mani"),
        _aliases("ing_pork_belly_raw", "pork belly", "pork liempo", "liempo"),
        _aliases("ing_pork_generic", "pork", "baboy", "lechon", "litson"),
        _aliases("ing_rice_generic", "rice", "bigas"),
        _aliases("ing_rice_flour", "rice flour"),
        _aliases("ing_rice_noodles", "rice noodles", "rice stick noodles", "rice vermicelli"),
        _aliases("ing_salmon", "salmon"),
        _aliases("ing_sayote", "sayote"),
        _aliases("ing_shrimp", "shrimp", "hipon"),
        _aliases("ing_soy_sauce", "soy sauce", "toyo"),
        _aliases("ing_salt", "salt", "asin", "rock salt"),
        _aliases("ing_sugar_white", "sugar", "white sugar", "granulated white sugar", "asukal"),
        _aliases("ing_soybean", "soybean", "soybeans", "soy", "soya"),
        _aliases("ing_squid", "squid", "pusit"),
        _aliases("ing_squash", "squash", "kalabasa"),
        _aliases("ing_string_beans", "string beans", "sitaw"),
        _aliases("ing_tilapia", "tilapia"),
        _aliases("ing_tofu", "tofu", "tokwa"),
        _aliases("ing_tomato", "tomato", "kamatis"),
        _aliases("ing_tuna", "tuna", "tambakol", "tulingan"),
        _aliases("ing_vinegar", "vinegar", "apple cider vinegar", "rice vinegar", "cane vinegar", "suka"),
        _aliases("ing_bay_leaf", "bay leaf", "bay leaves", "dahon ng laurel", "laurel"),
        _aliases("ing_oyster_sauce", "oyster sauce"),
        _aliases("ing_water", "water"),
        _aliases("ing_yogurt", "yogurt"),
    )
    for alias in group
)


INGREDIENT_ALLERGEN_LINKS: tuple[tuple[str, str, str, str], ...] = (
    ("ing_bangus", "fish", "contains", "high"),
    ("ing_bangus_fillet", "fish", "contains", "high"),
    ("ing_cheese_generic", "dairy", "contains", "high"),
    ("ing_butter", "dairy", "contains", "high"),
    ("ing_crab", "shellfish", "contains", "high"),
    ("ing_egg", "egg", "contains", "high"),
    ("ing_fish_generic", "fish", "contains", "high"),
    ("ing_fish_sauce", "fish", "contains", "high"),
    ("ing_galunggong", "fish", "contains", "high"),
    ("ing_milk", "dairy", "contains", "high"),
    ("ing_mussel", "shellfish", "contains", "high"),
    ("ing_pasta", "gluten", "may_contain", "medium"),
    ("ing_peanut", "peanut", "contains", "high"),
    ("ing_salmon", "fish", "contains", "high"),
    ("ing_shrimp", "shellfish", "contains", "high"),
    ("ing_soy_sauce", "soy", "contains", "high"),
    ("ing_soy_sauce", "gluten", "may_contain", "medium"),
    ("ing_soybean", "soy", "contains", "high"),
    ("ing_squid", "shellfish", "contains", "high"),
    ("ing_tilapia", "fish", "contains", "high"),
    ("ing_tofu", "soy", "contains", "high"),
    ("ing_tuna", "fish", "contains", "high"),
    ("ing_yogurt", "dairy", "contains", "high"),
)


INGREDIENT_PRICE_REFS: tuple[IngredientPriceSeed, ...] = (
    IngredientPriceSeed("ing_egg", "piece", 7, "Eggs & Dairy"),
    IngredientPriceSeed("ing_milk", "l", 90, "Eggs & Dairy"),
    IngredientPriceSeed("ing_cheese_generic", "kg", 300, "Eggs & Dairy"),
    IngredientPriceSeed("ing_yogurt", "piece", 60, "Eggs & Dairy"),
    IngredientPriceSeed("ing_butter", "kg", 300, "Eggs & Dairy"),
    IngredientPriceSeed("ing_coconut_milk", "l", 70, "Eggs & Dairy"),
    IngredientPriceSeed("ing_rice_generic", "kg", 60, "Dry Goods"),
    IngredientPriceSeed("ing_oats", "kg", 140, "Dry Goods"),
    IngredientPriceSeed("ing_bread", "piece", 80, "Dry Goods"),
    IngredientPriceSeed("ing_pasta", "kg", 90, "Dry Goods"),
    IngredientPriceSeed("ing_flour", "kg", 60, "Dry Goods"),
    IngredientPriceSeed("ing_chicken_generic", "kg", 180, "Meat/Seafood"),
    IngredientPriceSeed("ing_beef_generic", "kg", 320, "Meat/Seafood"),
    IngredientPriceSeed("ing_pork_generic", "kg", 260, "Meat/Seafood"),
    IngredientPriceSeed("ing_fish_generic", "kg", 220, "Meat/Seafood"),
    IngredientPriceSeed(
        "ing_bangus_fillet",
        "kg",
        388,
        "Meat/Seafood",
        source="metro_retail_fresh_boneless_listing",
        confidence="medium",
    ),
    IngredientPriceSeed("ing_shrimp", "kg", 300, "Meat/Seafood"),
    IngredientPriceSeed("ing_tomato", "kg", 60, "Produce"),
    IngredientPriceSeed("ing_onion_generic", "kg", 80, "Produce"),
    IngredientPriceSeed("ing_garlic", "kg", 120, "Produce"),
    IngredientPriceSeed("ing_carrot", "kg", 70, "Produce"),
    IngredientPriceSeed("ing_cabbage", "kg", 55, "Produce"),
    IngredientPriceSeed("ing_okra", "kg", 70, "Produce"),
    IngredientPriceSeed("ing_bitter_melon", "kg", 70, "Produce"),
    IngredientPriceSeed("ing_eggplant", "kg", 70, "Produce"),
    IngredientPriceSeed("ing_sayote", "kg", 70, "Produce"),
    IngredientPriceSeed("ing_squash", "kg", 70, "Produce"),
    IngredientPriceSeed("ing_banana", "kg", 60, "Produce"),
    IngredientPriceSeed("ing_apple", "kg", 120, "Produce"),
    IngredientPriceSeed("ing_orange", "kg", 80, "Produce"),
    IngredientPriceSeed("ing_ginger", "kg", 140, "Produce"),
    IngredientPriceSeed("ing_cooking_oil", "l", 120, "Spices & Condiments"),
    IngredientPriceSeed("ing_canola_oil", "l", 120, "Spices & Condiments"),
    IngredientPriceSeed("ing_olive_oil", "l", 120, "Spices & Condiments"),
    IngredientPriceSeed("ing_soy_sauce", "piece", 40, "Spices & Condiments"),
    IngredientPriceSeed("ing_vinegar", "piece", 40, "Spices & Condiments"),
    IngredientPriceSeed("ing_fish_sauce", "piece", 40, "Spices & Condiments"),
    IngredientPriceSeed("ing_salt", "piece", 20, "Spices & Condiments"),
    IngredientPriceSeed("ing_pepper_black", "piece", 20, "Spices & Condiments"),
    IngredientPriceSeed(
        "ing_water",
        "l",
        0,
        "Beverages",
        source="household_tap_water_baseline",
        confidence="high",
    ),
)


DA_NCR_WEEKLY_PRICE_SOURCE_URL = (
    "https://www.da.gov.ph/wp-content/uploads/2026/09/"
    "Weekly-Average-Prices-August-31-September-6-2026.pdf"
)
DA_NCR_WEEKLY_PRICE_SOURCE_DATE = "2026-09-06"

BANGUS_FILLET_RETAIL_SOURCE_URL = "https://shopmetro.ph/product/boneless-bangus-1kg/"
BANGUS_FILLET_RETAIL_SOURCE_DATE = "2026-09-08"
BANGUS_FILLET_RETAIL_PRICE_PHP_PER_KG = 388.0

# DA-AMAS NCR weekly average retail observations. These are purchase-market
# references, not nutrition values and not forecasts.
DA_NCR_WEEKLY_MARKET_PRICES: tuple[IngredientMarketPriceSeed, ...] = (
    IngredientMarketPriceSeed("ing_rice_generic", "kg", 48.60, "Local well-milled rice"),
    IngredientMarketPriceSeed("ing_bangus", "kg", 243.62, "Bangus, medium"),
    IngredientMarketPriceSeed("ing_galunggong", "kg", 322.56, "Galunggong"),
    IngredientMarketPriceSeed("ing_tilapia", "kg", 156.88, "Tilapia"),
    IngredientMarketPriceSeed("ing_squid", "kg", 468.41, "Squid"),
    IngredientMarketPriceSeed("ing_tuna", "kg", 320.52, "Tambakol/yellowfin tuna"),
    IngredientMarketPriceSeed("ing_beef_generic", "kg", 441.01, "Beef brisket", "medium"),
    IngredientMarketPriceSeed("ing_pork_belly_raw", "kg", 379.20, "Local pork belly/liempo"),
    IngredientMarketPriceSeed("ing_pork_generic", "kg", 325.22, "Local pork kasim", "medium"),
    IngredientMarketPriceSeed("ing_chicken_generic", "kg", 204.74, "Whole chicken", "medium"),
    IngredientMarketPriceSeed("ing_chicken_breast_raw", "kg", 204.74, "Whole chicken proxy", "medium"),
    IngredientMarketPriceSeed("ing_chicken_thigh_raw", "kg", 204.74, "Whole chicken proxy", "medium"),
    IngredientMarketPriceSeed("ing_egg", "piece", 8.09, "Medium white chicken egg"),
    IngredientMarketPriceSeed("ing_bitter_melon", "kg", 192.16, "Ampalaya"),
    IngredientMarketPriceSeed("ing_chili", "kg", 197.20, "Green chili"),
    IngredientMarketPriceSeed("ing_eggplant", "kg", 186.32, "Eggplant"),
    IngredientMarketPriceSeed("ing_bok_choy", "kg", 202.65, "Native pechay"),
    IngredientMarketPriceSeed("ing_string_beans", "kg", 185.41, "Pole sitao/string beans"),
    IngredientMarketPriceSeed("ing_squash", "kg", 66.87, "Squash"),
    IngredientMarketPriceSeed("ing_tomato", "kg", 108.50, "Tomato"),
    IngredientMarketPriceSeed("ing_cabbage", "kg", 147.87, "Cabbage"),
    IngredientMarketPriceSeed("ing_carrot", "kg", 117.41, "Carrot"),
    IngredientMarketPriceSeed("ing_sayote", "kg", 101.91, "Sayote"),
    IngredientMarketPriceSeed("ing_garlic", "kg", 150.77, "Imported garlic", "medium", 150.77, 351.00),
    IngredientMarketPriceSeed("ing_ginger", "kg", 187.60, "Ginger"),
    IngredientMarketPriceSeed("ing_onion_generic", "kg", 115.58, "Red onion proxy", "medium", 115.58, 132.69),
    IngredientMarketPriceSeed("ing_onion_red", "kg", 115.58, "Red onion"),
    IngredientMarketPriceSeed("ing_onion_white", "kg", 132.69, "White onion"),
    IngredientMarketPriceSeed("ing_banana", "kg", 77.18, "Latundan banana", "medium", 64.19, 96.49),
    IngredientMarketPriceSeed("ing_calamansi", "kg", 110.61, "Calamansi"),
    IngredientMarketPriceSeed("ing_papaya", "kg", 78.49, "Papaya"),
    IngredientMarketPriceSeed("ing_cooking_oil", "l", 100.06, "Palm cooking oil", "medium"),
    IngredientMarketPriceSeed("ing_canola_oil", "l", 100.06, "Palm cooking oil proxy", "medium"),
    IngredientMarketPriceSeed("ing_salt", "kg", 42.31, "Iodized salt"),
    IngredientMarketPriceSeed("ing_sugar_white", "kg", 81.46, "Refined sugar"),
)


UNIT_ALIASES = {
    "kg": "kg",
    "kilo": "kg",
    "kilogram": "kg",
    "kilograms": "kg",
    "g": "g",
    "gram": "g",
    "grams": "g",
    "lb": "lb",
    "lbs": "lb",
    "pound": "lb",
    "pounds": "lb",
    "oz": "oz",
    "ounce": "oz",
    "ounces": "oz",
    "l": "l",
    "liter": "l",
    "liters": "l",
    "ml": "ml",
    "milliliter": "ml",
    "milliliters": "ml",
    "cup": "cup",
    "cups": "cup",
    "glass": "glass",
    "glasses": "glass",
    "tbsp": "tbsp",
    "tablespoon": "tbsp",
    "tablespoons": "tbsp",
    "tsp": "tsp",
    "teaspoon": "tsp",
    "teaspoons": "tsp",
    "piece": "piece",
    "pieces": "piece",
    "clove": "clove",
    "cloves": "clove",
    "can": "can",
    "cans": "can",
    "thumb": "thumb",
    "thumbs": "thumb",
}

DESCRIPTOR_WORDS = {
    "about", "chopped", "cleaned", "coarsely", "cooked",
    "crushed", "cubed", "cut", "diced", "divided", "drained",
    "finely", "fresh", "freshly", "fried", "frozen", "grated", "ground",
    "halved", "julienned", "large", "lean", "medium", "minced", "optional",
    "peeled", "raw", "roughly", "seeded", "shredded", "sliced", "small",
    "thinly", "trimmed",
}

_NUMBER_TOKEN_RE = re.compile(r"^\d+(?:\.\d+)?$|^\d+/\d+$")
_CANONICAL_BY_ID = {item.ingredient_id: item for item in CANONICAL_INGREDIENTS}


def normalize_ingredient_text(raw_text: str) -> str:
    text = re.sub(r"\([^)]*\)", " ", str(raw_text or "").casefold())
    text = text.replace("-", " ")
    tokens = [
        token.strip(".")
        for token in re.sub(r"[^a-z0-9/.\s]+", " ", text).split()
    ]
    cleaned = [
        token
        for token in tokens
        if token not in UNIT_ALIASES
        and token not in DESCRIPTOR_WORDS
        and not _NUMBER_TOKEN_RE.match(token)
    ]
    return " ".join(cleaned)


def _parse_number(token: str) -> float | None:
    try:
        if "/" in token:
            return float(Fraction(token))
        return float(token)
    except (ValueError, ZeroDivisionError):
        return None


def parse_quantity_unit(raw_text: str) -> tuple[float | None, str | None]:
    tokens = [
        token.strip(".")
        for token in re.sub(r"[^a-z0-9/.\s]+", " ", str(raw_text or "").casefold()).split()
    ]
    quantity: float | None = None
    unit: str | None = None
    for index, token in enumerate(tokens):
        number = _parse_number(token)
        if number is None:
            continue
        quantity = number
        if index + 1 < len(tokens):
            second = _parse_number(tokens[index + 1])
            if second is not None and "/" in tokens[index + 1]:
                quantity += second
                index += 1
        next_index = index + 1
        if next_index < len(tokens):
            unit = UNIT_ALIASES.get(tokens[next_index])
        break
    return quantity, unit


def _alias_index(aliases: Iterable[IngredientAliasSeed]) -> dict[str, set[str]]:
    index: dict[str, set[str]] = {}
    for alias in aliases:
        normalized = normalize_ingredient_text(alias.alias_text)
        if normalized:
            index.setdefault(normalized, set()).add(alias.ingredient_id)
    return index


_DEFAULT_ALIAS_INDEX = _alias_index(INGREDIENT_ALIASES)


def resolve_ingredient(
    raw_text: str,
    aliases: Iterable[IngredientAliasSeed] = INGREDIENT_ALIASES,
) -> IngredientResolution:
    normalized = normalize_ingredient_text(raw_text)
    quantity, unit = parse_quantity_unit(raw_text)
    index = _DEFAULT_ALIAS_INDEX if aliases is INGREDIENT_ALIASES else _alias_index(aliases)
    matches: list[tuple[int, str, set[str]]] = []
    padded = f" {normalized} "
    for alias, ingredient_ids in index.items():
        if f" {alias} " in padded:
            matches.append((len(alias.split()), alias, ingredient_ids))
    if not matches:
        return IngredientResolution(
            str(raw_text or ""), normalized, None, None, "unmapped", "none",
            None, (), quantity, unit,
        )

    longest = max(item[0] for item in matches)
    strongest = [item for item in matches if item[0] == longest]
    candidate_ids = tuple(sorted({value for _, _, values in strongest for value in values}))
    matched_aliases = sorted({alias for _, alias, _ in strongest})
    if len(candidate_ids) != 1:
        return IngredientResolution(
            str(raw_text or ""), normalized, None, None, "ambiguous", "none",
            " | ".join(matched_aliases), candidate_ids, quantity, unit,
        )

    ingredient_id = candidate_ids[0]
    canonical = _CANONICAL_BY_ID.get(ingredient_id)
    return IngredientResolution(
        str(raw_text or ""),
        normalized,
        ingredient_id,
        canonical.canonical_name if canonical else ingredient_id,
        "mapped",
        "high",
        matched_aliases[0],
        candidate_ids,
        quantity,
        unit,
    )


def provisional_ingredient(raw_text: str, status: str = "unmapped") -> ProvisionalIngredient:
    normalized = normalize_ingredient_text(raw_text) or "unknown ingredient"
    digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:10]
    slug = re.sub(r"[^a-z0-9]+", "_", normalized).strip("_")[:64] or "unknown"
    quality_status = "provisional_ambiguous" if status == "ambiguous" else "provisional_unmapped"
    return ProvisionalIngredient(
        ingredient_id=f"ing_provisional_{slug}_{digest}",
        canonical_name=normalized,
        category="unclassified",
        default_unit="unspecified",
        quality_status=quality_status,
    )


def ingredient_name_and_quantity(ingredient: Any) -> tuple[str, str]:
    if isinstance(ingredient, dict):
        return (
            str(ingredient.get("name") or "").strip(),
            str(ingredient.get("quantity") or "").strip(),
        )
    return str(ingredient or "").strip(), ""
