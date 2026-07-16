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
    ("Tuna Pechay Budget Plate", "Dinner", [("cooked white rice", 130), ("tuna", 100), ("pechay", 130), ("tomato", 50), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("papaya", 80)]),
    ("Okra Sitaw Munggo Budget Plate", "Breakfast", [("cooked white rice", 130), ("cooked munggo", 200), ("okra", 80), ("sitaw", 80), ("tomato", 50), ("onion", 25), ("cooking oil", 8), ("banana", 60)]),
    ("Squash Munggo Rice Budget Plate", "Lunch", [("cooked white rice", 130), ("cooked munggo", 200), ("squash", 140), ("malunggay", 40), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("papaya", 80)]),
    ("Egg Pechay Rice Budget Plate", "Dinner", [("cooked white rice", 130), ("egg", 100), ("pechay", 140), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 8), ("banana", 60)]),
    ("Tofu Munggo Malunggay Budget Plate", "Breakfast", [("cooked white rice", 120), ("tofu", 120), ("cooked munggo", 150), ("malunggay", 40), ("tomato", 50), ("cooking oil", 8), ("papaya", 80)]),
    ("Sardines Pechay Egg Budget Plate", "Dinner", [("cooked white rice", 120), ("sardines", 70), ("egg", 50), ("pechay", 120), ("tomato", 50), ("onion", 25), ("cooking oil", 6), ("papaya", 80)]),
    ("Tuna Munggo Vegetable Budget Plate", "Lunch", [("cooked white rice", 120), ("tuna", 70), ("cooked munggo", 120), ("okra", 70), ("tomato", 50), ("onion", 25), ("cooking oil", 6), ("banana", 60)]),
    ("Egg Tofu Talong Budget Plate", "Breakfast", [("cooked white rice", 120), ("egg", 50), ("tofu", 120), ("eggplant", 140), ("tomato", 60), ("onion", 25), ("cooking oil", 8), ("banana", 60)]),
    ("Munggo Okra Malunggay Dinner Plate", "Dinner", [("cooked white rice", 130), ("cooked munggo", 230), ("okra", 90), ("malunggay", 45), ("tomato", 60), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("papaya", 80)]),
    ("Tofu Sitaw Squash Dinner Plate", "Dinner", [("cooked white rice", 120), ("tofu", 160), ("sitaw", 100), ("squash", 160), ("malunggay", 35), ("tomato", 50), ("onion", 25), ("garlic", 5), ("cooking oil", 10), ("banana", 60)]),
]


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
            "Prepare the measured one-person portions listed for this complete plate.",
            f"Cook the main components for {display_name} using standard safe cooking practices.",
            "Serve the plate with the listed fruit or vegetable companion and one glass of water.",
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


def main() -> int:
    base = json.loads(BASE.read_text(encoding="utf-8"))
    extra = []
    idx = 1
    for cycle in range(5):
        for name, meal_type, ingredients in TEMPLATES:
            extra.append(make_recipe(idx, name, meal_type, ingredients))
            idx += 1
    merged = base + extra
    OUT.write_text(json.dumps(merged, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"base": len(base), "added": len(extra), "total": len(merged), "path": str(OUT)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
