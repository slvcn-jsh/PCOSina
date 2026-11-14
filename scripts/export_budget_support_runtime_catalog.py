"""Append low-cost PhilFCT complete plates for budget stress scenarios."""

from __future__ import annotations

import json
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
BASE = BACKEND_ROOT / "seed_data" / "pcosina_philfct_runtime_candidate_catalog_v2_coverage70.json"
OUT = BACKEND_ROOT / "seed_data" / "pcosina_philfct_runtime_candidate_catalog_v2_budget_supported.json"

FCT = {
    "cooked white rice": (129, 2.1, 29.7, 0.2, 0.4),
    "cooked munggo": (54, 4.8, 7.0, 0.5, 1.6),
    "cooked chickpeas": (198, 5.7, 39.2, 2.0, 9.1),
    "red kidney beans boiled": (138, 7.9, 24.6, 0.9, 7.9),
    "egg": (139, 12.3, 1.4, 9.4, 0.0),
    "malunggay": (108, 9.7, 12.7, 2.0, 6.7),
    "tomato": (25, 0.8, 5.2, 0.1, 0.3),
    "onion": (52, 1.7, 10.5, 0.3, 2.0),
    "garlic": (129, 7.0, 24.6, 0.3, 1.7),
    "cooking oil": (896, 0, 0, 99.6, 0),
    "eggplant": (25, 1.0, 4.9, 0.1, 1.5),
    "pechay": (20, 1.7, 3.2, 0.2, 1.5),
    "mushroom": (48, 3.8, 6.9, 0.6, 1.6),
    "okra": (30, 1.0, 6.1, 0.2, 2.6),
    "sitaw": (52, 4.0, 7.9, 0.5, 2.7),
    "squash": (47, 0.4, 10.8, 0.2, 1.1),
    "banana": (104, 0.9, 23.1, 0.9, 2.7),
    "papaya": (24, 0.7, 4.9, 0.2, 0.7),
    "peanuts": (617, 25.8, 17.1, 49.5, 8.6),
    "chicken breast": (131, 21.6, 0, 5.0, 0),
    "milkfish": (137, 23.4, 0, 4.8, 0),
    "pineapple": (55, 0.4, 13.0, 0.2, 1.4),
    "sardines": (180, 22.0, 0, 10.0, 0),
    "tuna": (132, 28.0, 0, 1.0, 0),
    "shrimp": (91, 19.0, 2.0, 0.8, 0.0),
    "squid": (80, 17.4, 0.0, 1.2, 0.0),
    "tofu": (80, 8.0, 2.0, 5.0, 1.0),
}

FCT_REF = {
    "cooked white rice": ("Rice, well-milled, boiled", "A020"),
    "cooked munggo": ("Mung bean, boiled", "D132"),
    "cooked chickpeas": ("Chickpea, dried, boiled", "C005"),
    "red kidney beans boiled": ("Kidney bean seed, red, dried, boiled", "C021"),
    "egg": ("Egg, chicken, whole", None),
    "malunggay": ("Horseradish tree lvs", "D094"),
    "tomato": ("Tomato", "D257"),
    "onion": ("Onion, Bombay bulb", "D141"),
    "garlic": ("Garlic bulb", "D084"),
    "cooking oil": ("Vegetable cooking oil", None),
    "eggplant": ("Eggplant", "D073"),
    "pechay": ("Pechay lvs", "D160"),
    "mushroom": ("Mushroom, fresh", "D133"),
    "okra": ("Okra", "D139"),
    "sitaw": ("String beans", None),
    "squash": ("Squash, stringy", "S019"),
    "banana": ("Saging, cavendish, hinog", "E085"),
    "papaya": ("Papaya fruit", None),
    "peanuts": ("Peanut", None),
    "chicken breast": ("Chicken breast", "F093"),
    "milkfish": ("Milkfish", None),
    "pineapple": ("Pineapple", "E073"),
    "sardines": ("Sardines", None),
    "tuna": ("Tuna", None),
    "shrimp": ("Shrimp, banana prawn", "G113"),
    "squid": ("Squid, boiled", "G132"),
    "tofu": ("Soybean cheese, soft curd", "C063"),
}


def nutrient(ingredients):
    totals = [0, 0, 0, 0, 0]
    for name, grams in ingredients:
        vals = FCT[name]
        for i, val in enumerate(vals):
            totals[i] += grams / 100 * val
    return {
        "calories": int(round(totals[0])),
        "protein_g": int(round(totals[1])),
        "carbs_g": int(round(totals[2])),
        "fat_g": int(round(totals[3])),
        "fiber_g": int(round(totals[4])),
    }


def rebalance_formulated_support_ingredients(ingredients, extra_tags):
    tags = set(extra_tags or [])
    if tags & {"strict_profile_non_munggo", "no_red_meat_fish_allergy_support", "pescatarian_variety_support"}:
        carb_target = 70
    elif "soy_free_vegetarian" in tags:
        carb_target = 72
    else:
        return list(ingredients)

    adjusted = [(name, float(grams)) for name, grams in ingredients]

    def grams_for(items, ingredient_name):
        for name, grams in items:
            if name == ingredient_name:
                return float(grams)
        return 0.0

    def set_grams(items, ingredient_name, grams):
        found = False
        result = []
        for name, current_grams in items:
            if name == ingredient_name:
                result.append((name, round(max(0.0, grams), 2)))
                found = True
            else:
                result.append((name, current_grams))
        if not found and grams > 0:
            result.append((ingredient_name, round(grams, 2)))
        return result

    def viable(items):
        values = nutrient(items)
        return (
            400 <= values["calories"] <= 650
            and values["protein_g"] >= 18
            and 45 <= values["carbs_g"] <= 90
            and 8 <= values["fat_g"] <= 25
            and values["fiber_g"] >= 8
        )

    for _ in range(40):
        if nutrient(adjusted)["carbs_g"] <= carb_target:
            break
        source = None
        source_floor = 0.0
        if grams_for(adjusted, "cooked white rice") > 35.0:
            source = "cooked white rice"
            source_floor = 35.0
        else:
            legume_options = [
                (grams_for(adjusted, "cooked chickpeas"), "cooked chickpeas"),
                (grams_for(adjusted, "red kidney beans boiled"), "red kidney beans boiled"),
            ]
            source_grams, source_name = max(legume_options)
            if source_grams > 55.0:
                source = source_name
                source_floor = 55.0
        if source is None:
            break
        reduction = min(5.0, grams_for(adjusted, source) - source_floor)
        if reduction <= 0:
            break
        proposed = set_grams(adjusted, source, grams_for(adjusted, source) - reduction)
        proposed = set_grams(proposed, "cooking oil", grams_for(proposed, "cooking oil") + 0.8)
        if not viable(proposed):
            break
        adjusted = proposed
    return adjusted


PHP_PER_KG = {
        "cooked white rice": 60,
        "cooked munggo": 120,
        "cooked chickpeas": 86,
        "red kidney beans boiled": 120,
        "egg": 200,
        "malunggay": 200,
        "tomato": 64,
        "onion": 99,
        "garlic": 146,
        "cooking oil": 211.26,
        "eggplant": 50,
        "pechay": 60,
        "mushroom": 180,
        "okra": 70,
        "sitaw": 70,
        "squash": 70,
        "banana": 90,
        "papaya": 100,
        "peanuts": 2778,
        "chicken breast": 260,
        "milkfish": 260,
        "pineapple": 60,
        "sardines": 180,
        "tuna": 260,
        "shrimp": 450,
        "squid": 320,
        "tofu": 120,
}


def ingredient_cost(name: str, grams: float) -> float:
    priced_grams = grams
    if name == "cooked white rice":
        priced_grams = grams * 0.34
    if name == "cooked munggo":
        priced_grams = grams * 0.40
    if name in {"cooked chickpeas", "red kidney beans boiled"}:
        priced_grams = grams * 0.45
    return round(priced_grams / 1000 * PHP_PER_KG[name], 2)


def cost(ingredients):
    total = 0.0
    for name, grams in ingredients:
        total += ingredient_cost(name, grams)
    return round(total, 2)


TEMPLATES = [
    ("Munggo Malunggay Rice Budget Plate", "Lunch", [("cooked white rice", 130), ("cooked munggo", 220), ("malunggay", 50), ("tomato", 50), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("banana", 60)]),
    ("Eggplant Egg Rice Budget Plate", "Breakfast", [("cooked white rice", 130), ("egg", 100), ("eggplant", 160), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 60)]),
    ("Tofu Pechay Munggo Dinner Plate", "Dinner", [("cooked white rice", 120), ("tofu", 140), ("cooked munggo", 150), ("pechay", 160), ("malunggay", 30), ("tomato", 50), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("papaya", 80)]),
    ("Sardines Malunggay Rice Budget Plate", "Lunch", [("cooked white rice", 130), ("sardines", 100), ("malunggay", 50), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 6), ("banana", 60)]),
    ("Tuna Pechay Budget Plate", "Dinner", [("cooked white rice", 130), ("tuna", 100), ("pechay", 130), ("okra", 70), ("tomato", 50), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("papaya", 80)]),
    ("Okra Sitaw Munggo Budget Plate", "Breakfast", [("cooked white rice", 130), ("cooked munggo", 200), ("okra", 80), ("sitaw", 80), ("tomato", 50), ("onion", 25), ("cooking oil", 8), ("banana", 60)]),
    ("Squash Munggo Rice Budget Plate", "Lunch", [("cooked white rice", 130), ("cooked munggo", 200), ("squash", 140), ("malunggay", 40), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("papaya", 80)]),
    ("Egg Pechay Rice Budget Plate", "Dinner", [("cooked white rice", 130), ("egg", 100), ("pechay", 140), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("banana", 60)]),
    ("Tofu Munggo Malunggay Budget Plate", "Breakfast", [("cooked white rice", 120), ("tofu", 120), ("cooked munggo", 150), ("malunggay", 40), ("tomato", 50), ("cooking oil", 8), ("papaya", 80)]),
    ("Sardines Pechay Egg Budget Plate", "Dinner", [("cooked white rice", 120), ("sardines", 70), ("egg", 50), ("pechay", 120), ("okra", 70), ("tomato", 50), ("onion", 25), ("cooking oil", 6), ("papaya", 80)]),
    ("Tuna Munggo Vegetable Budget Plate", "Lunch", [("cooked white rice", 120), ("tuna", 70), ("cooked munggo", 120), ("okra", 70), ("tomato", 50), ("onion", 25), ("cooking oil", 6), ("banana", 60)]),
    ("Egg Tofu Talong Budget Plate", "Breakfast", [("cooked white rice", 120), ("egg", 50), ("tofu", 120), ("eggplant", 140), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 60)]),
    ("Munggo Okra Malunggay Dinner Plate", "Dinner", [("cooked white rice", 130), ("cooked munggo", 230), ("okra", 90), ("malunggay", 45), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("papaya", 80)]),
    ("Tofu Sitaw Squash Dinner Plate", "Dinner", [("cooked white rice", 120), ("tofu", 160), ("sitaw", 100), ("squash", 160), ("malunggay", 35), ("tomato", 50), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("banana", 60)]),
]

BUDGET_VARIETY_TEMPLATES = [
    ("Chicken Garbanzo Pechay Budget Breakfast Plate", "Breakfast", [("cooked white rice", 65), ("chicken breast", 60), ("cooked chickpeas", 90), ("pechay", 150), ("tomato", 60), ("onion", 20), ("cooking oil", 8), ("papaya", 50)]),
    ("Egg Kidney Malunggay Budget Breakfast Plate", "Breakfast", [("cooked white rice", 60), ("egg", 70), ("red kidney beans boiled", 100), ("malunggay", 55), ("mushroom", 90), ("tomato", 55), ("cooking oil", 7), ("banana", 45)]),
    ("Tofu Chickpea Okra Budget Breakfast Plate", "Breakfast", [("cooked white rice", 55), ("tofu", 120), ("cooked chickpeas", 90), ("okra", 120), ("pechay", 120), ("tomato", 55), ("cooking oil", 8), ("papaya", 45)]),
    ("Sardines Kidney Tomato Budget Breakfast Plate", "Breakfast", [("cooked white rice", 60), ("sardines", 55), ("red kidney beans boiled", 95), ("pechay", 140), ("tomato", 70), ("onion", 20), ("cooking oil", 6), ("banana", 45)]),
    ("Shrimp Chickpea Pechay Budget Breakfast Plate", "Breakfast", [("cooked white rice", 55), ("shrimp", 55), ("cooked chickpeas", 90), ("pechay", 160), ("mushroom", 90), ("tomato", 55), ("cooking oil", 8), ("papaya", 45)]),
    ("Squid Eggplant Kidney Budget Breakfast Plate", "Breakfast", [("cooked white rice", 55), ("squid", 65), ("red kidney beans boiled", 100), ("eggplant", 150), ("pechay", 100), ("tomato", 55), ("cooking oil", 8), ("banana", 45)]),
    ("Chicken Kidney Sitaw Budget Lunch Plate", "Lunch", [("cooked white rice", 65), ("chicken breast", 60), ("red kidney beans boiled", 105), ("sitaw", 130), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 45)]),
    ("Tuna Chickpea Okra Budget Lunch Plate", "Lunch", [("cooked white rice", 60), ("tuna", 55), ("cooked chickpeas", 95), ("okra", 120), ("malunggay", 40), ("tomato", 55), ("cooking oil", 7), ("banana", 45)]),
    ("Egg Garbanzo Pechay Budget Lunch Plate", "Lunch", [("cooked white rice", 60), ("egg", 70), ("cooked chickpeas", 95), ("pechay", 160), ("mushroom", 90), ("tomato", 55), ("cooking oil", 7), ("papaya", 45)]),
    ("Tofu Kidney Squash Budget Lunch Plate", "Lunch", [("cooked white rice", 55), ("tofu", 125), ("red kidney beans boiled", 95), ("squash", 130), ("pechay", 110), ("tomato", 55), ("cooking oil", 8), ("banana", 45)]),
    ("Shrimp Garbanzo Malunggay Budget Lunch Plate", "Lunch", [("cooked white rice", 55), ("shrimp", 55), ("cooked chickpeas", 95), ("malunggay", 55), ("okra", 110), ("tomato", 55), ("cooking oil", 8), ("papaya", 45)]),
    ("Squid Sitaw Chickpea Budget Lunch Plate", "Lunch", [("cooked white rice", 55), ("squid", 65), ("cooked chickpeas", 95), ("sitaw", 130), ("mushroom", 100), ("tomato", 55), ("cooking oil", 8), ("banana", 45)]),
    ("Chicken Chickpea Kalabasa Budget Dinner Plate", "Dinner", [("cooked white rice", 65), ("chicken breast", 60), ("cooked chickpeas", 95), ("squash", 130), ("okra", 100), ("tomato", 55), ("cooking oil", 8), ("papaya", 45)]),
    ("Sardines Kidney Pechay Budget Dinner Plate", "Dinner", [("cooked white rice", 60), ("sardines", 55), ("red kidney beans boiled", 100), ("pechay", 160), ("mushroom", 90), ("tomato", 55), ("cooking oil", 6), ("banana", 45)]),
    ("Egg Tofu Okra Budget Dinner Plate", "Dinner", [("cooked white rice", 55), ("egg", 60), ("tofu", 100), ("okra", 130), ("pechay", 130), ("tomato", 55), ("cooking oil", 7), ("papaya", 45)]),
    ("Shrimp Kidney Sitaw Budget Dinner Plate", "Dinner", [("cooked white rice", 55), ("shrimp", 55), ("red kidney beans boiled", 100), ("sitaw", 130), ("pechay", 110), ("tomato", 55), ("cooking oil", 8), ("banana", 45)]),
    ("Squid Garbanzo Mushroom Budget Dinner Plate", "Dinner", [("cooked white rice", 55), ("squid", 65), ("cooked chickpeas", 95), ("mushroom", 150), ("pechay", 100), ("tomato", 55), ("cooking oil", 8), ("papaya", 45)]),
    ("Chickpea Kidney Malunggay Budget Dinner Plate", "Dinner", [("cooked white rice", 55), ("cooked chickpeas", 95), ("red kidney beans boiled", 100), ("malunggay", 55), ("pechay", 140), ("tomato", 55), ("cooking oil", 9), ("papaya", 45)]),
]

RND_TEMPLATES = [
    (
        "ph_rnd_001",
        "Tortang Talong Breakfast Plate",
        "Breakfast",
        [
            ("cooked white rice", 100),
            ("egg", 50),
            ("eggplant", 150),
            ("tomato", 85),
            ("onion", 30),
            ("cooking oil", 5),
            ("banana", 120),
            ("peanuts", 25),
        ],
        ["contains_egg", "contains_peanut"],
    ),
    (
        "ph_rnd_002",
        "Grilled Chicken Pinakbet Plate",
        "Lunch",
        [
            ("cooked white rice", 100),
            ("chicken breast", 110),
            ("squash", 120),
            ("okra", 70),
            ("sitaw", 70),
            ("eggplant", 55),
            ("tomato", 70),
            ("onion", 30),
            ("cooking oil", 9),
            ("papaya", 180),
        ],
        ["contains_chicken", "contains_meat"],
    ),
    (
        "ph_rnd_003",
        "Bangus with Monggo-Malunggay Plate",
        "Dinner",
        [
            ("cooked white rice", 100),
            ("milkfish", 95),
            ("cooked munggo", 150),
            ("malunggay", 50),
            ("tomato", 70),
            ("onion", 30),
            ("garlic", 7),
            ("cooking oil", 13),
            ("pineapple", 150),
        ],
        ["contains_fish", "contains_seafood"],
    ),
]

FORMULATED_TEMPLATE_NOTES = (
    "Generated one-person complete plate formulated after the RND consultation pattern. "
    "Nutrition is computed from explicit ingredient gram portions using the same PhilFCT-style "
    "per-100g component references as the budget-support/RND seed plates. Designed to expand "
    "runtime variety without replacing the existing 277-meal baseline."
)


def formulated_templates():
    """Return additive complete-plate templates for variety and restrictive fallback coverage."""
    allergen_safe_breakfast = [
        ("Munggo Malunggay Banana Breakfast Plate", [("cooked white rice", 120), ("cooked munggo", 240), ("malunggay", 60), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 80)]),
        ("Okra Sitaw Munggo Breakfast Plate", [("cooked white rice", 120), ("cooked munggo", 230), ("okra", 90), ("sitaw", 80), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 80)]),
        ("Kalabasa Munggo Papaya Breakfast Plate", [("cooked white rice", 120), ("cooked munggo", 230), ("squash", 160), ("malunggay", 45), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("papaya", 120)]),
        ("Pechay Munggo Pineapple Breakfast Plate", [("cooked white rice", 120), ("cooked munggo", 240), ("pechay", 160), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("pineapple", 120)]),
        ("Eggplant Munggo Banana Breakfast Plate", [("cooked white rice", 120), ("cooked munggo", 250), ("eggplant", 150), ("tomato", 70), ("onion", 25), ("cooking oil", 8), ("banana", 70)]),
        ("Sitaw Kalabasa Munggo Breakfast Plate", [("cooked white rice", 115), ("cooked munggo", 240), ("sitaw", 90), ("squash", 120), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("papaya", 100)]),
        ("Malunggay Okra Munggo Breakfast Plate", [("cooked white rice", 115), ("cooked munggo", 250), ("malunggay", 60), ("okra", 90), ("tomato", 60), ("garlic", 5), ("cooking oil", 8), ("pineapple", 100)]),
        ("Vegetable Munggo Banana Breakfast Plate", [("cooked white rice", 110), ("cooked munggo", 250), ("pechay", 120), ("okra", 70), ("sitaw", 70), ("tomato", 60), ("cooking oil", 8), ("banana", 80)]),
    ]
    allergen_safe_lunch = [
        ("Munggo Pinakbet-Style Lunch Plate", [("cooked white rice", 130), ("cooked munggo", 240), ("squash", 140), ("okra", 80), ("sitaw", 80), ("eggplant", 80), ("tomato", 60), ("onion", 25), ("cooking oil", 10), ("papaya", 100)]),
        ("Malunggay Munggo Vegetable Lunch Plate", [("cooked white rice", 130), ("cooked munggo", 260), ("malunggay", 60), ("pechay", 120), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("banana", 70)]),
        ("Okra Sitaw Munggo Lunch Plate", [("cooked white rice", 130), ("cooked munggo", 260), ("okra", 100), ("sitaw", 90), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("pineapple", 100)]),
        ("Kalabasa Pechay Munggo Lunch Plate", [("cooked white rice", 130), ("cooked munggo", 250), ("squash", 170), ("pechay", 130), ("malunggay", 35), ("onion", 25), ("cooking oil", 10), ("papaya", 120)]),
        ("Eggplant Okra Munggo Lunch Plate", [("cooked white rice", 125), ("cooked munggo", 260), ("eggplant", 140), ("okra", 90), ("tomato", 70), ("onion", 25), ("cooking oil", 10), ("banana", 80)]),
        ("Sitaw Malunggay Munggo Lunch Plate", [("cooked white rice", 125), ("cooked munggo", 260), ("sitaw", 100), ("malunggay", 55), ("tomato", 60), ("garlic", 5), ("cooking oil", 10), ("pineapple", 120)]),
        ("Pechay Okra Munggo Lunch Plate", [("cooked white rice", 125), ("cooked munggo", 260), ("pechay", 150), ("okra", 90), ("tomato", 60), ("onion", 25), ("cooking oil", 10), ("papaya", 120)]),
        ("Kalabasa Sitaw Munggo Lunch Plate", [("cooked white rice", 125), ("cooked munggo", 250), ("squash", 160), ("sitaw", 100), ("malunggay", 40), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("banana", 80)]),
    ]
    allergen_safe_dinner = [
        ("Munggo Malunggay Dinner Plate", [("cooked white rice", 125), ("cooked munggo", 270), ("malunggay", 65), ("tomato", 70), ("onion", 30), ("garlic", 7), ("cooking oil", 10), ("papaya", 120)]),
        ("Okra Kalabasa Munggo Dinner Plate", [("cooked white rice", 125), ("cooked munggo", 260), ("okra", 100), ("squash", 160), ("tomato", 70), ("onion", 30), ("garlic", 7), ("cooking oil", 10), ("pineapple", 120)]),
        ("Sitaw Pechay Munggo Dinner Plate", [("cooked white rice", 125), ("cooked munggo", 260), ("sitaw", 100), ("pechay", 150), ("malunggay", 35), ("tomato", 60), ("cooking oil", 10), ("banana", 80)]),
        ("Eggplant Malunggay Munggo Dinner Plate", [("cooked white rice", 120), ("cooked munggo", 270), ("eggplant", 150), ("malunggay", 55), ("tomato", 70), ("onion", 30), ("cooking oil", 10), ("papaya", 120)]),
        ("Pinakbet-Style Munggo Dinner Plate", [("cooked white rice", 120), ("cooked munggo", 260), ("squash", 140), ("okra", 80), ("sitaw", 80), ("eggplant", 80), ("tomato", 70), ("onion", 30), ("cooking oil", 10), ("pineapple", 100)]),
        ("Pechay Malunggay Munggo Dinner Plate", [("cooked white rice", 120), ("cooked munggo", 270), ("pechay", 160), ("malunggay", 55), ("tomato", 70), ("garlic", 7), ("cooking oil", 10), ("banana", 80)]),
        ("Okra Sitaw Malunggay Munggo Dinner Plate", [("cooked white rice", 120), ("cooked munggo", 260), ("okra", 100), ("sitaw", 100), ("malunggay", 45), ("tomato", 60), ("cooking oil", 10), ("papaya", 120)]),
        ("Kalabasa Eggplant Munggo Dinner Plate", [("cooked white rice", 120), ("cooked munggo", 260), ("squash", 160), ("eggplant", 130), ("tomato", 70), ("onion", 30), ("garlic", 7), ("cooking oil", 10), ("pineapple", 120)]),
    ]
    general_extra = [
        ("Chicken Pechay Munggo Breakfast Plate", "Breakfast", [("cooked white rice", 120), ("chicken breast", 90), ("cooked munggo", 100), ("pechay", 130), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 80)], ["contains_chicken", "contains_meat"]),
        ("Chicken Kalabasa Okra Lunch Plate", "Lunch", [("cooked white rice", 130), ("chicken breast", 110), ("squash", 150), ("okra", 80), ("tomato", 60), ("onion", 25), ("cooking oil", 10), ("papaya", 120)], ["contains_chicken", "contains_meat"]),
        ("Chicken Sitaw Malunggay Dinner Plate", "Dinner", [("cooked white rice", 125), ("chicken breast", 110), ("sitaw", 100), ("malunggay", 45), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("pineapple", 120)], ["contains_chicken", "contains_meat"]),
        ("Bangus Pechay Papaya Breakfast Plate", "Breakfast", [("cooked white rice", 120), ("milkfish", 90), ("pechay", 140), ("okra", 70), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("papaya", 120)], ["contains_fish", "contains_seafood"]),
        ("Bangus Pinakbet Lunch Plate", "Lunch", [("cooked white rice", 130), ("milkfish", 100), ("squash", 140), ("okra", 80), ("sitaw", 80), ("eggplant", 80), ("tomato", 60), ("cooking oil", 10), ("banana", 80)], ["contains_fish", "contains_seafood"]),
        ("Tuna Malunggay Dinner Plate", "Dinner", [("cooked white rice", 125), ("tuna", 100), ("malunggay", 55), ("pechay", 120), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("pineapple", 120)], ["contains_fish", "contains_seafood"]),
        ("Egg Pechay Banana Breakfast Plate", "Breakfast", [("cooked white rice", 120), ("egg", 100), ("pechay", 150), ("tomato", 70), ("onion", 25), ("cooking oil", 8), ("banana", 90)], ["contains_egg"]),
        ("Eggplant Egg Malunggay Lunch Plate", "Lunch", [("cooked white rice", 125), ("egg", 100), ("eggplant", 150), ("malunggay", 50), ("tomato", 70), ("onion", 25), ("cooking oil", 8), ("papaya", 120)], ["contains_egg"]),
        ("Tofu Pechay Banana Breakfast Plate", "Breakfast", [("cooked white rice", 120), ("tofu", 150), ("pechay", 150), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 90)], ["contains_soy"]),
        ("Tofu Sitaw Kalabasa Lunch Plate", "Lunch", [("cooked white rice", 125), ("tofu", 170), ("sitaw", 100), ("squash", 150), ("tomato", 60), ("onion", 25), ("cooking oil", 10), ("papaya", 120)], ["contains_soy"]),
        ("Tofu Malunggay Munggo Dinner Plate", "Dinner", [("cooked white rice", 120), ("tofu", 150), ("cooked munggo", 130), ("malunggay", 45), ("tomato", 60), ("onion", 25), ("cooking oil", 10), ("pineapple", 120)], ["contains_soy"]),
        ("Sardines Okra Pechay Lunch Plate", "Lunch", [("cooked white rice", 125), ("sardines", 90), ("okra", 80), ("pechay", 130), ("tomato", 70), ("onion", 25), ("cooking oil", 6), ("banana", 80)], ["contains_fish", "contains_seafood"]),
    ]
    no_pork_no_dairy_no_egg_no_fish = [
        ("Chicken Pechay Garbanzo Breakfast Plate", "Breakfast", [("cooked white rice", 70), ("chicken breast", 90), ("cooked chickpeas", 100), ("pechay", 120), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("papaya", 100)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Okra Kidney Breakfast Plate", "Breakfast", [("cooked white rice", 80), ("chicken breast", 75), ("red kidney beans boiled", 110), ("okra", 100), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 70)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Kalabasa Chickpea Breakfast Plate", "Breakfast", [("cooked white rice", 70), ("chicken breast", 80), ("cooked chickpeas", 110), ("squash", 140), ("malunggay", 30), ("cooking oil", 8), ("papaya", 80)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Sitaw Chickpea Breakfast Plate", "Breakfast", [("cooked white rice", 60), ("chicken breast", 70), ("cooked chickpeas", 110), ("sitaw", 100), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("banana", 60)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Eggplant Kidney Breakfast Plate", "Breakfast", [("cooked white rice", 80), ("chicken breast", 75), ("red kidney beans boiled", 110), ("eggplant", 140), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("papaya", 100)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Mushroom Chickpea Breakfast Plate", "Breakfast", [("cooked white rice", 70), ("chicken breast", 70), ("cooked chickpeas", 105), ("mushroom", 120), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 80)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Garbanzo Pinakbet Lunch Plate", "Lunch", [("cooked white rice", 60), ("chicken breast", 80), ("cooked chickpeas", 100), ("squash", 130), ("okra", 90), ("sitaw", 80), ("tomato", 60), ("cooking oil", 8), ("papaya", 80)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Kidney Pechay Lunch Plate", "Lunch", [("cooked white rice", 80), ("chicken breast", 70), ("red kidney beans boiled", 120), ("pechay", 140), ("tomato", 70), ("onion", 25), ("cooking oil", 8), ("banana", 60)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Mushroom Okra Lunch Plate", "Lunch", [("cooked white rice", 90), ("chicken breast", 75), ("cooked chickpeas", 85), ("mushroom", 120), ("okra", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 80)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Sitaw Kidney Lunch Plate", "Lunch", [("cooked white rice", 80), ("chicken breast", 55), ("red kidney beans boiled", 110), ("sitaw", 100), ("malunggay", 30), ("tomato", 60), ("cooking oil", 8), ("pineapple", 80)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Chickpea Malunggay Dinner Plate", "Dinner", [("cooked white rice", 75), ("chicken breast", 70), ("cooked chickpeas", 105), ("malunggay", 45), ("pechay", 120), ("tomato", 60), ("cooking oil", 8), ("papaya", 80)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Kidney Kalabasa Dinner Plate", "Dinner", [("cooked white rice", 80), ("chicken breast", 70), ("red kidney beans boiled", 120), ("squash", 140), ("okra", 80), ("tomato", 60), ("cooking oil", 8), ("banana", 60)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Mushroom Pechay Dinner Plate", "Dinner", [("cooked white rice", 90), ("chicken breast", 70), ("cooked chickpeas", 85), ("mushroom", 120), ("pechay", 130), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("papaya", 80)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
        ("Chicken Garbanzo Sitaw Dinner Plate", "Dinner", [("cooked white rice", 50), ("chicken breast", 80), ("cooked chickpeas", 100), ("sitaw", 100), ("squash", 110), ("tomato", 60), ("cooking oil", 8), ("pineapple", 80)], ["contains_chicken", "contains_meat", "strict_profile_non_munggo"]),
    ]
    vegetarian_non_soy = [
        ("Garbanzo Kidney Malunggay Breakfast Plate", "Breakfast", [("cooked chickpeas", 110), ("red kidney beans boiled", 120), ("malunggay", 40), ("tomato", 70), ("onion", 25), ("cooking oil", 9), ("papaya", 80)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Mushroom Pechay Breakfast Plate", "Breakfast", [("red kidney beans boiled", 180), ("cooked chickpeas", 70), ("mushroom", 100), ("pechay", 100), ("tomato", 60), ("cooking oil", 10), ("papaya", 60)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Okra Squash Breakfast Plate", "Breakfast", [("cooked chickpeas", 90), ("red kidney beans boiled", 100), ("okra", 100), ("squash", 120), ("malunggay", 45), ("tomato", 50), ("cooking oil", 9), ("papaya", 50)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Garbanzo Sitaw Breakfast Plate", "Breakfast", [("red kidney beans boiled", 75), ("cooked chickpeas", 65), ("sitaw", 150), ("pechay", 160), ("tomato", 60), ("cooking oil", 21), ("malunggay", 45), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Mushroom Kalabasa Breakfast Plate", "Breakfast", [("cooked chickpeas", 65), ("red kidney beans boiled", 65), ("mushroom", 230), ("squash", 35), ("malunggay", 65), ("tomato", 45), ("cooking oil", 20.5), ("papaya", 15)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Okra Pechay Breakfast Plate", "Breakfast", [("red kidney beans boiled", 80), ("cooked chickpeas", 60), ("okra", 150), ("pechay", 180), ("tomato", 60), ("onion", 15), ("cooking oil", 21), ("pineapple", 20), ("malunggay", 25)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Malunggay Mushroom Breakfast Plate", "Breakfast", [("cooked chickpeas", 65), ("red kidney beans boiled", 65), ("malunggay", 70), ("mushroom", 200), ("tomato", 50), ("cooking oil", 21), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Kidney Pinakbet Lunch Plate", "Lunch", [("cooked chickpeas", 75), ("red kidney beans boiled", 120), ("squash", 130), ("okra", 90), ("sitaw", 70), ("tomato", 60), ("cooking oil", 9), ("papaya", 60)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Malunggay Pechay Lunch Plate", "Lunch", [("red kidney beans boiled", 140), ("cooked chickpeas", 80), ("malunggay", 45), ("pechay", 130), ("tomato", 60), ("onion", 25), ("cooking oil", 10), ("pineapple", 60)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Mushroom Sitaw Lunch Plate", "Lunch", [("cooked chickpeas", 100), ("red kidney beans boiled", 100), ("mushroom", 120), ("sitaw", 100), ("tomato", 60), ("onion", 25), ("cooking oil", 9), ("papaya", 60)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Squash Sitaw Lunch Plate", "Lunch", [("red kidney beans boiled", 70), ("cooked chickpeas", 60), ("squash", 55), ("sitaw", 165), ("tomato", 45), ("onion", 10), ("cooking oil", 21), ("papaya", 15), ("malunggay", 50)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Pechay Okra Lunch Plate", "Lunch", [("cooked chickpeas", 65), ("red kidney beans boiled", 60), ("pechay", 200), ("okra", 160), ("malunggay", 55), ("tomato", 50), ("cooking oil", 21), ("pineapple", 20)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Mushroom Kalabasa Lunch Plate", "Lunch", [("red kidney beans boiled", 75), ("cooked chickpeas", 55), ("mushroom", 230), ("squash", 45), ("pechay", 170), ("tomato", 45), ("cooking oil", 21), ("papaya", 15)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Sitaw Eggplant Lunch Plate", "Lunch", [("cooked chickpeas", 60), ("red kidney beans boiled", 60), ("sitaw", 170), ("eggplant", 180), ("tomato", 60), ("onion", 15), ("cooking oil", 21), ("pineapple", 20), ("malunggay", 25)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Garbanzo Kalabasa Dinner Plate", "Dinner", [("red kidney beans boiled", 170), ("cooked chickpeas", 55), ("squash", 130), ("okra", 90), ("tomato", 60), ("cooking oil", 10), ("papaya", 60)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Pechay Mushroom Dinner Plate", "Dinner", [("cooked chickpeas", 95), ("red kidney beans boiled", 100), ("pechay", 150), ("mushroom", 120), ("tomato", 70), ("onion", 25), ("cooking oil", 9), ("pineapple", 60)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Sitaw Malunggay Dinner Plate", "Dinner", [("red kidney beans boiled", 145), ("cooked chickpeas", 90), ("sitaw", 100), ("malunggay", 45), ("tomato", 60), ("cooking oil", 10), ("papaya", 50)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Pechay Okra Dinner Plate", "Dinner", [("red kidney beans boiled", 80), ("cooked chickpeas", 55), ("pechay", 200), ("okra", 150), ("tomato", 60), ("onion", 15), ("cooking oil", 21), ("papaya", 20), ("malunggay", 20)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Kalabasa Malunggay Dinner Plate", "Dinner", [("cooked chickpeas", 65), ("red kidney beans boiled", 60), ("squash", 70), ("malunggay", 75), ("tomato", 50), ("cooking oil", 21), ("pineapple", 20), ("mushroom", 120)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Kidney Mushroom Sitaw Dinner Plate", "Dinner", [("red kidney beans boiled", 70), ("cooked chickpeas", 60), ("mushroom", 230), ("sitaw", 155), ("tomato", 45), ("cooking oil", 21), ("papaya", 15)], ["vegetarian_fallback", "soy_free_vegetarian"]),
        ("Garbanzo Eggplant Pechay Dinner Plate", "Dinner", [("cooked chickpeas", 60), ("red kidney beans boiled", 60), ("eggplant", 190), ("pechay", 180), ("malunggay", 50), ("tomato", 50), ("cooking oil", 21), ("pineapple", 20)], ["vegetarian_fallback", "soy_free_vegetarian"]),
    ]
    shellfish_non_fish = [
        ("Shrimp Pechay Garbanzo Breakfast Plate", "Breakfast", [("cooked white rice", 75), ("shrimp", 100), ("cooked chickpeas", 95), ("pechay", 120), ("tomato", 60), ("onion", 25), ("cooking oil", 9), ("papaya", 80)], ["strict_profile_non_munggo"]),
        ("Squid Okra Kidney Lunch Plate", "Lunch", [("cooked white rice", 80), ("squid", 80), ("red kidney beans boiled", 115), ("okra", 100), ("sitaw", 80), ("tomato", 60), ("cooking oil", 9), ("banana", 60)], ["strict_profile_non_munggo"]),
        ("Shrimp Kalabasa Chickpea Dinner Plate", "Dinner", [("cooked white rice", 75), ("shrimp", 95), ("cooked chickpeas", 100), ("squash", 130), ("pechay", 120), ("tomato", 60), ("cooking oil", 9), ("papaya", 80)], ["strict_profile_non_munggo"]),
    ]
    fish_allergy_no_red_meat_support = [
        ("Egg Garbanzo Pechay Breakfast Plate", "Breakfast", [("cooked white rice", 65), ("egg", 75), ("cooked chickpeas", 95), ("pechay", 130), ("tomato", 60), ("onion", 25), ("cooking oil", 6), ("papaya", 80)], ["no_red_meat_fish_allergy_support"]),
        ("Egg Kidney Mushroom Breakfast Plate", "Breakfast", [("cooked white rice", 70), ("egg", 75), ("red kidney beans boiled", 115), ("mushroom", 100), ("pechay", 100), ("tomato", 60), ("cooking oil", 6), ("banana", 60)], ["no_red_meat_fish_allergy_support"]),
        ("Tofu Garbanzo Tomato Breakfast Plate", "Breakfast", [("cooked white rice", 70), ("tofu", 140), ("cooked chickpeas", 100), ("pechay", 120), ("tomato", 70), ("onion", 25), ("cooking oil", 5), ("papaya", 80)], ["no_red_meat_fish_allergy_support"]),
        ("Tofu Egg Garbanzo Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("tofu", 120), ("egg", 55), ("cooked chickpeas", 70), ("pechay", 140), ("mushroom", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Kidney Egg Pechay Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("egg", 75), ("red kidney beans boiled", 100), ("pechay", 150), ("mushroom", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Shrimp Egg Pechay Breakfast Plate", "Breakfast", [("cooked white rice", 75), ("shrimp", 75), ("egg", 50), ("cooked chickpeas", 40), ("pechay", 140), ("tomato", 70), ("onion", 25), ("cooking oil", 7), ("banana", 70)], ["no_red_meat_fish_allergy_support"]),
        ("Tofu Kidney Pinakbet Lunch Plate", "Lunch", [("cooked white rice", 70), ("tofu", 150), ("red kidney beans boiled", 100), ("squash", 130), ("okra", 90), ("sitaw", 80), ("tomato", 60), ("cooking oil", 7), ("papaya", 70)], ["no_red_meat_fish_allergy_support"]),
        ("Egg Garbanzo Sitaw Lunch Plate", "Lunch", [("cooked white rice", 65), ("egg", 75), ("cooked chickpeas", 95), ("sitaw", 100), ("pechay", 120), ("tomato", 60), ("cooking oil", 6), ("pineapple", 70)], ["no_red_meat_fish_allergy_support"]),
        ("Tofu Garbanzo Mushroom Lunch Plate", "Lunch", [("cooked white rice", 45), ("tofu", 150), ("cooked chickpeas", 80), ("mushroom", 130), ("sitaw", 90), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Egg Kidney Sitaw Lunch Plate", "Lunch", [("cooked white rice", 45), ("egg", 75), ("red kidney beans boiled", 100), ("sitaw", 110), ("squash", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Shrimp Okra Garbanzo Lunch Plate", "Lunch", [("cooked white rice", 75), ("shrimp", 95), ("cooked chickpeas", 95), ("okra", 100), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 80)], ["no_red_meat_fish_allergy_support"]),
        ("Squid Pechay Kidney Lunch Plate", "Lunch", [("cooked white rice", 80), ("squid", 85), ("red kidney beans boiled", 110), ("pechay", 140), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 60)], ["no_red_meat_fish_allergy_support"]),
        ("Egg Kidney Pechay Lunch Plate", "Lunch", [("cooked white rice", 45), ("egg", 75), ("red kidney beans boiled", 100), ("pechay", 150), ("mushroom", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Tofu Eggplant Garbanzo Lunch Plate", "Lunch", [("cooked white rice", 45), ("tofu", 150), ("cooked chickpeas", 80), ("eggplant", 150), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Tofu Chickpea Malunggay Dinner Plate", "Dinner", [("cooked white rice", 65), ("tofu", 150), ("cooked chickpeas", 100), ("malunggay", 40), ("pechay", 120), ("tomato", 60), ("cooking oil", 7), ("papaya", 80)], ["no_red_meat_fish_allergy_support"]),
        ("Egg Kidney Kalabasa Dinner Plate", "Dinner", [("cooked white rice", 70), ("egg", 75), ("red kidney beans boiled", 115), ("squash", 130), ("okra", 90), ("tomato", 60), ("cooking oil", 7), ("pineapple", 70)], ["no_red_meat_fish_allergy_support"]),
        ("Tofu Eggplant Chickpea Dinner Plate", "Dinner", [("cooked white rice", 45), ("tofu", 150), ("cooked chickpeas", 80), ("eggplant", 150), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Egg Pechay Kidney Dinner Plate", "Dinner", [("cooked white rice", 45), ("egg", 75), ("red kidney beans boiled", 100), ("pechay", 150), ("mushroom", 100), ("tomato", 60), ("cooking oil", 8), ("pineapple", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Shrimp Mushroom Pechay Dinner Plate", "Dinner", [("cooked white rice", 80), ("shrimp", 90), ("cooked chickpeas", 30), ("mushroom", 120), ("pechay", 140), ("tomato", 70), ("onion", 25), ("cooking oil", 9), ("banana", 70)], ["no_red_meat_fish_allergy_support"]),
        ("Squid Garbanzo Sitaw Dinner Plate", "Dinner", [("cooked white rice", 70), ("squid", 85), ("cooked chickpeas", 100), ("sitaw", 100), ("squash", 120), ("tomato", 60), ("cooking oil", 8), ("papaya", 80)], ["no_red_meat_fish_allergy_support"]),
        ("Egg Tofu Pechay Dinner Plate", "Dinner", [("cooked white rice", 45), ("egg", 70), ("tofu", 120), ("cooked chickpeas", 60), ("pechay", 150), ("mushroom", 100), ("tomato", 60), ("cooking oil", 8), ("pineapple", 50)], ["no_red_meat_fish_allergy_support"]),
        ("Tofu Kidney Mushroom Dinner Plate", "Dinner", [("cooked white rice", 45), ("tofu", 150), ("red kidney beans boiled", 100), ("mushroom", 130), ("pechay", 130), ("tomato", 70), ("cooking oil", 9), ("papaya", 50)], ["no_red_meat_fish_allergy_support"]),
    ]
    no_beef_lactose_shellfish_support = [
        ("Egg Garbanzo Pechay Rice Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("egg", 75), ("cooked chickpeas", 90), ("pechay", 140), ("mushroom", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 60)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Kidney Okra Rice Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("egg", 75), ("red kidney beans boiled", 100), ("okra", 130), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("pineapple", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Garbanzo Kidney Malunggay Rice Breakfast Plate", "Breakfast", [("cooked white rice", 35), ("cooked chickpeas", 70), ("red kidney beans boiled", 90), ("malunggay", 65), ("mushroom", 160), ("tomato", 60), ("cooking oil", 19), ("papaya", 30)], ["no_beef_lactose_shellfish_support"]),
        ("Tofu Garbanzo Mushroom Rice Breakfast Plate", "Breakfast", [("cooked white rice", 40), ("tofu", 130), ("cooked chickpeas", 80), ("mushroom", 140), ("pechay", 120), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Tofu Pechay Rice Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("egg", 65), ("tofu", 100), ("red kidney beans boiled", 70), ("pechay", 160), ("mushroom", 100), ("cooking oil", 8), ("pineapple", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Kidney Sitaw Malunggay Rice Breakfast Plate", "Breakfast", [("cooked white rice", 35), ("red kidney beans boiled", 130), ("cooked chickpeas", 60), ("sitaw", 140), ("malunggay", 50), ("tomato", 60), ("cooking oil", 17), ("papaya", 20)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Mushroom Squash Rice Breakfast Plate", "Breakfast", [("cooked white rice", 50), ("egg", 80), ("cooked chickpeas", 70), ("mushroom", 160), ("squash", 120), ("pechay", 100), ("cooking oil", 8), ("papaya", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Garbanzo Pechay Okra Rice Breakfast Plate", "Breakfast", [("cooked white rice", 35), ("cooked chickpeas", 100), ("red kidney beans boiled", 80), ("pechay", 180), ("okra", 120), ("malunggay", 40), ("cooking oil", 18), ("pineapple", 20)], ["no_beef_lactose_shellfish_support"]),
        ("Tofu Kidney Sitaw Rice Breakfast Plate", "Breakfast", [("cooked white rice", 40), ("tofu", 130), ("red kidney beans boiled", 100), ("sitaw", 120), ("mushroom", 100), ("tomato", 60), ("cooking oil", 9), ("papaya", 40)], ["no_beef_lactose_shellfish_support"]),
        ("Eggplant Egg Kidney Rice Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("egg", 75), ("red kidney beans boiled", 100), ("eggplant", 160), ("pechay", 100), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Tofu Garbanzo Kidney Pinakbet Rice Lunch Plate", "Lunch", [("cooked white rice", 20), ("tofu", 50), ("cooked chickpeas", 80), ("red kidney beans boiled", 90), ("squash", 120), ("okra", 90), ("sitaw", 80), ("tomato", 60), ("cooking oil", 15), ("papaya", 30)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Tofu Sitaw Rice Lunch Plate", "Lunch", [("cooked white rice", 60), ("egg", 70), ("tofu", 120), ("sitaw", 130), ("pechay", 140), ("squash", 50), ("tomato", 60), ("cooking oil", 8), ("pineapple", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Kidney Mushroom Pechay Rice Lunch Plate", "Lunch", [("cooked white rice", 35), ("red kidney beans boiled", 130), ("cooked chickpeas", 55), ("mushroom", 180), ("pechay", 160), ("tomato", 70), ("cooking oil", 15), ("papaya", 30)], ["no_beef_lactose_shellfish_support"]),
        ("Garbanzo Eggplant Malunggay Rice Lunch Plate", "Lunch", [("cooked white rice", 40), ("cooked chickpeas", 90), ("red kidney beans boiled", 70), ("eggplant", 180), ("malunggay", 55), ("tomato", 70), ("cooking oil", 16), ("pineapple", 30)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Kidney Kalabasa Rice Lunch Plate", "Lunch", [("cooked white rice", 45), ("egg", 75), ("red kidney beans boiled", 110), ("squash", 160), ("okra", 90), ("pechay", 100), ("cooking oil", 8), ("papaya", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Tofu Garbanzo Okra Rice Lunch Plate", "Lunch", [("cooked white rice", 45), ("tofu", 140), ("cooked chickpeas", 90), ("okra", 120), ("pechay", 130), ("tomato", 60), ("cooking oil", 8), ("pineapple", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Mushroom Pinakbet Rice Lunch Plate", "Lunch", [("cooked white rice", 50), ("egg", 80), ("cooked chickpeas", 70), ("mushroom", 140), ("squash", 120), ("sitaw", 100), ("tomato", 60), ("cooking oil", 9), ("papaya", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Garbanzo Sitaw Pechay Rice Lunch Plate", "Lunch", [("cooked white rice", 30), ("cooked chickpeas", 90), ("red kidney beans boiled", 75), ("sitaw", 140), ("pechay", 160), ("malunggay", 40), ("cooking oil", 17), ("papaya", 20)], ["no_beef_lactose_shellfish_support"]),
        ("Tofu Kidney Eggplant Rice Lunch Plate", "Lunch", [("cooked white rice", 40), ("tofu", 140), ("red kidney beans boiled", 95), ("eggplant", 160), ("mushroom", 120), ("tomato", 60), ("cooking oil", 9), ("pineapple", 40)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Garbanzo Malunggay Rice Lunch Plate", "Lunch", [("cooked white rice", 45), ("egg", 75), ("cooked chickpeas", 90), ("malunggay", 60), ("pechay", 140), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Kidney Garbanzo Pechay Rice Dinner Plate", "Dinner", [("cooked white rice", 35), ("red kidney beans boiled", 125), ("cooked chickpeas", 75), ("pechay", 180), ("mushroom", 120), ("tomato", 60), ("cooking oil", 16), ("papaya", 30)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Tofu Mushroom Rice Dinner Plate", "Dinner", [("cooked white rice", 50), ("egg", 65), ("tofu", 115), ("cooked chickpeas", 30), ("mushroom", 170), ("pechay", 130), ("tomato", 60), ("cooking oil", 8), ("pineapple", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Garbanzo Kalabasa Okra Rice Dinner Plate", "Dinner", [("cooked white rice", 20), ("cooked chickpeas", 90), ("red kidney beans boiled", 75), ("squash", 150), ("okra", 110), ("malunggay", 45), ("cooking oil", 18), ("papaya", 20)], ["no_beef_lactose_shellfish_support"]),
        ("Kidney Egg Pechay Support Dinner Plate", "Dinner", [("cooked white rice", 45), ("egg", 75), ("red kidney beans boiled", 105), ("pechay", 170), ("mushroom", 110), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Tofu Chickpea Sitaw Rice Dinner Plate", "Dinner", [("cooked white rice", 45), ("tofu", 140), ("cooked chickpeas", 85), ("sitaw", 130), ("pechay", 130), ("tomato", 60), ("cooking oil", 8), ("pineapple", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Garbanzo Mushroom Malunggay Rice Dinner Plate", "Dinner", [("cooked white rice", 35), ("cooked chickpeas", 90), ("red kidney beans boiled", 70), ("mushroom", 180), ("malunggay", 60), ("pechay", 100), ("cooking oil", 17), ("papaya", 20)], ["no_beef_lactose_shellfish_support"]),
        ("Eggplant Kidney Garbanzo Rice Dinner Plate", "Dinner", [("cooked white rice", 40), ("red kidney beans boiled", 110), ("cooked chickpeas", 80), ("eggplant", 180), ("pechay", 120), ("tomato", 60), ("cooking oil", 16), ("pineapple", 30)], ["no_beef_lactose_shellfish_support"]),
        ("Egg Pechay Okra Rice Dinner Plate", "Dinner", [("cooked white rice", 50), ("egg", 80), ("red kidney beans boiled", 90), ("pechay", 160), ("okra", 120), ("tomato", 60), ("cooking oil", 8), ("papaya", 50)], ["no_beef_lactose_shellfish_support"]),
        ("Tofu Kalabasa Kidney Rice Dinner Plate", "Dinner", [("cooked white rice", 40), ("tofu", 140), ("red kidney beans boiled", 95), ("squash", 170), ("mushroom", 120), ("tomato", 60), ("cooking oil", 9), ("papaya", 40)], ["no_beef_lactose_shellfish_support"]),
        ("Garbanzo Sitaw Mushroom Rice Dinner Plate", "Dinner", [("cooked white rice", 25), ("cooked chickpeas", 90), ("red kidney beans boiled", 75), ("sitaw", 140), ("mushroom", 160), ("pechay", 100), ("cooking oil", 17), ("pineapple", 20)], ["no_beef_lactose_shellfish_support"]),
    ]
    pescatarian_variety_support = [
        ("Sardines Egg Pechay Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("sardines", 55), ("egg", 35), ("cooked chickpeas", 80), ("pechay", 140), ("tomato", 70), ("onion", 25), ("cooking oil", 7), ("papaya", 70)], ["pescatarian_variety_support"]),
        ("Tuna Eggplant Okra Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("tuna", 65), ("red kidney beans boiled", 70), ("eggplant", 130), ("okra", 90), ("tomato", 60), ("cooking oil", 8), ("papaya", 60)], ["pescatarian_variety_support"]),
        ("Bangus Garbanzo Pechay Breakfast Plate", "Breakfast", [("cooked white rice", 40), ("milkfish", 80), ("cooked chickpeas", 70), ("pechay", 140), ("tomato", 70), ("onion", 25), ("cooking oil", 8), ("papaya", 60)], ["pescatarian_variety_support"]),
        ("Shrimp Egg Mushroom Breakfast Plate", "Breakfast", [("cooked white rice", 45), ("shrimp", 80), ("egg", 35), ("cooked chickpeas", 40), ("mushroom", 120), ("pechay", 120), ("tomato", 70), ("cooking oil", 8), ("banana", 50)], ["pescatarian_variety_support"]),
        ("Tuna Garbanzo Pinakbet Lunch Plate", "Lunch", [("cooked white rice", 40), ("tuna", 65), ("cooked chickpeas", 75), ("squash", 130), ("okra", 90), ("sitaw", 80), ("tomato", 60), ("cooking oil", 9), ("papaya", 60)], ["pescatarian_variety_support"]),
        ("Sardines Eggplant Pechay Lunch Plate", "Lunch", [("cooked white rice", 45), ("sardines", 80), ("red kidney beans boiled", 70), ("eggplant", 130), ("pechay", 120), ("tomato", 70), ("cooking oil", 8), ("pineapple", 60)], ["pescatarian_variety_support"]),
        ("Shrimp Kidney Okra Lunch Plate", "Lunch", [("cooked white rice", 45), ("shrimp", 100), ("red kidney beans boiled", 80), ("okra", 100), ("pechay", 120), ("tomato", 60), ("cooking oil", 9), ("papaya", 60)], ["pescatarian_variety_support"]),
        ("Squid Egg Sitaw Lunch Plate", "Lunch", [("cooked white rice", 45), ("squid", 85), ("egg", 55), ("cooked chickpeas", 35), ("sitaw", 110), ("squash", 110), ("tomato", 60), ("cooking oil", 8), ("papaya", 60)], ["pescatarian_variety_support"]),
        ("Bangus Okra Malunggay Dinner Plate", "Dinner", [("cooked white rice", 40), ("milkfish", 85), ("cooked chickpeas", 70), ("okra", 100), ("malunggay", 40), ("tomato", 60), ("cooking oil", 9), ("papaya", 60)], ["pescatarian_variety_support"]),
        ("Tuna Pechay Chickpea Dinner Plate", "Dinner", [("cooked white rice", 40), ("tuna", 60), ("cooked chickpeas", 80), ("pechay", 140), ("mushroom", 100), ("tomato", 70), ("cooking oil", 9), ("pineapple", 60)], ["pescatarian_variety_support"]),
        ("Shrimp Eggplant Kidney Dinner Plate", "Dinner", [("cooked white rice", 45), ("shrimp", 90), ("red kidney beans boiled", 80), ("eggplant", 130), ("pechay", 120), ("tomato", 60), ("cooking oil", 9), ("papaya", 60)], ["pescatarian_variety_support"]),
        ("Squid Mushroom Garbanzo Dinner Plate", "Dinner", [("cooked white rice", 45), ("squid", 80), ("cooked chickpeas", 80), ("mushroom", 130), ("sitaw", 90), ("tomato", 60), ("cooking oil", 9), ("papaya", 60)], ["pescatarian_variety_support"]),
    ]
    vegetarian_egg_soy_support = [
        ("Garbanzo Kidney Pechay Vegan Breakfast Plate", "Breakfast", [("cooked white rice", 40), ("cooked chickpeas", 85), ("red kidney beans boiled", 90), ("pechay", 170), ("mushroom", 120), ("tomato", 60), ("cooking oil", 15), ("papaya", 25)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Malunggay Mushroom Vegan Breakfast Plate", "Breakfast", [("cooked white rice", 35), ("red kidney beans boiled", 105), ("cooked chickpeas", 70), ("malunggay", 65), ("mushroom", 140), ("tomato", 55), ("cooking oil", 15), ("pineapple", 25)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Chickpea Okra Sitaw Vegan Breakfast Plate", "Breakfast", [("cooked white rice", 35), ("cooked chickpeas", 90), ("red kidney beans boiled", 70), ("okra", 130), ("sitaw", 120), ("malunggay", 35), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Mushroom Eggplant Vegan Breakfast Plate", "Breakfast", [("cooked white rice", 35), ("red kidney beans boiled", 105), ("cooked chickpeas", 55), ("mushroom", 180), ("eggplant", 160), ("pechay", 100), ("cooking oil", 14), ("pineapple", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Garbanzo Kalabasa Pechay Vegan Breakfast Plate", "Breakfast", [("cooked white rice", 35), ("cooked chickpeas", 80), ("red kidney beans boiled", 70), ("squash", 100), ("pechay", 160), ("mushroom", 100), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Sitaw Okra Vegan Breakfast Plate", "Breakfast", [("cooked white rice", 35), ("red kidney beans boiled", 100), ("cooked chickpeas", 65), ("sitaw", 140), ("okra", 120), ("tomato", 55), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Garbanzo Kidney Pinakbet Vegan Lunch Plate", "Lunch", [("cooked white rice", 35), ("cooked chickpeas", 80), ("red kidney beans boiled", 85), ("squash", 120), ("okra", 110), ("sitaw", 100), ("tomato", 60), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Pechay Mushroom Vegan Lunch Plate", "Lunch", [("cooked white rice", 35), ("red kidney beans boiled", 105), ("cooked chickpeas", 65), ("pechay", 180), ("mushroom", 150), ("tomato", 60), ("cooking oil", 15), ("pineapple", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Chickpea Sitaw Malunggay Vegan Lunch Plate", "Lunch", [("cooked white rice", 35), ("cooked chickpeas", 90), ("red kidney beans boiled", 70), ("sitaw", 140), ("malunggay", 55), ("tomato", 60), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Eggplant Okra Vegan Lunch Plate", "Lunch", [("cooked white rice", 35), ("red kidney beans boiled", 105), ("cooked chickpeas", 60), ("eggplant", 180), ("okra", 130), ("pechay", 100), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Garbanzo Mushroom Kalabasa Vegan Lunch Plate", "Lunch", [("cooked white rice", 35), ("cooked chickpeas", 85), ("red kidney beans boiled", 70), ("mushroom", 180), ("squash", 110), ("pechay", 120), ("cooking oil", 15), ("pineapple", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Pechay Sitaw Vegan Lunch Plate", "Lunch", [("cooked white rice", 35), ("red kidney beans boiled", 100), ("cooked chickpeas", 65), ("pechay", 170), ("sitaw", 130), ("tomato", 55), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Garbanzo Malunggay Vegan Dinner Plate", "Dinner", [("cooked white rice", 35), ("red kidney beans boiled", 105), ("cooked chickpeas", 70), ("malunggay", 60), ("pechay", 150), ("tomato", 60), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Garbanzo Okra Mushroom Vegan Dinner Plate", "Dinner", [("cooked white rice", 35), ("cooked chickpeas", 90), ("red kidney beans boiled", 70), ("okra", 130), ("mushroom", 170), ("pechay", 110), ("cooking oil", 15), ("pineapple", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Sitaw Kalabasa Vegan Dinner Plate", "Dinner", [("cooked white rice", 35), ("red kidney beans boiled", 105), ("cooked chickpeas", 65), ("sitaw", 130), ("squash", 130), ("tomato", 60), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Chickpea Pechay Eggplant Vegan Dinner Plate", "Dinner", [("cooked white rice", 35), ("cooked chickpeas", 85), ("red kidney beans boiled", 70), ("pechay", 170), ("eggplant", 180), ("tomato", 60), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Kidney Mushroom Okra Vegan Dinner Plate", "Dinner", [("cooked white rice", 35), ("red kidney beans boiled", 100), ("cooked chickpeas", 65), ("mushroom", 180), ("okra", 130), ("tomato", 60), ("cooking oil", 15), ("pineapple", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
        ("Garbanzo Sitaw Pechay Vegan Dinner Plate", "Dinner", [("cooked white rice", 35), ("cooked chickpeas", 90), ("red kidney beans boiled", 70), ("sitaw", 140), ("pechay", 150), ("tomato", 60), ("cooking oil", 15), ("papaya", 20)], ["vegetarian_fallback", "soy_free_vegetarian", "egg_soy_free_vegetarian_support"]),
    ]
    result = []
    for meal_type, group in [
        ("Breakfast", allergen_safe_breakfast),
        ("Lunch", allergen_safe_lunch),
        ("Dinner", allergen_safe_dinner),
    ]:
        for name, ingredients in group:
            result.append((name, meal_type, ingredients, ["allergy_safe_fallback", "vegetarian_fallback"]))
    result.extend(general_extra)
    result.extend(no_pork_no_dairy_no_egg_no_fish)
    result.extend(vegetarian_non_soy)
    result.extend(shellfish_non_fish)
    result.extend(fish_allergy_no_red_meat_support)
    result.extend(no_beef_lactose_shellfish_support)
    result.extend(pescatarian_variety_support)
    result.extend(vegetarian_egg_soy_support)
    return result


def formulated_tags(ingredients, extra_tags):
    tags = [
        "complete_plate",
        "formulated_complete_plate",
        "includes_fruit",
        "includes_glow_vegetables",
        "includes_water",
        "philfct_portioned_runtime_candidate",
    ] + list(extra_tags or [])
    if any(n in {"sardines", "tuna", "milkfish"} for n, _ in ingredients):
        tags += ["contains_fish", "contains_seafood"]
    if any(n in {"shrimp", "squid"} for n, _ in ingredients):
        tags += ["contains_shellfish", "contains_seafood"]
    if any(n == "egg" for n, _ in ingredients):
        tags.append("contains_egg")
    if any(n == "tofu" for n, _ in ingredients):
        tags.append("contains_soy")
    if any(n == "chicken breast" for n, _ in ingredients):
        tags += ["contains_chicken", "contains_meat"]
    if any(n in {"cooked chickpeas", "red kidney beans boiled"} for n, _ in ingredients):
        tags.append("contains_legume")
    return sorted(set(tags))


def make_formulated_recipe(index: int, name: str, meal_type: str, ingredients, extra_tags):
    ingredients = rebalance_formulated_support_ingredients(ingredients, extra_tags)
    return {
        "id": f"ph_form_{index:03d}",
        "name": name,
        "title": name,
        "mealType": meal_type,
        "tags": formulated_tags(ingredients, extra_tags),
        "nutrition": nutrient(ingredients),
        "ingredients": [
            {
                "name": ingredient_name,
                "quantity": f"{grams} g",
                "priceCostPhp": ingredient_cost(ingredient_name, grams),
                "pricingSource": "PCOSina formulated complete-plate consumed portion estimate",
                "philfctFormulated": True,
                "philfctName": FCT_REF.get(ingredient_name, (ingredient_name, None))[0],
                "philfctCode": FCT_REF.get(ingredient_name, (ingredient_name, None))[1],
            }
            for ingredient_name, grams in ingredients
        ] + [
            {
                "name": "water",
                "quantity": "1 glass",
                "sourceText": "PCOSina complete-plate companion: water; excluded from nutrient totals",
                "philfctName": "Water",
                "philfctCode": "PCOSINA-WATER",
                "priceCostPhp": 0.0,
                "completePlateAddon": True,
                "excludedFromNutritionTotals": True,
            }
        ],
        "instructions": [
            "Prepare the measured one-person portions listed for this complete plate.",
            f"Cook {name} using standard safe cooking practices.",
            "Season lightly with salt and pepper to taste if allowed by the user's preference.",
            "Serve with the listed fruit and one glass of water. Water is not included in nutrition totals.",
        ],
        "sourceServings": "1",
        "sourceDataset": "PCOSina formulated PhilFCT complete-plate expansion",
        "nutritionDataSource": "philfct_formulated_complete_plate_ingredient_sum",
        "nutritionConfidence": "high",
        "nutritionReviewStatus": "source_mapped_needs_final_review",
        "nutritionNotes": FORMULATED_TEMPLATE_NOTES,
        "philfctCoverage": 1.0,
        "estimatedCostPhp": cost(ingredients),
    }


def make_recipe(index: int, name: str, meal_type: str, ingredients):
    tags = ["philfct_budget_support", "philfct_portioned_runtime_candidate"]
    if any(n in {"sardines", "tuna"} for n, _ in ingredients):
        tags += ["contains_fish", "contains_seafood"]
    if any(n in {"shrimp", "squid"} for n, _ in ingredients):
        tags += ["contains_shellfish", "contains_seafood"]
    if any(n == "chicken breast" for n, _ in ingredients):
        tags += ["contains_chicken", "contains_meat"]
    if any(n == "egg" for n, _ in ingredients):
        tags += ["contains_egg"]
    if any(n == "tofu" for n, _ in ingredients):
        tags += ["contains_soy"]
    if any(n in {"cooked munggo", "cooked chickpeas", "red kidney beans boiled"} for n, _ in ingredients):
        tags += ["contains_legume"]
    display_name = name if "Complete Plate" in name else name.replace("Budget Plate", "Complete Plate")
    return {
        "id": f"ph_budget_{index:03d}",
        "name": display_name,
        "title": display_name,
        "mealType": meal_type,
        "tags": sorted(set(tags + ["complete_plate", "includes_water", "includes_fruit", "includes_glow_vegetables"])),
        "nutrition": nutrient(ingredients),
        "ingredients": [
            {
                "name": name,
                "quantity": f"{grams} g",
                "priceCostPhp": ingredient_cost(name, grams),
                "pricingSource": "PCOSina budget-support consumed portion estimate",
                "philfctBudgetSupport": True,
                "philfctName": FCT_REF.get(name, (name, None))[0],
                "philfctCode": FCT_REF.get(name, (name, None))[1],
            }
            for name, grams in ingredients
        ] + [
            {
                "name": "water",
                "quantity": "1 glass",
                "sourceText": "PCOSina complete-plate companion: water; excluded from nutrient totals",
                "philfctName": "Water",
                "philfctCode": "PCOSINA-WATER",
                "priceCostPhp": 0.0,
                "completePlateAddon": True,
                "excludedFromNutritionTotals": True,
            }
        ],
        "instructions": [
            "Prepare the listed ingredients for one serving.",
            f"Cook {display_name} using standard safe cooking practices.",
            "Serve with the listed fruit or vegetable side and one glass of water.",
        ],
        "sourceServings": "1",
        "sourceDataset": "PCOSina PhilFCT budget-support generated plates",
        "nutritionDataSource": "philfct_budget_support_ingredient_sum",
        "nutritionConfidence": "high",
        "nutritionReviewStatus": "source_mapped_needs_final_review",
        "nutritionNotes": (
            "Generated one-person budget support plate computed from PhilFCT-style ingredient values "
            "and consumed-portion price rules."
        ),
        "philfctCoverage": 1.0,
        "estimatedCostPhp": cost(ingredients),
    }


def make_rnd_recipe(recipe_id: str, name: str, meal_type: str, ingredients, extra_tags):
    tags = [
        "complete_plate",
        "includes_water",
        "includes_fruit",
        "includes_glow_vegetables",
        "philfct_rnd_evaluated",
        "philfct_portioned_runtime_candidate",
    ] + list(extra_tags)
    return {
        "id": recipe_id,
        "name": name,
        "title": name,
        "mealType": meal_type,
        "tags": sorted(set(tags)),
        "nutrition": nutrient(ingredients),
        "ingredients": [
            {
                "name": ingredient_name,
                "quantity": f"{grams} g",
                "priceCostPhp": ingredient_cost(ingredient_name, grams),
                "pricingSource": "PCOSina RND evaluated consumed portion estimate",
                "philfctRndEvaluated": True,
                "philfctName": FCT_REF.get(ingredient_name, (ingredient_name, None))[0],
                "philfctCode": FCT_REF.get(ingredient_name, (ingredient_name, None))[1],
            }
            for ingredient_name, grams in ingredients
        ] + [
            {
                "name": "water",
                "quantity": "1 glass",
                "sourceText": "PCOSina complete-plate companion: water; excluded from nutrient totals",
                "philfctName": "Water",
                "philfctCode": "PCOSINA-WATER",
                "priceCostPhp": 0.0,
                "completePlateAddon": True,
                "excludedFromNutritionTotals": True,
            }
        ],
        "instructions": [
            "Prepare the listed ingredients for one serving.",
            f"Cook {name} using standard safe cooking practices.",
            "Serve with one glass of water. Water is not included in the nutrition totals.",
        ],
        "sourceServings": "1",
        "sourceDataset": "PCOSina RND evaluated three-meal seed",
        "nutritionDataSource": "philfct_rnd_evaluated_ingredient_sum",
        "nutritionConfidence": "high",
        "nutritionReviewStatus": "rnd_representative_meal_reviewed",
        "nutritionNotes": (
            "Representative meal adapted from the RND consultation packet; nutrition computed "
            "from one-person ingredient portions using PhilFCT per-100g values."
        ),
        "philfctCoverage": 1.0,
        "estimatedCostPhp": cost(ingredients),
    }


def main() -> int:
    base = json.loads(BASE.read_text(encoding="utf-8"))
    extra = []
    idx = 1
    for cycle in range(5):
        for name, meal_type, ingredients in TEMPLATES:
            extra.append(make_recipe(idx, name, meal_type, ingredients))
            idx += 1
    for name, meal_type, ingredients in BUDGET_VARIETY_TEMPLATES:
        extra.append(make_recipe(idx, name, meal_type, ingredients))
        idx += 1
    for recipe_id, name, meal_type, ingredients, tags in RND_TEMPLATES:
        extra.append(make_rnd_recipe(recipe_id, name, meal_type, ingredients, tags))
    for form_idx, (name, meal_type, ingredients, tags) in enumerate(formulated_templates(), start=1):
        extra.append(make_formulated_recipe(form_idx, name, meal_type, ingredients, tags))
    merged = base + extra
    OUT.write_text(json.dumps(merged, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"base": len(base), "added": len(extra), "total": len(merged), "path": str(OUT)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
