"""Create a gram-portioned, PhilFCT-matched draft catalog.

This is the ingredient-level layer for the complete-plate catalog. It parses the
original recipe ingredient strings, estimates one-person gram portions from the
source recipe serving count, matches ingredients to PhilFCT food items, and
computes ingredient nutrition from PhilFCT per-100g data when a match is found.

The output is a draft review artifact. Automatic unit conversion and fuzzy food
matching are useful for scale, but every uncertain match remains flagged.
"""

from __future__ import annotations

import argparse
import json
import math
import re
from collections import Counter
from difflib import SequenceMatcher
from io import StringIO
from pathlib import Path
from typing import Any

import pandas as pd
import requests


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
DEFAULT_RECIPES = BACKEND_ROOT / "recipes.json"
DEFAULT_COMPLETE_PLATE = BACKEND_ROOT / "seed_data" / "pcosina_philfct_complete_plate_catalog_v2_draft.json"
DEFAULT_PHILFCT_CACHE = BACKEND_ROOT / "seed_data" / "philfct_reference_cache.json"
DEFAULT_OUTPUT = BACKEND_ROOT / "seed_data" / "pcosina_philfct_portioned_catalog_v2_draft.json"
DEFAULT_REVIEW = (
    REPO_ROOT
    / "docs"
    / "thesis_validation"
    / "03_ACTUAL_SYSTEM_DATA_EXPORTS"
    / "pcosina_philfct_portioned_ingredient_review_queue.csv"
)
PHILFCT_URL = "https://i.fnri.dost.gov.ph/fct/library/search_item"
NUTRIENTS = ("calories", "protein_g", "carbs_g", "fat_g", "fiber_g")

UNICODE_FRACTIONS = {
    "½": "1/2",
    "¼": "1/4",
    "¾": "3/4",
    "⅓": "1/3",
    "⅔": "2/3",
    "⅛": "1/8",
    "⅜": "3/8",
    "⅝": "5/8",
    "⅞": "7/8",
}

GRAM_UNITS = {
    "g": 1.0,
    "gram": 1.0,
    "grams": 1.0,
    "kg": 1000.0,
    "kilo": 1000.0,
    "kilos": 1000.0,
    "kilogram": 1000.0,
    "kilograms": 1000.0,
    "lb": 453.592,
    "lbs": 453.592,
    "pound": 453.592,
    "pounds": 453.592,
    "oz": 28.3495,
    "ounce": 28.3495,
    "ounces": 28.3495,
}

VOLUME_TO_GRAM = {
    "cup": 240.0,
    "cups": 240.0,
    "tbsp": 15.0,
    "tbsps": 15.0,
    "tablespoon": 15.0,
    "tablespoons": 15.0,
    "tsp": 5.0,
    "teaspoon": 5.0,
    "teaspoons": 5.0,
    "ml": 1.0,
    "milliliter": 1.0,
    "milliliters": 1.0,
    "l": 1000.0,
    "liter": 1000.0,
    "liters": 1000.0,
    "quart": 946.0,
    "quarts": 946.0,
}

PIECE_GRAMS = [
    (("egg", "itlog"), 50.0),
    (("quail egg",), 9.0),
    (("okra",), 12.0),
    (("shrimp", "hipon"), 15.0),
    (("mussel", "tahong"), 15.0),
    (("siling labuyo", "bird s eye chili", "birds eye chili", "thai bird", "habanero", "chile", "chili"), 2.0),
    (("siling pansigang",), 15.0),
    (("tomato", "kamatis"), 60.0),
    (("onion", "sibuyas"), 110.0),
    (("green onion", "spring onion", "scallion"), 10.0),
    (("garlic", "clove"), 3.0),
    (("head garlic", "garlic head"), 45.0),
    (("potato", "patatas"), 170.0),
    (("carrot",), 60.0),
    (("cauliflower",), 600.0),
    (("bell pepper", "pepper"), 120.0),
    (("eggplant", "talong"), 100.0),
    (("chinese eggplant",), 120.0),
    (("banana", "saging"), 100.0),
    (("saba", "plantain"), 100.0),
    (("mango",), 165.0),
    (("lemon slice", "slice lemon"), 10.0),
    (("lemon",), 58.0),
    (("calamansi",), 15.0),
    (("long green pepper", "chili"), 15.0),
    (("bay leaves", "bay leaf"), 0.5),
    (("sinigang mix", "maggi magic", "sampalok"), 22.0),
    (("pork cube", "beef cube", "chicken cube", "bouillon cube", "bouillon", "knorr"), 10.0),
    (("beef powder", "beef broth", "chicken powder"), 8.0),
    (("thumb ginger", "thumb-size ginger", "thumb sized ginger", "thumbs ginger"), 15.0),
    (("ginger",), 5.0),
    (("stalk lemongrass", "lemongrass"), 20.0),
    (("celery",), 40.0),
    (("pandan",), 5.0),
    (("cilantro", "coriander", "wansoy"), 1.0),
    (("string beans", "sitaw", "snake beans", "baguio beans", "green beans", "long green beans"), 12.0),
    (("corn cob", "cobs corn", "cob corn", "corn"), 100.0),
    (("small cabbage",), 500.0),
    (("head cabbage", "cabbage head"), 900.0),
    (("napa cabbage",), 700.0),
    (("cabbage",), 900.0),
    (("romaine lettuce", "lettuce"), 20.0),
    (("lumpia wrapper", "wonton wrapper", "spring roll wrapper"), 15.0),
    (("taco shell",), 13.0),
    (("block extra firm tofu", "extra firm tofu"), 450.0),
    (("tofu", "tokwa"), 120.0),
    (("hot dogs", "hotdog", "hot dog"), 45.0),
    (("bacon strip", "bacon"), 12.0),
    (("chorizo de bilbao", "chorizo"), 60.0),
    (("longganisa",), 50.0),
    (("chicken wings", "chicken wing"), 85.0),
    (("leg quarters", "leg quarter"), 250.0),
    (("chicken", "manok"), 200.0),
    (("bangus", "milkfish"), 600.0),
    (("tilapia fillet", "fish fillet", "fillets tilapia", "fillet"), 120.0),
    (("fish steak", "white fish steaks", "fish"), 250.0),
    (("blue crabs", "alimasag", "crab"), 150.0),
    (("squid", "pusit"), 150.0),
    (("ampalaya", "bitter gourd"), 180.0),
    (("sayote", "chayote"), 250.0),
    (("upo", "opo squash", "bottle gourd"), 450.0),
    (("patola",), 300.0),
    (("labanos", "radish"), 200.0),
    (("kalabasa", "squash", "butternut squash"), 500.0),
    (("green papaya", "unripe papaya", "papaya"), 500.0),
    (("pork chop", "pork steaks", "pork steak"), 150.0),
    (("pork belly", "lechon kawali", "slab pork"), 500.0),
    (("mini-meatballs", "meatballs"), 100.0),
]

BUNDLE_GRAMS = [
    (("kangkong", "pechay", "bok choy", "malunggay", "mustasa"), 150.0),
    (("string beans", "sitaw"), 120.0),
]

IGNORE_TERMS = {
    "water", "salt", "pepper", "ground black pepper", "black pepper", "fish sauce to taste",
    "to taste", "optional", "oil for frying", "oil for deep frying", "oil for deep-frying",
    "vegetable oil for frying", "canola oil for frying", "oil as needed", "to serve",
    "bay leaf", "bay leaves", "dried bay leaf", "dried bay leaves", "dahon ng laurel",
    "marinade ingredients", "marinade ingredients:", "cooking procedure",
    "traditional: head and feet up and down", "modern: shoulder or leg meat cut into 2-inch pieces",
    "traditional head and feet up and down", "modern shoulder or leg meat cut into 2-inch pieces",
    "crab shells", "juice extracted from the shrimp head", "prawns heads only",
}

NO_QUANTITY_DEFAULTS = [
    (("cooking oil", "vegetable oil", "canola oil", "corn oil", "mantika"), 15.0, "estimated_default_portion"),
    (("toasted garlic bits", "crispy fried garlic", "garlic flakes"), 3.0, "estimated_garnish_portion"),
    (("garlic",), 6.0, "estimated_default_portion"),
    (("green onions", "green onion", "spring onions", "spring onion", "scallions"), 10.0, "estimated_garnish_portion"),
    (("cilantro", "coriander", "wansoy"), 5.0, "estimated_garnish_portion"),
    (("calamansi", "lemon wedges", "lemon juice"), 15.0, "estimated_garnish_portion"),
    (("mango",), 165.0, "estimated_default_portion"),
    (("green lettuce leaves", "lettuce leaves", "romaine lettuce"), 40.0, "estimated_garnish_portion"),
    (("peanuts", "peanut"), 20.0, "estimated_garnish_portion"),
    (("fish sauce", "patis"), 15.0, "estimated_default_portion"),
    (("mini-meatballs", "meatballs"), 100.0, "estimated_default_portion"),
]

ALIAS_QUERY = {
    "adlai": "job's tears grain",
    "abitsuelas": "snap bean pod green",
    "ampalaya": "bitter melon gourd fruit",
    "kalabasa": "squash",
    "butternut squash": "squash fruit",
    "sitaw": "string yard long bean pod green boiled",
    "string beans": "string yard long bean pod green boiled",
    "snake beans": "string yard long bean pod green boiled",
    "long beans": "string yard long bean pod green boiled",
    "yard long beans": "string yard long bean pod green boiled",
    "baguio beans": "snap bean pod green",
    "green beans": "snap bean pod green",
    "snap beans": "snap bean pod green",
    "bangus": "milkfish",
    "milkfish": "milkfish",
    "malunggay leaves": "horseradish tree lvs",
    "malunggay": "horseradish tree lvs",
    "pechay": "pechay",
    "napa cabbage": "chinese cabbage",
    "chinese cabbage": "chinese cabbage",
    "cabbage": "cabbage green",
    "romaine lettuce": "lettuce lvs petioles",
    "green lettuce": "lettuce lvs petioles",
    "lettuce": "lettuce lvs petioles",
    "kangkong": "water spinach",
    "talong": "eggplant",
    "eggplant": "eggplant",
    "eggs": "egg chicken whole",
    "itlog": "egg chicken whole",
    "tomatoes": "tomato",
    "tomato": "tomato",
    "kamatis": "tomato",
    "carrots": "carrot",
    "carrot": "carrot",
    "onion": "onion bombay bulb",
    "garlic": "garlic bulb",
    "chicken breast": "chicken breast",
    "leg quarters": "chicken thigh leg",
    "leg quarter": "chicken thigh leg",
    "chicken": "chicken whole",
    "crispy pata": "pork leg",
    "pig s leg": "pork leg",
    "pork leg": "pork leg",
    "ham hock": "pork leg",
    "lechon liempo": "pork belly seasoned roasted",
    "liempo": "pork belly",
    "pork belly": "pork belly",
    "pork": "pork",
    "bottom round": "beef round",
    "beef round": "beef round",
    "beef": "beef",
    "prawns": "shrimp giant tiger prawn",
    "prawn": "shrimp giant tiger prawn",
    "shrimp": "shrimp banana prawn",
    "hipon": "shrimp banana prawn",
    "mussels": "mussel green",
    "tahong": "mussel green",
    "blue crabs": "crab blue swimming",
    "alimasag": "crab blue swimming",
    "squid": "squid",
    "pusit": "squid",
    "tilapia": "tilapia",
    "tanigue": "mackerel spanish",
    "lapu-lapu": "grouper whitespotted",
    "lapu lapu": "grouper whitespotted",
    "red snapper": "red snapper malabar",
    "maya-maya": "red snapper malabar",
    "maya maya": "red snapper malabar",
    "dalag": "mudfish murrel striated",
    "mudfish": "mudfish murrel striated",
    "tulingan": "tuna frigate",
    "tuna jaw": "tuna yellow-fin",
    "salmon": "runner rainbow",
    "rice": "rice well milled boiled",
    "white rice": "rice well milled boiled",
    "glutinous rice": "rice glutinous",
    "palabok noodles": "noodles rice",
    "fettuccine noodles": "pasta spaghetti",
    "spaghetti noodles": "pasta spaghetti",
    "spaghetti": "pasta spaghetti",
    "coconut milk": "coconut milk",
    "coconut cream": "coconut cream",
    "extra virgin olive oil": "oil corn",
    "olive oil": "oil corn",
    "canola oil": "oil corn",
    "vegetable oil": "oil corn",
    "cooking oil": "oil corn",
    "corn oil": "oil corn",
    "soy sauce": "soy sauce",
    "fish sauce": "fish sauce",
    "patis": "fish sauce",
    "vinegar": "vinegar",
    "brown sugar": "sugar brown",
    "white sugar": "sugar white refined",
    "sugar": "sugar white refined",
    "honey": "honey pulot-pukyutan",
    "ketchup": "catsup tomato",
    "catsup": "catsup tomato",
    "green olives": "olive green in brine",
    "olives": "olive green in brine",
    "green papaya": "papaya fruit unripe",
    "unripe papaya": "papaya fruit unripe",
    "papaya": "papaya fruit ripe",
    "pineapple": "pineapple",
    "calamansi": "calamansi philippine lemon",
    "lime": "lime",
    "lemon": "calamansi philippine lemon",
    "mango": "mango manila super ripe",
    "banana": "saging cavendish hinog",
    "munggo": "mung bean",
    "monggo": "mung bean",
    "ginger": "ginger",
    "chicken bouillon cube": "bouillon cube chicken",
    "chicken bouillon": "bouillon cube chicken",
    "chicken cube": "bouillon cube chicken",
    "bouillon cube": "bouillon cube chicken",
    "potatoes": "potato",
    "potato": "potato",
    "scallions": "onion",
    "spring onions": "onion",
    "green onions": "onion",
    "bok choy": "pechay",
    "baby bok choy": "pechay",
    "garbanzos": "chickpea",
    "lentils": "lentil",
    "bamboo shoot": "bamboo shoot",
    "button mushrooms": "mushroom",
    "shiitake mushrooms": "mushroom fresh",
    "tofu": "soybean cheese soft curd",
    "tokwa": "soybean cheese hard curd",
    "chicharon": "pork rind",
    "paprika": "pepper",
    "siling pangsigang": "pepper chili fruit",
    "siling pansigang": "pepper chili fruit",
    "sili pangsigang": "pepper chili fruit",
    "sili pansigang": "pepper chili fruit",
    "finger chilies": "pepper chili fruit",
    "finger chili": "pepper chili fruit",
    "birds eye chilies": "pepper chili fruit",
    "bird s eye chili": "pepper chili fruit",
    "birds eye chili": "pepper chili fruit",
    "thai bird": "pepper chili fruit",
    "thai chili": "pepper chili fruit",
    "habanero": "pepper chili fruit",
    "labuyo": "pepper chili fruit",
    "chilies": "pepper chili fruit",
    "chiles": "pepper chili fruit",
    "cilantro": "coriander leaves",
    "wansoy": "coriander leaves",
    "celery": "celery lvs petioles",
    "leeks": "leek",
    "green peas in can": "green pea in brine cnd",
    "frozen green peas": "green pea in brine cnd",
    "green peas": "green pea in brine cnd",
    "gisantes": "green pea in brine cnd",
    "snow peas": "snow sugar pea pod",
    "chicharo": "snow sugar pea pod",
    "winged beans": "winged bean pod",
    "sigarilyas": "winged bean pod",
    "bean sprouts": "mung bean sprout",
    "togue": "mung bean sprout",
    "saluyot": "jute lvs",
    "sayote": "chayote fruit",
    "chayote": "chayote fruit",
    "opo squash": "bottle gourd fruit",
    "upo": "bottle gourd fruit",
    "patola": "sponge gourd fruit",
    "young corn": "corn on cob yellow",
    "japanese corn": "corn on cob yellow",
    "peanuts": "peanut without skin",
    "peanut": "peanut without skin",
    "chickpeas": "chickpea dried boiled",
    "garbanzos": "chickpea dried boiled",
    "crabmeat": "crab",
}

ALIAS_CODE = {
    "pork adobo": "R050",
    "pork beans": "R059",
    "pork kasim": "F179",
    "kasim": "F179",
    "pork belly": "F147",
    "lechon liempo": "F269",
    "liempo": "F147",
    "crispy pata": "F173",
    "pig s leg": "F173",
    "pork leg": "F173",
    "ham hock": "F173",
    "chicken breast": "F093",
    "leg quarters": "F108",
    "leg quarter": "F108",
    "chicken bouillon cube": "N003",
    "chicken bouillon": "N003",
    "chicken cube": "N003",
    "bouillon cube": "N003",
    "chicken": "F113",
    "eggs": "H003",
    "itlog": "H003",
    "canola oil": "K009",
    "extra virgin olive oil": "K009",
    "olive oil": "K009",
    "vegetable oil": "K009",
    "cooking oil": "K009",
    "corn oil": "K009",
    "honey": "M021",
    "brown sugar": "M042",
    "white sugar": "M046",
    "sugar": "M046",
    "shrimp": "G113",
    "hipon": "G113",
    "prawns": "G117",
    "prawn": "G117",
    "malunggay leaves": "D094",
    "malunggay": "D094",
    "green papaya": "D154",
    "unripe papaya": "D154",
    "papaya": "E069",
    "calamansi": "E023",
    "lime": "E042",
    "lemon": "E023",
    "bamboo shoot": "D010",
    "winged beans": "D272",
    "sigarilyas": "D272",
    "tanigue": "G072",
    "fish sauce": "N011",
    "patis": "N011",
    "soy sauce": "N023",
    "vinegar": "N028",
    "ketchup": "N005",
    "catsup": "N005",
}


def normalize_text(text: str) -> str:
    text = str(text or "").lower()
    for src, dst in UNICODE_FRACTIONS.items():
        text = text.replace(src, f" {dst} ")
    text = text.replace("½", " 1/2 ")
    text = re.sub(r"(\d+)\s*/\s*(\d+)", r"\1/\2", text)
    text = re.sub(r"\btable\s+spoon\b", "tablespoon", text)
    text = re.sub(r"\btbsps?\.\b", "tbsp", text)
    text = re.sub(r"\b(lbs?|oz|tbsp|tsp)\.", r"\1", text)
    text = re.sub(r"\bcip\b", "cup", text)
    text = re.sub(r"\b(\d+)-\s+(tablespoon|tbsp|teaspoon|tsp|cup|cups|gram|grams|kg|kilo|pound|pounds|lb|lbs)\b", r"\1 \2", text)
    text = re.sub(r"[^a-z0-9./\\s-]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def parse_number(raw: str) -> float | None:
    text = normalize_text(raw)
    parts = text.split()
    if not parts:
        return None
    if len(parts) >= 2 and re.match(r"^\d+$", parts[0]) and re.match(r"^\d+/\d+$", parts[1]):
        whole = float(parts[0])
        num, den = parts[1].split("/")
        return whole + float(num) / float(den)
    token = parts[0]
    if re.match(r"^\d+/\d+$", token):
        num, den = token.split("/")
        return float(num) / float(den)
    try:
        return float(token)
    except Exception:
        return None


AMOUNT_PATTERN = r"(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)"
UNIT_PATTERN = (
    r"(?:g|gram|grams|kg|kilo|kilos|kilogram|kilograms|lb|lbs|pound|pounds|"
    r"oz|ounce|ounces|cup|cups|tbsp|tbsps|tablespoon|tablespoons|tsp|teaspoon|"
    r"teaspoons|ml|l|liter|liters)"
)


def amount_value(raw: str) -> float:
    text = normalize_text(raw)
    parts = text.split()
    if len(parts) == 2 and re.match(r"^\d+$", parts[0]) and re.match(r"^\d+/\d+$", parts[1]):
        num, den = parts[1].split("/")
        return float(parts[0]) + float(num) / float(den)
    if re.match(r"^\d+/\d+$", text):
        num, den = text.split("/")
        return float(num) / float(den)
    return float(text)


def unit_to_grams(unit: str) -> float:
    unit = unit.strip(".,;:()").lower()
    if unit in GRAM_UNITS:
        return GRAM_UNITS[unit]
    if unit in VOLUME_TO_GRAM:
        return VOLUME_TO_GRAM[unit]
    return 1.0


def portion_result(status: str, batch_g: float, servings: float, clean_name: str) -> dict[str, Any]:
    portion = batch_g / max(servings, 1.0)
    return {
        "parse_status": status,
        "batch_g": round(batch_g, 2),
        "portion_g": round(portion, 2),
        "clean_name": clean_ingredient_name(clean_name),
    }


def source_servings(raw: Any) -> float:
    text = str(raw or "").strip()
    match = re.search(r"\d+(?:\.\d+)?", text)
    if not match:
        return 1.0
    value = float(match.group(0))
    return value if value > 0 else 1.0


def piece_weight(name: str, unit: str) -> float | None:
    text = normalize_text(name)
    if unit in {"bunch", "bunches", "bundle", "bundles"}:
        for terms, grams in BUNDLE_GRAMS:
            if any(term in text for term in terms):
                return grams
        return 150.0
    for terms, grams in PIECE_GRAMS:
        if any(term in text for term in terms):
            return grams
    return None


def parse_leading_range(text: str, servings: float) -> dict[str, Any] | None:
    match = re.match(
        rf"^(?P<low>{AMOUNT_PATTERN})\s*(?:-|to)\s*(?P<high>{AMOUNT_PATTERN})\s+(?P<unit>{UNIT_PATTERN}|pieces?|pcs?)\b(?P<rest>.*)$",
        text,
    )
    if not match:
        return None
    amount = (amount_value(match.group("low")) + amount_value(match.group("high"))) / 2.0
    unit = match.group("unit").strip(".,;:()")
    rest = match.group("rest")
    clean_name = rest or text
    if unit in GRAM_UNITS or unit in VOLUME_TO_GRAM:
        return portion_result("parsed_range_estimate", amount * unit_to_grams(unit), servings, clean_name)
    grams_each = piece_weight(text, unit)
    if grams_each is not None:
        return portion_result("parsed_range_piece_estimate", amount * grams_each, servings, clean_name)
    return None


def parse_leading_count_range(text: str, servings: float) -> dict[str, Any] | None:
    match = re.match(
        rf"^(?P<low>{AMOUNT_PATTERN})\s*(?:-|to)\s*(?P<high>{AMOUNT_PATTERN})\b(?P<rest>.*)$",
        text,
    )
    if not match:
        return None
    grams_each = piece_weight(text, "")
    if grams_each is None:
        return None
    amount = (amount_value(match.group("low")) + amount_value(match.group("high"))) / 2.0
    return portion_result("parsed_range_piece_estimate", amount * grams_each, servings, match.group("rest") or text)


def parse_embedded_weight(text: str, servings: float) -> dict[str, Any] | None:
    each = re.match(
        rf"^(?P<count>{AMOUNT_PATTERN})\s+(?:pieces?|pcs?)\b.*?(?:about|around|approximately)?\s*(?P<amount>{AMOUNT_PATTERN})\s*(?P<unit>{UNIT_PATTERN})\s+(?:each|per)\b(?P<rest>.*)$",
        text,
    )
    if each:
        count = amount_value(each.group("count"))
        amount = amount_value(each.group("amount"))
        clean = re.sub(rf"^{AMOUNT_PATTERN}\s+(?:pieces?|pcs?)\s+", " ", text)
        clean = re.sub(
            rf"(?:about|around|approximately)?\s*{re.escape(each.group('amount'))}\s*{re.escape(each.group('unit'))}\s+(?:each|per)(?:\s+piece)?",
            " ",
            clean,
        )
        return portion_result(
            "parsed_embedded_each_weight",
            count * amount * unit_to_grams(each.group("unit")),
            servings,
            clean or text,
        )

    ranged = re.search(
        rf"(?:about|around|approximately)?\s*(?P<low>{AMOUNT_PATTERN})\s*(?:-|to)\s*(?P<high>{AMOUNT_PATTERN})\s*(?P<unit>kg|kilo|kilos|kilogram|kilograms|lb|lbs|pound|pounds|g|gram|grams|oz|ounce|ounces)\b",
        text,
    )
    if ranged:
        amount = (amount_value(ranged.group("low")) + amount_value(ranged.group("high"))) / 2.0
        clean = (text[: ranged.start()] + " " + text[ranged.end() :]).strip()
        return portion_result("parsed_embedded_weight_range", amount * unit_to_grams(ranged.group("unit")), servings, clean or text)

    single = re.search(
        rf"(?:about|around|approximately)\s*(?P<amount>{AMOUNT_PATTERN})\s*(?P<unit>kg|kilo|kilos|kilogram|kilograms|lb|lbs|pound|pounds|g|gram|grams|oz|ounce|ounces)\b",
        text,
    )
    if single:
        clean = (text[: single.start()] + " " + text[single.end() :]).strip()
        return portion_result("parsed_embedded_weight_estimate", amount_value(single.group("amount")) * unit_to_grams(single.group("unit")), servings, clean or text)
    return None


def parse_default_no_quantity(text: str, servings: float) -> dict[str, Any] | None:
    for ignored in IGNORE_TERMS:
        if text == ignored or ignored in text:
            return {"parse_status": "ignored_non_nutritive", "batch_g": 0.0, "portion_g": 0.0, "clean_name": text}
    for terms, batch_g, status in NO_QUANTITY_DEFAULTS:
        if any(term in text for term in terms):
            return portion_result(status, batch_g, servings, text)
    return None


def parse_ingredient_portion(raw: str, servings: float) -> dict[str, Any]:
    text = normalize_text(raw)
    if not text:
        return {"parse_status": "empty", "batch_g": None, "portion_g": None, "clean_name": ""}
    if any(term == text or term in text for term in IGNORE_TERMS):
        return {"parse_status": "ignored_non_nutritive", "batch_g": 0.0, "portion_g": 0.0, "clean_name": text}

    packaged = parse_packaged_amount(text)
    if packaged:
        amount, unit, remainder = packaged
        batch_g = amount * unit_to_grams(unit)
        return portion_result("parsed_package_label", batch_g, servings, remainder)

    ranged = parse_leading_range(text, servings)
    if ranged:
        return ranged

    count_range = parse_leading_count_range(text, servings)
    if count_range:
        return count_range

    embedded_weight = parse_embedded_weight(text, servings)
    if embedded_weight:
        return embedded_weight

    number = parse_number(text)
    if number is None:
        default = parse_default_no_quantity(text, servings)
        if default:
            return default
        return {"parse_status": "needs_manual_quantity", "batch_g": None, "portion_g": None, "clean_name": clean_ingredient_name(text)}

    tokens = text.split()
    number_token_count = 1
    if len(tokens) >= 2 and re.match(r"^\d+$", tokens[0]) and re.match(r"^\d+/\d+$", tokens[1]):
        number_token_count = 2
    unit = tokens[number_token_count].strip(".,;:()") if len(tokens) > number_token_count else ""
    clean_name = clean_ingredient_name(" ".join(tokens[number_token_count + 1:]) if unit else text)

    if unit in GRAM_UNITS:
        batch_g = number * GRAM_UNITS[unit]
        status = "parsed_weight"
    elif unit in VOLUME_TO_GRAM:
        batch_g = number * VOLUME_TO_GRAM[unit]
        status = "parsed_volume_estimate"
    elif unit in {
        "piece", "pieces", "pc", "pcs", "clove", "cloves", "head", "heads",
        "bunch", "bundle", "strings", "string", "thumb", "thumbs", "stalk",
        "bunches", "bundles",
        "stalks", "slab", "pack", "packs", "can", "cans", "strip", "strips",
        "package", "packages",
        "wrapper", "wrappers", "fillet", "fillets", "steak", "steaks", "cob",
        "cobs", "ear", "ears", "cube", "cubes", "wedge", "wedges", "slice",
        "slices", "sprig", "sprigs", "block", "blocks", "knob", "knobs",
        "inch", "inches", "leaf", "leaves",
    }:
        grams_each = piece_weight(text, "bunch" if unit in {"bunch", "bundle"} else unit)
        if grams_each is None and unit in {"can", "cans"}:
            grams_each = 400.0
        if grams_each is None and unit in {"pack", "packs"}:
            grams_each = 40.0
        if grams_each is None:
            return {"parse_status": "needs_piece_weight", "batch_g": None, "portion_g": None, "clean_name": clean_ingredient_name(text)}
        batch_g = number * grams_each
        status = "parsed_piece_estimate"
    else:
        # Some source strings start with a count and immediately name the item.
        grams_each = piece_weight(text, unit)
        if grams_each is None:
            default = parse_default_no_quantity(text, servings)
            if default:
                return default
            return {"parse_status": "needs_unit_conversion", "batch_g": None, "portion_g": None, "clean_name": clean_ingredient_name(text)}
        clean_name = clean_ingredient_name(text)
        batch_g = number * grams_each
        status = "parsed_piece_estimate"

    return portion_result(status, batch_g, servings, clean_name or text)


def unit_to_grams(unit: str) -> float:
    unit = unit.strip(".,;:()").lower()
    if unit in GRAM_UNITS:
        return GRAM_UNITS[unit]
    if unit in VOLUME_TO_GRAM:
        return VOLUME_TO_GRAM[unit]
    raise ValueError(f"Unsupported package unit: {unit}")


def parse_packaged_amount(text: str) -> tuple[float, str, str] | None:
    # Handles patterns such as "1 can (13.5 ounces) coconut milk" and
    # "1 400-ml can coconut milk". The package label gives the real amount.
    paren = re.search(r"\((\d+(?:\.\d+)?)\s*(g|gram|grams|kg|kilo|kilos|kilogram|kilograms|ml|l|oz|ounces)\)", text)
    if paren:
        amount = float(paren.group(1))
        unit = paren.group(2)
        remainder = text[paren.end():] or text[:paren.start()]
        return amount, unit, remainder
    inline = re.search(r"\b\d+\s+(\d+(?:\.\d+)?)[-\s]*(g|gram|grams|kg|kilo|kilos|kilogram|kilograms|ml|l|oz|ounces)\s+(?:can|pack|packet|pouch)?\s*(.*)$", text)
    if inline:
        return float(inline.group(1)), inline.group(2), inline.group(3)
    return None


def clean_ingredient_name(text: str) -> str:
    text = normalize_text(text)
    text = re.sub(r"^\d+(?:\.\d+)?(?:/\d+)?\s+", "", text)
    text = re.sub(r"^\d+\s+\d+/\d+\s+", "", text)
    text = re.sub(r"^(?:piece|pieces|pc|pcs|grams|gram|g|kg|kilo|kilos|kilogram|kilograms|lb|lbs|pound|pounds|oz|ounces|cup|cups|tbsp|tablespoons|tsp|teaspoons)\s+", "", text)
    text = re.sub(r"\b(?:cubed|diced|sliced|chopped|crushed|minced|cleaned|cut|into|wedged|boiled|cooked|fresh|raw|large|medium|small|thinly|strips|pieces|piece|preferably|leftovers|optional|peeled|quartered|trimmed|divided|julienned|julienne|serve|serving|garnish)\b", " ", text)
    text = re.sub(r"\b(?:to taste|taste|and|or|with|without|whole|ground)\b", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def fetch_philfct(cache_path: Path, refresh: bool = False) -> list[dict[str, Any]]:
    if cache_path.exists() and not refresh:
        return json.loads(cache_path.read_text(encoding="utf-8"))

    html = requests.get(PHILFCT_URL, timeout=60).text
    table = pd.read_html(StringIO(html))[0]
    nutrients_by_code = {}
    for code in table["Food_ID"].astype(str):
        idx = html.find(f'id="{code}_data"')
        if idx < 0:
            continue
        end = html.find('<div class="modal fade"', idx + 1)
        block = html[idx:end if end > idx else idx + 12000]
        nutrients_by_code[code] = {
            "calories": extract_nutrient(block, "Energy, calculated (kcal)"),
            "protein_g": extract_nutrient(block, "Protein (g)"),
            "fat_g": extract_nutrient(block, "Total Fat (g)"),
            "carbs_g": extract_nutrient(block, "Carbohydrate, total (g)"),
            "fiber_g": extract_nutrient(block, "Fiber, total dietary (g)"),
        }

    records = []
    for _, row in table.iterrows():
        code = str(row.get("Food_ID") or "").strip()
        rec = {
            "code": code,
            "name": str(row.get("Food name and Description") or "").strip(),
            "scientific_name": str(row.get("Scientific name") or "").strip(),
            "alternate_names": str(row.get("Alternate/Common name(s)") or "").strip(),
            "edible_portion": str(row.get("Edible portion") or "").strip(),
            "per_100g": nutrients_by_code.get(code) or {},
        }
        records.append(rec)
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    cache_path.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    return records


def extract_nutrient(block: str, label: str) -> float | None:
    pattern = re.escape(label) + r".{0,300}?<strong>([^<]+)</strong>"
    match = re.search(pattern, block, flags=re.I | re.S)
    if not match:
        return None
    raw = re.sub(r"[^0-9.]+", "", match.group(1))
    if not raw:
        return None
    try:
        return float(raw)
    except Exception:
        return None


def index_philfct(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    indexed = []
    for rec in records:
        text = normalize_text(" ".join([rec.get("name", ""), rec.get("alternate_names", "")]))
        indexed.append({**rec, "_search": text, "_tokens": set(text.split())})
    return indexed


def philfct_result(rec: dict[str, Any], score: float, status: str) -> dict[str, Any]:
    return {
        "philfct_code": rec["code"],
        "philfct_name": rec["name"],
        "philfct_alternate_names": rec.get("alternate_names"),
        "philfct_edible_portion": rec.get("edible_portion"),
        "match_score": round(score, 3),
        "match_status": status,
        "per_100g": rec.get("per_100g") or {},
    }


def direct_alias_match(clean_name: str, index: list[dict[str, Any]]) -> dict[str, Any] | None:
    text = normalize_text(clean_name)
    for key, code in sorted(ALIAS_CODE.items(), key=lambda item: len(item[0]), reverse=True):
        if key in text:
            for rec in index:
                if rec.get("code") == code:
                    return philfct_result(rec, 1.0, "matched_alias")
    return None


def query_for(clean_name: str) -> str:
    text = normalize_text(clean_name)
    for key, query in sorted(ALIAS_QUERY.items(), key=lambda item: len(item[0]), reverse=True):
        if key in text:
            return query
    return text


def match_philfct(clean_name: str, index: list[dict[str, Any]], cache: dict[str, dict[str, Any] | None]) -> dict[str, Any] | None:
    alias = direct_alias_match(clean_name, index)
    if alias:
        return alias
    query = normalize_text(query_for(clean_name))
    if not query:
        return None
    if query in cache:
        return cache[query]
    qtokens = set(query.split())
    best = None
    best_score = 0.0
    candidates = [rec for rec in index if qtokens & rec["_tokens"]]
    if not candidates:
        candidates = index
    for rec in candidates:
        hay = rec["_search"]
        htokens = rec["_tokens"]
        token_score = len(qtokens & htokens) / max(len(qtokens), 1)
        seq_score = SequenceMatcher(None, query, hay).ratio()
        score = (token_score * 0.7) + (seq_score * 0.3)
        if score > best_score:
            best = rec
            best_score = score
    if not best:
        cache[query] = None
        return None
    status = "matched_high" if best_score >= 0.72 else "matched_review" if best_score >= 0.45 else "unmatched"
    if status == "unmatched":
        cache[query] = None
        return None
    result = philfct_result(best, best_score, status)
    cache[query] = result
    return result


def compute_nutrition(portion_g: float | None, per_100g: dict[str, Any]) -> dict[str, float] | None:
    if portion_g is None or portion_g <= 0:
        return None
    if not per_100g or per_100g.get("calories") is None:
        return None
    factor = portion_g / 100
    return {
        key: round(factor * float(per_100g.get(key) or 0), 2)
        for key in NUTRIENTS
    }


def sum_nutrition(items: list[dict[str, float]]) -> dict[str, float]:
    return {
        key: round(sum(item.get(key, 0.0) for item in items), 2)
        for key in NUTRIENTS
    }


def source_ingredient_strings(recipe: dict[str, Any]) -> list[str]:
    values = []
    for ingredient in recipe.get("ingredients") or []:
        if isinstance(ingredient, dict):
            name = str(ingredient.get("name") or "").strip()
            quantity = str(ingredient.get("quantity") or "").strip()
            values.append(" ".join(part for part in [quantity, name] if part))
        else:
            values.append(str(ingredient).strip())
    return [value for value in values if value]


def convert_recipe(recipe: dict[str, Any], match_index: list[dict[str, Any]], match_cache: dict[str, dict[str, Any] | None]) -> dict[str, Any]:
    servings = source_servings(recipe.get("sourceServings"))
    rows = []
    computed_parts = []
    for raw in source_ingredient_strings(recipe):
        parsed = parse_ingredient_portion(raw, servings)
        match = None
        computed = None
        if parsed["parse_status"] != "ignored_non_nutritive" and parsed.get("clean_name"):
            match = match_philfct(parsed["clean_name"], match_index, match_cache)
            if match:
                computed = compute_nutrition(parsed.get("portion_g"), match.get("per_100g") or {})
        if computed:
            computed_parts.append(computed)
        rows.append({
            "source_text": raw,
            "clean_name": parsed.get("clean_name"),
            "source_servings": servings,
            "batch_g": parsed.get("batch_g"),
            "portion_g": parsed.get("portion_g"),
            "parse_status": parsed.get("parse_status"),
            "philfct": match,
            "computed": computed,
        })
    reviewable_count = sum(1 for row in rows if row.get("parse_status") != "ignored_non_nutritive")
    parsed_count = sum(1 for row in rows if row.get("portion_g") not in (None, 0))
    matched_count = sum(1 for row in rows if row.get("computed"))
    total_count = len(rows)
    computed_nutrition = sum_nutrition(computed_parts)
    return {
        "sourceRecipeId": recipe.get("id"),
        "sourceRecipeName": recipe.get("name"),
        "sourceServings": recipe.get("sourceServings"),
        "parsedIngredientCount": parsed_count,
        "matchedIngredientCount": matched_count,
        "sourceIngredientCount": total_count,
        "reviewableIngredientCount": reviewable_count,
        "coverage": round(matched_count / reviewable_count, 3) if reviewable_count else 1,
        "computedPhilFctNutrition": computed_nutrition,
        "portionIngredients": rows,
    }


def export_review_csv(path: Path, converted: list[dict[str, Any]]) -> None:
    import csv

    path.parent.mkdir(parents=True, exist_ok=True)
    headers = [
        "recipe_id", "recipe_name", "source_servings", "source_text", "clean_name",
        "batch_g", "portion_g", "parse_status", "philfct_code", "philfct_name",
        "match_score", "match_status", "computed_kcal", "computed_protein_g",
        "computed_carbs_g", "computed_fat_g", "computed_fiber_g",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=headers)
        writer.writeheader()
        for meal in converted:
            for row in meal["portionIngredients"]:
                match = row.get("philfct") or {}
                computed = row.get("computed") or {}
                writer.writerow({
                    "recipe_id": meal["sourceRecipeId"],
                    "recipe_name": meal["sourceRecipeName"],
                    "source_servings": meal["sourceServings"],
                    "source_text": row.get("source_text"),
                    "clean_name": row.get("clean_name"),
                    "batch_g": row.get("batch_g"),
                    "portion_g": row.get("portion_g"),
                    "parse_status": row.get("parse_status"),
                    "philfct_code": match.get("philfct_code"),
                    "philfct_name": match.get("philfct_name"),
                    "match_score": match.get("match_score"),
                    "match_status": match.get("match_status"),
                    "computed_kcal": computed.get("calories"),
                    "computed_protein_g": computed.get("protein_g"),
                    "computed_carbs_g": computed.get("carbs_g"),
                    "computed_fat_g": computed.get("fat_g"),
                    "computed_fiber_g": computed.get("fiber_g"),
                })


def main() -> int:
    parser = argparse.ArgumentParser(description="Build ingredient-level PhilFCT portion draft catalog.")
    parser.add_argument("--recipes", type=Path, default=DEFAULT_RECIPES)
    parser.add_argument("--complete-plate", type=Path, default=DEFAULT_COMPLETE_PLATE)
    parser.add_argument("--philfct-cache", type=Path, default=DEFAULT_PHILFCT_CACHE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--review-csv", type=Path, default=DEFAULT_REVIEW)
    parser.add_argument("--refresh-philfct", action="store_true")
    args = parser.parse_args()

    recipes = json.loads(args.recipes.read_text(encoding="utf-8"))
    philfct = fetch_philfct(args.philfct_cache, refresh=args.refresh_philfct)
    match_index = index_philfct(philfct)
    match_cache: dict[str, dict[str, Any] | None] = {}
    converted = [convert_recipe(recipe, match_index, match_cache) for recipe in recipes]

    parse_status = Counter(
        row["parse_status"]
        for meal in converted
        for row in meal["portionIngredients"]
    )
    match_status = Counter(
        (row.get("philfct") or {}).get("match_status") or "no_match"
        for meal in converted
        for row in meal["portionIngredients"]
        if row["parse_status"] != "ignored_non_nutritive"
    )
    coverage_values = [meal["coverage"] for meal in converted]
    fullish = sum(1 for value in coverage_values if value >= 0.8)
    partial = sum(1 for value in coverage_values if 0 < value < 0.8)
    none = sum(1 for value in coverage_values if value == 0)

    payload = {
        "version": "pcosina-philfct-portioned-catalog-v2-draft",
        "status": "draft_requires_manual_review",
        "philfctSource": PHILFCT_URL,
        "sourceRecipeCount": len(recipes),
        "summary": {
            "philfctReferenceItems": len(philfct),
            "uniqueIngredientMatchQueries": len(match_cache),
            "recipeCoverageAtLeast80Percent": fullish,
            "recipeCoveragePartial": partial,
            "recipeCoverageNone": none,
            "parseStatusCounts": dict(parse_status),
            "matchStatusCounts": dict(match_status),
        },
        "meals": converted,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    export_review_csv(args.review_csv, converted)

    print(f"Output: {args.output}")
    print(f"Review CSV: {args.review_csv}")
    print(json.dumps(payload["summary"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
