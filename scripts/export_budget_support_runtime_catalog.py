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
    "egg": (139, 12.3, 1.4, 9.4, 0.0),
    "malunggay": (108, 9.7, 12.7, 2.0, 6.7),
    "tomato": (25, 0.8, 5.2, 0.1, 0.3),
    "onion": (52, 1.7, 10.5, 0.3, 2.0),
    "garlic": (129, 7.0, 24.6, 0.3, 1.7),
    "cooking oil": (896, 0, 0, 99.6, 0),
    "eggplant": (25, 1.0, 4.9, 0.1, 1.5),
    "pechay": (20, 1.7, 3.2, 0.2, 1.5),
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
    "tofu": (80, 8.0, 2.0, 5.0, 1.0),
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


PHP_PER_KG = {
        "cooked white rice": 60,
        "cooked munggo": 120,
        "egg": 200,
        "malunggay": 200,
        "tomato": 64,
        "onion": 99,
        "garlic": 146,
        "cooking oil": 211.26,
        "eggplant": 50,
        "pechay": 60,
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
        "tofu": 120,
}


def ingredient_cost(name: str, grams: float) -> float:
    priced_grams = grams
    if name == "cooked white rice":
        priced_grams = grams * 0.34
    if name == "cooked munggo":
        priced_grams = grams * 0.40
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
    result = []
    for meal_type, group in [
        ("Breakfast", allergen_safe_breakfast),
        ("Lunch", allergen_safe_lunch),
        ("Dinner", allergen_safe_dinner),
    ]:
        for name, ingredients in group:
            result.append((name, meal_type, ingredients, ["allergy_safe_fallback", "vegetarian_fallback"]))
    result.extend(general_extra)
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
    if any(n == "egg" for n, _ in ingredients):
        tags.append("contains_egg")
    if any(n == "tofu" for n, _ in ingredients):
        tags.append("contains_soy")
    if any(n == "chicken breast" for n, _ in ingredients):
        tags += ["contains_chicken", "contains_meat"]
    return sorted(set(tags))


def make_formulated_recipe(index: int, name: str, meal_type: str, ingredients, extra_tags):
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
    if any(n == "egg" for n, _ in ingredients):
        tags += ["contains_egg"]
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
