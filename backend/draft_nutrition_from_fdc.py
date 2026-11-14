"""Draft per-serving recipe nutrition from USDA FoodData Central.

This script produces review artifacts only. It intentionally writes
`review_status=pending_review` so calculated values cannot be mistaken for
dietitian-reviewed catalog data.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol


BACKEND_ROOT = Path(__file__).resolve().parent
DEFAULT_REVIEW_QUEUE = BACKEND_ROOT.parent / "docs" / "production_readiness" / "nutrition_review_queue.csv"
DEFAULT_OUTPUT = BACKEND_ROOT.parent / "docs" / "production_readiness" / "nutrition_draft_fdc_estimates.csv"
DEFAULT_AUDIT = BACKEND_ROOT.parent / "docs" / "production_readiness" / "nutrition_draft_fdc_audit.json"
DEFAULT_CACHE = BACKEND_ROOT / "seed_data" / "fdc_food_matches_cache.json"
FDC_SEARCH_URL = "https://api.nal.usda.gov/fdc/v1/foods/search"
FDC_DATA_TYPES = ["Foundation", "SR Legacy", "Survey (FNDDS)"]
NUTRIENT_KEYS = {
    "calories": {1008, 2047, 2048},
    "protein_grams": {1003},
    "carbs_grams": {1005},
    "fats_grams": {1004},
    "fiber_grams": {1079},
    "sodium_mg": {1093},
    "sugar_grams": {2000},
}
NUTRIENT_NAMES = {
    "calories": ("energy",),
    "protein_grams": ("protein",),
    "carbs_grams": ("carbohydrate",),
    "fats_grams": ("total lipid", "total fat"),
    "fiber_grams": ("fiber",),
    "sodium_mg": ("sodium",),
    "sugar_grams": ("sugars", "sugar"),
}
IMPORT_BOUNDS = {
    "calories": (1, 3000),
    "protein_grams": (0, 300),
    "carbs_grams": (0, 500),
    "fats_grams": (0, 250),
    "fiber_grams": (0, 120),
    "sodium_mg": (0, 10000),
    "sugar_grams": (0, 250),
}
OUTPUT_FIELDS = [
    "priority",
    "recipe_id",
    "title",
    "meal_type",
    "source_servings",
    "minutes",
    "source_prep_time",
    "source_cook_time",
    "source_total_time",
    "source_ingredient_names",
    "ingredient_count",
    "ingredients",
    "instructions_excerpt",
    "current_calories",
    "current_protein_grams",
    "current_carbs_grams",
    "current_fats_grams",
    "current_fiber_grams",
    "calories",
    "protein_grams",
    "carbs_grams",
    "fats_grams",
    "fiber_grams",
    "sodium_mg",
    "sugar_grams",
    "source",
    "confidence",
    "review_status",
    "notes",
    "draft_status",
    "matched_ingredient_count",
    "unmatched_ingredients",
    "fdc_matches",
]


class NutritionResolver(Protocol):
    def search_nutrients(self, query: str) -> dict[str, Any] | None:
        ...

    def save(self) -> None:
        ...


@dataclass
class ParsedIngredient:
    original: str
    query: str
    grams: float | None
    reason: str = ""


class FdcClient:
    source_name = "usda_fdc_ingredient_sum_draft"
    review_note = "summed USDA FDC ingredient matches"

    def __init__(self, api_key: str, cache_path: Path = DEFAULT_CACHE, delay_seconds: float = 0.08):
        self.api_key = api_key
        self.cache_path = cache_path
        self.delay_seconds = max(0.0, delay_seconds)
        self.cache: dict[str, Any] = {}
        self.rate_limited = False
        if cache_path.exists():
            self.cache = json.loads(cache_path.read_text(encoding="utf-8"))

    def search_nutrients(self, query: str) -> dict[str, Any] | None:
        key = normalize_query(query)
        if not key:
            return None
        if self.rate_limited:
            return None
        if key in self.cache:
            cached = self.cache[key]
            return cached if isinstance(cached, dict) else None
        payload = {
            "query": key,
            "pageSize": 5,
            "dataType": FDC_DATA_TYPES,
        }
        request = urllib.request.Request(
            f"{FDC_SEARCH_URL}?api_key={urllib.parse.quote(self.api_key)}",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                data = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            if exc.code in {400, 404}:
                self.cache[key] = {}
                return None
            if exc.code == 429:
                self.rate_limited = True
                return None
            raise RuntimeError(f"FDC search failed for {key}: HTTP {exc.code}") from exc
        except Exception as exc:
            raise RuntimeError(f"FDC search failed for {key}: {exc}") from exc
        foods = data.get("foods") or []
        result = None
        if foods:
            result = food_search_result_to_nutrients(select_best_food(foods, key))
        self.cache[key] = result or {}
        time.sleep(self.delay_seconds)
        return result

    def save(self) -> None:
        self.cache_path.parent.mkdir(parents=True, exist_ok=True)
        self.cache_path.write_text(json.dumps(self.cache, ensure_ascii=True, indent=2), encoding="utf-8")


class LocalReferenceResolver:
    source_name = "local_reference_ingredient_sum_draft"
    review_note = "summed local reference ingredient profiles"

    def search_nutrients(self, query: str) -> dict[str, Any] | None:
        normalized = normalize_query(query)
        if not normalized:
            return None
        profile_key = select_local_profile_key(normalized)
        if profile_key is None:
            return None
        return {
            "fdcId": f"local:{profile_key}",
            "description": profile_key,
            "dataType": "Local reference",
            "nutrientsPer100g": LOCAL_REFERENCE_NUTRIENTS[profile_key],
        }

    def save(self) -> None:
        return None


LOCAL_REFERENCE_NUTRIENTS: dict[str, dict[str, float]] = {
    "annatto oil": {"calories": 884, "protein_grams": 0, "carbs_grams": 0, "fats_grams": 100, "fiber_grams": 0, "sodium_mg": 0, "sugar_grams": 0},
    "bacon": {"calories": 541, "protein_grams": 37, "carbs_grams": 1.4, "fats_grams": 42, "fiber_grams": 0, "sodium_mg": 1717, "sugar_grams": 0},
    "banana blossom": {"calories": 51, "protein_grams": 1.6, "carbs_grams": 9.9, "fats_grams": 0.6, "fiber_grams": 5.7, "sodium_mg": 48, "sugar_grams": 3.0},
    "beef": {"calories": 250, "protein_grams": 26, "carbs_grams": 0, "fats_grams": 15, "fiber_grams": 0, "sodium_mg": 72, "sugar_grams": 0},
    "beef ribs": {"calories": 306, "protein_grams": 24, "carbs_grams": 0, "fats_grams": 23, "fiber_grams": 0, "sodium_mg": 90, "sugar_grams": 0},
    "bell pepper": {"calories": 31, "protein_grams": 1, "carbs_grams": 6, "fats_grams": 0.3, "fiber_grams": 2.1, "sodium_mg": 4, "sugar_grams": 4.2},
    "bitter melon": {"calories": 17, "protein_grams": 1, "carbs_grams": 3.7, "fats_grams": 0.2, "fiber_grams": 2.8, "sodium_mg": 5, "sugar_grams": 1.0},
    "bok choy": {"calories": 13, "protein_grams": 1.5, "carbs_grams": 2.2, "fats_grams": 0.2, "fiber_grams": 1.0, "sodium_mg": 65, "sugar_grams": 1.2},
    "broth": {"calories": 7, "protein_grams": 1, "carbs_grams": 0.5, "fats_grams": 0.2, "fiber_grams": 0, "sodium_mg": 250, "sugar_grams": 0},
    "cabbage": {"calories": 25, "protein_grams": 1.3, "carbs_grams": 5.8, "fats_grams": 0.1, "fiber_grams": 2.5, "sodium_mg": 18, "sugar_grams": 3.2},
    "carrot": {"calories": 41, "protein_grams": 0.9, "carbs_grams": 9.6, "fats_grams": 0.2, "fiber_grams": 2.8, "sodium_mg": 69, "sugar_grams": 4.7},
    "cassava": {"calories": 160, "protein_grams": 1.4, "carbs_grams": 38, "fats_grams": 0.3, "fiber_grams": 1.8, "sodium_mg": 14, "sugar_grams": 1.7},
    "chicken": {"calories": 165, "protein_grams": 31, "carbs_grams": 0, "fats_grams": 3.6, "fiber_grams": 0, "sodium_mg": 74, "sugar_grams": 0},
    "coconut cream": {"calories": 330, "protein_grams": 3.6, "carbs_grams": 6.7, "fats_grams": 34.7, "fiber_grams": 2.2, "sodium_mg": 20, "sugar_grams": 3.3},
    "coconut milk": {"calories": 230, "protein_grams": 2.3, "carbs_grams": 5.5, "fats_grams": 24, "fiber_grams": 2.2, "sodium_mg": 15, "sugar_grams": 3.3},
    "cooking oil": {"calories": 884, "protein_grams": 0, "carbs_grams": 0, "fats_grams": 100, "fiber_grams": 0, "sodium_mg": 0, "sugar_grams": 0},
    "corn": {"calories": 96, "protein_grams": 3.4, "carbs_grams": 21, "fats_grams": 1.5, "fiber_grams": 2.4, "sodium_mg": 1, "sugar_grams": 4.5},
    "crab": {"calories": 97, "protein_grams": 19, "carbs_grams": 0, "fats_grams": 1.5, "fiber_grams": 0, "sodium_mg": 1072, "sugar_grams": 0},
    "egg": {"calories": 143, "protein_grams": 13, "carbs_grams": 1.1, "fats_grams": 9.5, "fiber_grams": 0, "sodium_mg": 142, "sugar_grams": 0.4},
    "eggplant": {"calories": 25, "protein_grams": 1, "carbs_grams": 5.9, "fats_grams": 0.2, "fiber_grams": 3.0, "sodium_mg": 2, "sugar_grams": 3.5},
    "fish": {"calories": 140, "protein_grams": 24, "carbs_grams": 0, "fats_grams": 5, "fiber_grams": 0, "sodium_mg": 60, "sugar_grams": 0},
    "fish sauce": {"calories": 35, "protein_grams": 5, "carbs_grams": 3.6, "fats_grams": 0, "fiber_grams": 0, "sodium_mg": 7851, "sugar_grams": 3.6},
    "flour": {"calories": 364, "protein_grams": 10, "carbs_grams": 76, "fats_grams": 1, "fiber_grams": 2.7, "sodium_mg": 2, "sugar_grams": 0.3},
    "garlic": {"calories": 149, "protein_grams": 6.4, "carbs_grams": 33, "fats_grams": 0.5, "fiber_grams": 2.1, "sodium_mg": 17, "sugar_grams": 1.0},
    "ginger": {"calories": 80, "protein_grams": 1.8, "carbs_grams": 18, "fats_grams": 0.8, "fiber_grams": 2.0, "sodium_mg": 13, "sugar_grams": 1.7},
    "green papaya": {"calories": 43, "protein_grams": 0.5, "carbs_grams": 11, "fats_grams": 0.3, "fiber_grams": 1.7, "sodium_mg": 8, "sugar_grams": 7.8},
    "green peas": {"calories": 81, "protein_grams": 5.4, "carbs_grams": 14, "fats_grams": 0.4, "fiber_grams": 5.1, "sodium_mg": 5, "sugar_grams": 5.7},
    "ham": {"calories": 145, "protein_grams": 21, "carbs_grams": 1.5, "fats_grams": 5.5, "fiber_grams": 0, "sodium_mg": 1200, "sugar_grams": 1.5},
    "hot dog": {"calories": 290, "protein_grams": 10, "carbs_grams": 4, "fats_grams": 26, "fiber_grams": 0, "sodium_mg": 1090, "sugar_grams": 1},
    "jackfruit": {"calories": 95, "protein_grams": 1.7, "carbs_grams": 23, "fats_grams": 0.6, "fiber_grams": 1.5, "sodium_mg": 2, "sugar_grams": 19},
    "liver": {"calories": 135, "protein_grams": 20, "carbs_grams": 3.9, "fats_grams": 3.6, "fiber_grams": 0, "sodium_mg": 69, "sugar_grams": 0},
    "liver spread": {"calories": 240, "protein_grams": 12, "carbs_grams": 4, "fats_grams": 19, "fiber_grams": 0, "sodium_mg": 760, "sugar_grams": 1},
    "mushroom": {"calories": 22, "protein_grams": 3.1, "carbs_grams": 3.3, "fats_grams": 0.3, "fiber_grams": 1.0, "sodium_mg": 5, "sugar_grams": 2.0},
    "mackerel": {"calories": 205, "protein_grams": 19, "carbs_grams": 0, "fats_grams": 14, "fiber_grams": 0, "sodium_mg": 90, "sugar_grams": 0},
    "milkfish": {"calories": 190, "protein_grams": 20, "carbs_grams": 0, "fats_grams": 12, "fiber_grams": 0, "sodium_mg": 65, "sugar_grams": 0},
    "moringa leaves": {"calories": 64, "protein_grams": 9.4, "carbs_grams": 8.3, "fats_grams": 1.4, "fiber_grams": 2.0, "sodium_mg": 9, "sugar_grams": 0},
    "mung beans": {"calories": 105, "protein_grams": 7, "carbs_grams": 19, "fats_grams": 0.4, "fiber_grams": 7.6, "sodium_mg": 2, "sugar_grams": 2},
    "mussels": {"calories": 86, "protein_grams": 12, "carbs_grams": 3.7, "fats_grams": 2.2, "fiber_grams": 0, "sodium_mg": 286, "sugar_grams": 0},
    "noodles": {"calories": 138, "protein_grams": 4.5, "carbs_grams": 25, "fats_grams": 2.1, "fiber_grams": 1.2, "sodium_mg": 5, "sugar_grams": 0.5},
    "okra": {"calories": 33, "protein_grams": 1.9, "carbs_grams": 7.5, "fats_grams": 0.2, "fiber_grams": 3.2, "sodium_mg": 7, "sugar_grams": 1.5},
    "onion": {"calories": 40, "protein_grams": 1.1, "carbs_grams": 9.3, "fats_grams": 0.1, "fiber_grams": 1.7, "sodium_mg": 4, "sugar_grams": 4.2},
    "pasta": {"calories": 158, "protein_grams": 5.8, "carbs_grams": 31, "fats_grams": 0.9, "fiber_grams": 1.8, "sodium_mg": 1, "sugar_grams": 0.6},
    "peanut butter": {"calories": 588, "protein_grams": 25, "carbs_grams": 20, "fats_grams": 50, "fiber_grams": 6, "sodium_mg": 17, "sugar_grams": 9},
    "peanuts": {"calories": 567, "protein_grams": 26, "carbs_grams": 16, "fats_grams": 49, "fiber_grams": 8.5, "sodium_mg": 18, "sugar_grams": 4.7},
    "pork": {"calories": 242, "protein_grams": 27, "carbs_grams": 0, "fats_grams": 14, "fiber_grams": 0, "sodium_mg": 62, "sugar_grams": 0},
    "pork belly": {"calories": 518, "protein_grams": 9.3, "carbs_grams": 0, "fats_grams": 53, "fiber_grams": 0, "sodium_mg": 32, "sugar_grams": 0},
    "pork ribs": {"calories": 291, "protein_grams": 24, "carbs_grams": 0, "fats_grams": 21, "fiber_grams": 0, "sodium_mg": 90, "sugar_grams": 0},
    "potato": {"calories": 77, "protein_grams": 2, "carbs_grams": 17, "fats_grams": 0.1, "fiber_grams": 2.2, "sodium_mg": 6, "sugar_grams": 0.8},
    "rice": {"calories": 130, "protein_grams": 2.7, "carbs_grams": 28, "fats_grams": 0.3, "fiber_grams": 0.4, "sodium_mg": 1, "sugar_grams": 0.1},
    "salmon": {"calories": 208, "protein_grams": 20, "carbs_grams": 0, "fats_grams": 13, "fiber_grams": 0, "sodium_mg": 59, "sugar_grams": 0},
    "sayote": {"calories": 19, "protein_grams": 0.8, "carbs_grams": 4.5, "fats_grams": 0.1, "fiber_grams": 1.7, "sodium_mg": 2, "sugar_grams": 1.7},
    "seasoning mix": {"calories": 200, "protein_grams": 10, "carbs_grams": 35, "fats_grams": 2, "fiber_grams": 0, "sodium_mg": 12000, "sugar_grams": 5},
    "shrimp": {"calories": 99, "protein_grams": 24, "carbs_grams": 0.2, "fats_grams": 0.3, "fiber_grams": 0, "sodium_mg": 111, "sugar_grams": 0},
    "shrimp paste": {"calories": 180, "protein_grams": 20, "carbs_grams": 10, "fats_grams": 6, "fiber_grams": 0, "sodium_mg": 7000, "sugar_grams": 1},
    "soy sauce": {"calories": 53, "protein_grams": 8, "carbs_grams": 4.9, "fats_grams": 0.6, "fiber_grams": 0.8, "sodium_mg": 5493, "sugar_grams": 0.4},
    "soybean paste": {"calories": 198, "protein_grams": 12, "carbs_grams": 26, "fats_grams": 6, "fiber_grams": 5.4, "sodium_mg": 3728, "sugar_grams": 6},
    "spinach": {"calories": 23, "protein_grams": 2.9, "carbs_grams": 3.6, "fats_grams": 0.4, "fiber_grams": 2.2, "sodium_mg": 79, "sugar_grams": 0.4},
    "squid": {"calories": 92, "protein_grams": 15.6, "carbs_grams": 3.1, "fats_grams": 1.4, "fiber_grams": 0, "sodium_mg": 44, "sugar_grams": 0},
    "squash": {"calories": 45, "protein_grams": 1, "carbs_grams": 12, "fats_grams": 0.1, "fiber_grams": 2, "sodium_mg": 4, "sugar_grams": 2.2},
    "sugar": {"calories": 387, "protein_grams": 0, "carbs_grams": 100, "fats_grams": 0, "fiber_grams": 0, "sodium_mg": 1, "sugar_grams": 100},
    "taro": {"calories": 112, "protein_grams": 1.5, "carbs_grams": 26, "fats_grams": 0.2, "fiber_grams": 4.1, "sodium_mg": 11, "sugar_grams": 0.4},
    "tilapia": {"calories": 128, "protein_grams": 26, "carbs_grams": 0, "fats_grams": 2.7, "fiber_grams": 0, "sodium_mg": 56, "sugar_grams": 0},
    "tofu": {"calories": 76, "protein_grams": 8, "carbs_grams": 1.9, "fats_grams": 4.8, "fiber_grams": 0.3, "sodium_mg": 7, "sugar_grams": 0.6},
    "tomato": {"calories": 18, "protein_grams": 0.9, "carbs_grams": 3.9, "fats_grams": 0.2, "fiber_grams": 1.2, "sodium_mg": 5, "sugar_grams": 2.6},
    "tuna": {"calories": 132, "protein_grams": 28, "carbs_grams": 0, "fats_grams": 1, "fiber_grams": 0, "sodium_mg": 47, "sugar_grams": 0},
    "vinegar": {"calories": 18, "protein_grams": 0, "carbs_grams": 0.9, "fats_grams": 0, "fiber_grams": 0, "sodium_mg": 2, "sugar_grams": 0.4},
    "yardlong beans": {"calories": 47, "protein_grams": 2.8, "carbs_grams": 8.4, "fats_grams": 0.4, "fiber_grams": 2.6, "sodium_mg": 4, "sugar_grams": 3.4},
    "cheese": {"calories": 402, "protein_grams": 25, "carbs_grams": 1.3, "fats_grams": 33, "fiber_grams": 0, "sodium_mg": 621, "sugar_grams": 0.5},
}


def select_local_profile_key(query: str) -> str | None:
    normalized = normalize_query(query)
    if not normalized:
        return None
    aliases = {
        "annatto": "annatto oil",
        "achuete": "annatto oil",
        "bihon": "noodles",
        "chicken broth": "broth",
        "beef broth": "broth",
        "pork broth": "broth",
        "bouillon": "broth",
        "cream": "coconut cream" if "coconut" in normalized else "",
        "gabi": "taro",
        "kangkong": "spinach",
        "liempo": "pork belly",
        "mackerel": "mackerel",
        "noodle": "noodles",
        "pancit": "noodles",
        "salmon": "salmon",
        "sparerib": "pork ribs",
        "spareribs": "pork ribs",
        "stewed tomato": "tomato sauce",
        "tomato sauce": "tomato",
    }
    for token, target in aliases.items():
        if token and token in normalized and target:
            return target
    for key in sorted(LOCAL_REFERENCE_NUTRIENTS, key=len, reverse=True):
        if key in normalized:
            return key
    if "fish" in normalized:
        return "fish"
    if "pork" in normalized:
        return "pork"
    if "beef" in normalized:
        return "beef"
    return None


def food_search_result_to_nutrients(food: dict[str, Any]) -> dict[str, Any]:
    nutrients = {key: 0.0 for key in NUTRIENT_KEYS}
    for item in food.get("foodNutrients") or []:
        nutrient_id = int(item.get("nutrientId") or 0)
        nutrient_name = str(item.get("nutrientName") or "").strip().lower()
        value = _float_or_none(item.get("value"))
        if value is None:
            continue
        for key, ids in NUTRIENT_KEYS.items():
            if nutrient_id in ids or any(name in nutrient_name for name in NUTRIENT_NAMES[key]):
                nutrients[key] = float(value)
                break
    return {
        "fdcId": food.get("fdcId"),
        "description": food.get("description"),
        "dataType": food.get("dataType"),
        "nutrientsPer100g": nutrients,
    }


def select_best_food(foods: list[dict[str, Any]], query: str) -> dict[str, Any]:
    if not foods:
        return {}
    query_text = normalize_query(query)
    query_tokens = {token for token in query_text.split() if token}
    return max(foods, key=lambda food: food_match_score(food, query_text, query_tokens))


def food_match_score(food: dict[str, Any], query_text: str, query_tokens: set[str]) -> float:
    description = str(food.get("description") or "").strip().lower()
    data_type = str(food.get("dataType") or "").strip()
    score = 0.0
    score += {"Foundation": 35.0, "SR Legacy": 30.0, "Survey (FNDDS)": 20.0}.get(data_type, -20.0)
    if description == query_text:
        score += 100.0
    elif description.startswith(query_text):
        score += 70.0
    description_tokens = {token for token in re.split(r"[^a-z0-9]+", description) if token}
    if query_tokens and query_tokens.issubset(description_tokens):
        score += 35.0
    bad_terms = {"sauce", "tea", "beverage", "drink", "soup", "babyfood", "restaurant", "fast food", "snack"}
    for term in bad_terms:
        if term in description and term not in query_text:
            score -= 80.0
    if "raw" in description:
        score += 8.0
    if "cooked" in description:
        score += 5.0
    source_score = _float_or_none(food.get("score"))
    if source_score is not None:
        score += min(source_score / 100.0, 20.0)
    return score


def draft_rows(
    rows: list[dict[str, str]],
    resolver: NutritionResolver,
    *,
    priority: str | None = None,
    limit: int | None = None,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    output: list[dict[str, Any]] = []
    considered = 0
    for row in rows:
        if priority and str(row.get("priority") or "").upper() != priority.upper():
            continue
        if any(str(row.get(key) or "").strip() for key in ("calories", "protein_grams", "carbs_grams", "fats_grams", "fiber_grams")):
            continue
        if limit is not None and considered >= limit:
            break
        considered += 1
        output.append(draft_row(row, resolver))
    summary = summarize_drafts(output)
    summary["consideredRows"] = considered
    return output, summary


def draft_row(row: dict[str, str], resolver: NutritionResolver) -> dict[str, Any]:
    servings = parse_servings(row.get("source_servings"))
    draft = {field: row.get(field, "") for field in OUTPUT_FIELDS}
    if servings is None or servings <= 0:
        draft["draft_status"] = "needs_manual_serving_review"
        draft["notes"] = "Missing or invalid source serving count; calculate manually before import."
        return draft

    totals = {key: 0.0 for key in NUTRIENT_KEYS}
    matches: list[str] = []
    unmatched: list[str] = []
    used_missing_quantity_defaults = False
    for ingredient in split_ingredients(row.get("ingredients") or ""):
        parsed = parse_ingredient(ingredient)
        if parsed.grams is None or parsed.grams <= 0 or not parsed.query:
            unmatched.append(f"{ingredient} ({parsed.reason or 'no usable quantity'})")
            continue
        nutrient_result = resolver.search_nutrients(parsed.query)
        if not nutrient_result:
            unmatched.append(f"{ingredient} (no FDC match for {parsed.query})")
            continue
        nutrients = nutrient_result.get("nutrientsPer100g") or {}
        for key in totals:
            totals[key] += float(nutrients.get(key) or 0.0) * parsed.grams / 100.0
        matches.append(
            f"{ingredient} => {parsed.query} [{round(parsed.grams, 1)}g, FDC {nutrient_result.get('fdcId')}]"
        )
    if not matches:
        default_matches, default_unmatched = draft_missing_quantity_totals(
            split_ingredients(row.get("ingredients") or ""),
            resolver,
            totals,
        )
        if default_matches:
            matches = default_matches
            unmatched = default_unmatched
            used_missing_quantity_defaults = True

    per_serving = {key: totals[key] / servings for key in totals}
    has_nutrition_signal = per_serving["calories"] > 0 and any(
        per_serving[key] > 0 for key in ("protein_grams", "carbs_grams", "fats_grams")
    )
    if not matches or not has_nutrition_signal:
        draft["draft_status"] = "needs_manual_nutrition_review"
    elif unmatched:
        draft["draft_status"] = "draft_partial_match"
    elif used_missing_quantity_defaults:
        draft["draft_status"] = "draft_missing_quantity_estimate"
    else:
        draft["draft_status"] = "draft_ready_for_review"

    draft["matched_ingredient_count"] = len(matches)
    draft["unmatched_ingredients"] = " | ".join(unmatched)
    draft["fdc_matches"] = " | ".join(matches)
    if draft["draft_status"] != "needs_manual_nutrition_review" and not within_import_bounds(per_serving):
        draft["draft_status"] = "needs_manual_nutrition_review"
        unmatched.append("computed nutrition outside database import bounds")
        draft["unmatched_ingredients"] = " | ".join(unmatched)
    if draft["draft_status"] == "needs_manual_nutrition_review":
        for key in ("calories", "protein_grams", "carbs_grams", "fats_grams", "fiber_grams", "sodium_mg", "sugar_grams"):
            draft[key] = ""
        draft["source"] = ""
        draft["confidence"] = ""
        draft["review_status"] = ""
        draft["notes"] = "Needs manual nutrition review; automated draft did not produce importable nutrition signal."
    else:
        draft["calories"] = int(round(per_serving["calories"]))
        draft["protein_grams"] = round(per_serving["protein_grams"], 1)
        draft["carbs_grams"] = round(per_serving["carbs_grams"], 1)
        draft["fats_grams"] = round(per_serving["fats_grams"], 1)
        draft["fiber_grams"] = round(per_serving["fiber_grams"], 1)
        draft["sodium_mg"] = int(round(per_serving["sodium_mg"]))
        draft["sugar_grams"] = round(per_serving["sugar_grams"], 1)
        source_name = (
            "local_reference_missing_quantity_draft"
            if used_missing_quantity_defaults
            else str(getattr(resolver, "source_name", "usda_fdc_ingredient_sum_draft"))
        )
        review_note = (
            "used source ingredient names with default ingredient quantities"
            if used_missing_quantity_defaults
            else str(getattr(resolver, "review_note", "summed ingredient matches"))
        )
        draft["source"] = source_name
        draft["confidence"] = "api_estimate"
        draft["review_status"] = "pending_review"
        draft["notes"] = (
            f"Draft only: {review_note} and divided by source_servings={servings:g}. "
            "Reviewer must verify ingredient matches, edible yield, oil absorption, and serving size before marking reviewed."
        )
    return draft


def draft_missing_quantity_totals(
    ingredients: list[str],
    resolver: NutritionResolver,
    totals: dict[str, float],
) -> tuple[list[str], list[str]]:
    matches: list[str] = []
    unmatched: list[str] = []
    for ingredient in ingredients:
        cleaned = clean_ingredient_text(ingredient)
        if not cleaned or is_ignored_ingredient(cleaned):
            unmatched.append(f"{ingredient} (ignored seasoning/water)")
            continue
        query = normalize_query(cleaned)
        grams = default_missing_quantity_grams(query)
        if grams is None:
            unmatched.append(f"{ingredient} (missing quantity)")
            continue
        nutrient_result = resolver.search_nutrients(query)
        if not nutrient_result:
            unmatched.append(f"{ingredient} (no local reference match for {query})")
            continue
        nutrients = nutrient_result.get("nutrientsPer100g") or {}
        for key in totals:
            totals[key] += float(nutrients.get(key) or 0.0) * grams / 100.0
        matches.append(
            f"{ingredient} => {query} [{round(grams, 1)}g default, FDC {nutrient_result.get('fdcId')}]"
        )
    return matches, unmatched


def default_missing_quantity_grams(query: str) -> float | None:
    normalized = normalize_query(query)
    if "coconut milk" in normalized:
        return 240.0
    if "coconut cream" in normalized:
        return 120.0
    if "pork" in normalized:
        return 150.0
    if "vinegar" in normalized or "soy sauce" in normalized or "fish sauce" in normalized:
        return 15.0
    if "garlic" in normalized:
        return 6.0
    if "onion" in normalized:
        return 110.0
    if "tomato" in normalized:
        return 120.0
    if "chili" in normalized or "siling" in normalized:
        return 10.0
    return None


def within_import_bounds(values: dict[str, float]) -> bool:
    for key, (low, high) in IMPORT_BOUNDS.items():
        value = float(values.get(key) or 0.0)
        if value < low or value > high:
            return False
    return True


def parse_ingredient(text: str) -> ParsedIngredient:
    original = str(text or "").strip()
    lowered_original = original.lower()
    pack_match = re.match(
        r"^\s*(?P<count>\d+(?:\.\d+)?)\s*\(\s*(?P<amount>\d+(?:\.\d+)?)\s*-?\s*(?P<unit>ml|g|grams?)\s*\)\s*packs?\s*(?P<food>.*)$",
        lowered_original,
        re.IGNORECASE,
    )
    if pack_match:
        count = float(pack_match.group("count"))
        amount = float(pack_match.group("amount"))
        unit = normalize_unit(pack_match.group("unit"))
        query = normalize_query(pack_match.group("food"))
        grams = count * amount if unit in {"g", "ml"} else None
        if grams and query:
            return ParsedIngredient(original=original, query=query, grams=grams)
    if "oil" in lowered_original and ("for deep" in lowered_original or "for frying" in lowered_original):
        return ParsedIngredient(original=original, query="cooking oil", grams=None, reason="ignored frying oil reserve")
    cleaned = clean_ingredient_text(original)
    if not cleaned:
        return ParsedIngredient(original=original, query="", grams=None, reason="blank")
    if is_ignored_ingredient(cleaned):
        return ParsedIngredient(original=original, query=cleaned, grams=None, reason="ignored seasoning/water")

    quantity, remainder = parse_leading_quantity(cleaned)
    if quantity is None:
        return ParsedIngredient(original=original, query=normalize_query(cleaned), grams=None, reason="missing quantity")

    unit, food = parse_unit_and_food(remainder)
    query = normalize_query(food)
    grams = grams_for_quantity(quantity, unit, query)
    if query and "oil" in query and unit == "cup" and quantity >= 0.5:
        return ParsedIngredient(original=original, query=query, grams=None, reason="ignored frying oil reserve")
    if grams is None:
        return ParsedIngredient(original=original, query=query, grams=None, reason=f"unsupported unit {unit or 'piece'}")
    return ParsedIngredient(original=original, query=query, grams=grams)


def split_ingredients(value: str) -> list[str]:
    return [part.strip() for part in str(value or "").split("|") if part.strip()]


def parse_leading_quantity(text: str) -> tuple[float | None, str]:
    match = re.match(
        r"^\s*(?P<num>(?:\d+(?:\.\d+)?\s+)?\d+/\d+|\d+(?:\.\d+)?|a|an)"
        r"(?:\s*(?:to|-|–)\s*(?P<end>\d+(?:\.\d+)?(?:/\d+)?))?\s*(?P<rest>.*)$",
        text,
        re.IGNORECASE,
    )
    if not match:
        return None, text
    quantity = parse_number(match.group("num"))
    end = parse_number(match.group("end")) if match.group("end") else None
    if quantity is not None and end is not None and end >= quantity:
        quantity = (quantity + end) / 2.0
    return quantity, match.group("rest").strip()


def parse_number(value: str) -> float | None:
    text = str(value or "").strip().lower()
    if text in {"a", "an"}:
        return 1.0
    if " " in text and "/" in text:
        whole, fraction = text.split(None, 1)
        parsed_fraction = parse_number(fraction)
        if parsed_fraction is None:
            return None
        return float(whole) + parsed_fraction
    if "/" in text:
        top, bottom = text.split("/", 1)
        try:
            if top.isdigit() and len(top) > 1 and bottom.isdigit():
                whole = int(top[:-1])
                numerator = int(top[-1])
                denominator = int(bottom)
                if whole > 0 and denominator > 0 and numerator < denominator:
                    return float(whole) + (float(numerator) / float(denominator))
            return float(top) / float(bottom)
        except Exception:
            return None
    try:
        return float(text)
    except Exception:
        return None


def parse_unit_and_food(text: str) -> tuple[str, str]:
    normalized = str(text or "").strip()
    match = re.match(
        r"^(?P<unit>lbs?\.?|pounds?|kg|kilograms?|g|grams?|oz\.?|ounces?|cups?|tablespoons?|tbsp\.?|teaspoons?|tsp\.?|pieces?|pcs?\.?|cloves?|heads?|thumbs?|bunch(?:es)?|bundles?|cobs?|quarts?|liters?|ml)\b\.?\s*(?P<food>.*)$",
        normalized,
        re.IGNORECASE,
    )
    if not match:
        return "piece", normalized
    return normalize_unit(match.group("unit")), match.group("food").strip()


def normalize_unit(unit: str) -> str:
    text = str(unit or "").strip().lower().rstrip(".")
    aliases = {
        "lb": "lb",
        "lbs": "lb",
        "pound": "lb",
        "pounds": "lb",
        "kilogram": "kg",
        "kilograms": "kg",
        "gram": "g",
        "grams": "g",
        "ounce": "oz",
        "ounces": "oz",
        "cup": "cup",
        "cups": "cup",
        "tablespoon": "tbsp",
        "tablespoons": "tbsp",
        "teaspoon": "tsp",
        "teaspoons": "tsp",
        "pieces": "piece",
        "pcs": "piece",
        "pc": "piece",
        "cloves": "clove",
        "heads": "head",
        "thumbs": "thumb",
        "bunches": "bunch",
        "bundles": "bundle",
        "cobs": "cob",
        "quart": "quart",
        "quarts": "quart",
        "liter": "liter",
        "liters": "liter",
        "ml": "ml",
    }
    return aliases.get(text, text)


def grams_for_quantity(quantity: float, unit: str, query: str) -> float | None:
    if unit == "g":
        return quantity
    if unit == "kg":
        return quantity * 1000.0
    if unit == "lb":
        return quantity * 453.592
    if unit == "oz":
        return quantity * 28.3495
    if unit == "cup":
        return quantity * cup_grams(query)
    if unit == "tbsp":
        return quantity * tablespoon_grams(query)
    if unit == "tsp":
        return quantity * tablespoon_grams(query) / 3.0
    if unit in {"piece", "clove", "head", "thumb", "bunch", "bundle", "cob"}:
        return quantity * piece_grams(unit, query)
    if unit in {"ml", "liter", "quart"}:
        if "oil" in query:
            return quantity * (0.92 if unit == "ml" else 920.0 if unit == "liter" else 870.0)
        return None
    return None


def cup_grams(query: str) -> float:
    if any(token in query for token in ("rice", "broth", "milk", "cream", "water")):
        return 240.0
    if any(token in query for token in ("flour", "sugar")):
        return 125.0
    if "peas" in query or "corn" in query:
        return 150.0
    if any(token in query for token in ("leaves", "kangkong", "malunggay", "spinach", "alugbati")):
        return 30.0
    if "oil" in query:
        return 218.0
    return 140.0


def tablespoon_grams(query: str) -> float:
    if "oil" in query:
        return 13.6
    if any(token in query for token in ("soy sauce", "vinegar", "fish sauce")):
        return 15.0
    if "sugar" in query:
        return 12.5
    if "paste" in query or "miso" in query:
        return 17.0
    return 8.0


def piece_grams(unit: str, query: str) -> float:
    if unit == "clove":
        return 3.0
    if unit == "head" and "garlic" in query:
        return 50.0
    if unit == "thumb":
        return 15.0
    if unit in {"bunch", "bundle"}:
        return 150.0
    if unit == "cob":
        return 90.0
    if "egg" in query:
        return 50.0
    if "onion" in query:
        return 110.0
    if "tomato" in query:
        return 120.0
    if "potato" in query:
        return 170.0
    if "carrot" in query:
        return 60.0
    if "bell pepper" in query:
        return 120.0
    if "pepper" in query:
        return 20.0
    if "okra" in query:
        return 12.0
    if "eggplant" in query:
        return 300.0
    if "sayote" in query:
        return 200.0
    if "hot dog" in query:
        return 45.0
    if "shrimp" in query:
        return 20.0
    if "bay leave" in query or "bay leaf" in query:
        return 0.2
    return 100.0


def clean_ingredient_text(value: str) -> str:
    text = str(value or "").strip().lower()
    text = text.replace("tablespoonsns", "tablespoons")
    text = re.sub(r"\([^)]*\)", " ", text)
    text = re.sub(r"\b(note|optional ingredient|optional)\b.*$", " ", text)
    text = re.sub(r"\b(?:cleaned|sliced|diced|cubed|chopped|minced|crushed|wedged|julienned|fresh|frozen|cooked|boiled|steamed|toasted|pounded|peeled|cut into.*|preferably.*)\b", " ", text)
    text = re.sub(r"[^a-z0-9./\s-]", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def normalize_query(value: str) -> str:
    text = clean_ingredient_text(value)
    text = re.sub(r"\b(?:small|medium|large|ripe|red|yellow|green|whole|ground|black|white|long|raw|firm)\b", " ", text)
    replacements = {
        "kalabasa": "squash",
        "sitaw": "yardlong beans",
        "bangus milkfish": "milkfish",
        "bangus": "milkfish",
        "pechay": "bok choy",
        "malunggay": "moringa leaves",
        "kamatis": "tomato",
        "sibuyas": "onion",
        "talong": "eggplant",
        "repolyo": "cabbage",
        "tokwa": "tofu",
        "bagoong alamang": "shrimp paste",
        "bagoong": "shrimp paste",
        "canola oil": "cooking oil",
        "chorizo": "pork",
        "crispy boneless dilis": "fish",
        "dilis": "fish",
        "fettuccine noodles": "pasta",
        "miso": "soybean paste",
        "magic sarap": "seasoning mix",
        "monggo": "mung beans",
        "munggo": "mung beans",
        "peas": "green peas",
        "pig leg": "pork",
        "pig parts": "pork",
        "knorr pork cube": "pork bouillon",
        "lima beans": "green peas",
        "vegetable oil": "cooking oil",
        "mayonnaise": "cooking oil",
        "shrimp bouillon": "shrimp bouillon",
        "wanton wrapper": "flour",
        "wood ear mushrooms": "mushroom",
    }
    for source, target in replacements.items():
        text = re.sub(rf"\b{re.escape(source)}\b", target, text)
    text = re.sub(r"\b(?:pieces?|pcs?|cups?|tablespoons?|tbsp|teaspoons?|tsp|lbs?|pounds?|grams?|g|kg|ounces?|oz|cloves?|heads?|thumbs?|bunch(?:es)?|bundles?|cobs?)\b", " ", text)
    text = re.sub(r"\b\d+(?:\.\d+)?(?:/\d+)?\b", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def is_ignored_ingredient(value: str) -> bool:
    text = str(value or "").strip().lower()
    return (
        "to taste" in text
        or "rock salt" in text
        or text in {"water", "salt", "pepper", "salt and pepper", "ground black pepper"}
        or bool(re.fullmatch(r"\d+(?:\.\d+)?\s*(?:teaspoons?|tsp|tablespoons?|tbsp|cups?)\s+salt", text))
        or bool(re.fullmatch(r"\d+(?:\.\d+)?\s*(?:pieces?|pcs?)\s+(?:dried\s+)?(?:bay leaves|laurel leaves)", text))
        or bool(re.fullmatch(r"\d+(?:\.\d+)?(?:/\d+)?\s*(?:cups?|quarts?|liters?|ml)\s+water(?:\b.*)?", text))
    )


def summarize_drafts(rows: list[dict[str, Any]]) -> dict[str, Any]:
    counts: dict[str, int] = {}
    unmatched_total = 0
    for row in rows:
        status = str(row.get("draft_status") or "unknown")
        counts[status] = int(counts.get(status, 0)) + 1
        unmatched = str(row.get("unmatched_ingredients") or "").strip()
        if unmatched:
            unmatched_total += len([part for part in unmatched.split("|") if part.strip()])
    return {
        "draftCount": len(rows),
        "statusCounts": dict(sorted(counts.items())),
        "unmatchedIngredientCount": unmatched_total,
    }


def read_csv_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return [dict(row) for row in csv.DictReader(handle)]


def write_csv_rows(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def write_audit(path: Path, summary: dict[str, Any], output_path: Path, cache_path: Path, source_name: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "summary": summary,
        "outputCsv": str(output_path),
        "cachePath": str(cache_path),
        "source": source_name,
        "reviewPolicy": "Draft values remain pending_review until verified by a human reviewer.",
    }
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding="utf-8")


def _float_or_none(value: Any) -> float | None:
    if value in (None, ""):
        return None
    try:
        parsed = float(str(value).strip())
        return parsed if parsed >= 0 else None
    except Exception:
        return None


def parse_servings(value: Any) -> float | None:
    text = str(value or "").strip().lower()
    if not text:
        return None
    direct = _float_or_none(text)
    if direct is not None and direct > 0:
        return direct
    if "cup" in text and any(token in text for token in ("around", "about", "approx")):
        return 1.0
    range_match = re.search(
        r"(?P<start>\d+(?:\.\d+)?(?:/\d+)?)\s*(?:to|-|–)\s*(?P<end>\d+(?:\.\d+)?(?:/\d+)?)",
        text,
    )
    if range_match:
        start = parse_number(range_match.group("start"))
        end = parse_number(range_match.group("end"))
        if start is not None and end is not None and start > 0 and end >= start:
            return (start + end) / 2.0
    count_match = re.search(
        r"(?P<count>\d+(?:\.\d+)?(?:/\d+)?)\s*(?:servings?|persons?|people|pieces?|pcs?|rolls?)\b",
        text,
    )
    if count_match:
        count = parse_number(count_match.group("count"))
        if count is not None and count > 0:
            return count
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Draft recipe nutrition from USDA FoodData Central for manual review.")
    parser.add_argument("--input", type=Path, default=DEFAULT_REVIEW_QUEUE, help="Nutrition review queue CSV.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="Draft correction CSV to write.")
    parser.add_argument("--audit", type=Path, default=DEFAULT_AUDIT, help="Audit JSON to write.")
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE, help="FDC food match cache.")
    parser.add_argument("--priority", default="P0", help="Priority to process, or blank/all for every priority.")
    parser.add_argument("--limit", type=int, default=10, help="Maximum rows to draft in one run.")
    parser.add_argument("--api-key", default=os.getenv("FDC_API_KEY") or os.getenv("USDA_FDC_API_KEY") or "DEMO_KEY")
    parser.add_argument("--resolver", choices=("fdc", "local"), default="fdc")
    args = parser.parse_args()

    priority = args.priority.strip() if args.priority and args.priority.strip().lower() not in {"all", "*"} else None
    rows = read_csv_rows(args.input)
    client: NutritionResolver
    if args.resolver == "local":
        client = LocalReferenceResolver()
    else:
        client = FdcClient(args.api_key, args.cache)
    try:
        drafted, summary = draft_rows(rows, client, priority=priority, limit=args.limit)
    finally:
        client.save()
    if getattr(client, "rate_limited", False):
        summary["rateLimited"] = True
        summary["rateLimitNote"] = "USDA FDC returned HTTP 429; rerun with FDC_API_KEY or after the DEMO_KEY window resets."
    write_csv_rows(args.output, drafted)
    write_audit(args.audit, summary, args.output, args.cache, str(getattr(client, "source_name", "ingredient_sum_draft")))
    print(f"Wrote {len(drafted)} draft nutrition row(s) to {args.output}")
    print(f"Wrote audit report to {args.audit}")
    print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
