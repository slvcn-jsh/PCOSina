"""Build a draft PhilFCT-style complete-plate catalog.

This script is intentionally non-destructive. It reads the current runtime
recipe catalog and produces a v2 draft catalog that adapts each source recipe
into the same structure used by the RND-reviewed meals:

- one-person plate components
- Pinggang Pinoy food-group grouping
- gram-based add-on portions
- traceable nutrition provenance
- review flags for records that still need ingredient-level PhilFCT validation

The existing recipe nutrition is preserved as the base dish estimate unless a
recipe already has no usable nutrition. Added plate components use PhilFCT-style
per-100g values from the RND seed format.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter
from pathlib import Path
from statistics import median
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
DEFAULT_RECIPES = BACKEND_ROOT / "recipes.json"
DEFAULT_RND_SEED = BACKEND_ROOT / "seed_data" / "pcosina_rnd_complete_meal_seed_v1.json"
DEFAULT_OUTPUT = BACKEND_ROOT / "seed_data" / "pcosina_philfct_complete_plate_catalog_v2_draft.json"
DEFAULT_REVIEW_CSV = REPO_ROOT / "docs" / "thesis_validation" / "03_ACTUAL_SYSTEM_DATA_EXPORTS" / "pcosina_philfct_complete_plate_catalog_v2_review_queue.csv"

NUTRIENTS = ("calories", "protein_g", "carbs_g", "fat_g", "fiber_g")

PANLASANG_CORRECTIONS = BACKEND_ROOT / "seed_data" / "panlasang_pinoy_nutrition_corrections.csv"
LOCAL_ESTIMATES = BACKEND_ROOT / "seed_data" / "local_reference_nutrition_estimates.csv"


PHILFCT_ADDONS: dict[str, dict[str, Any]] = {
    "cooked_white_rice_100g": {
        "name": "Cooked white rice",
        "portion_g": 100,
        "philfct_name": "Rice, well-milled, boiled",
        "philfct_code": "A020",
        "per_100g": {"calories": 129, "protein_g": 2.1, "carbs_g": 29.7, "fat_g": 0.2, "fiber_g": 0.4},
        "food_group": "go",
    },
    "banana_100g": {
        "name": "Banana",
        "portion_g": 100,
        "philfct_name": "Saging, cavendish, hinog",
        "philfct_code": None,
        "per_100g": {"calories": 104, "protein_g": 0.9, "carbs_g": 23.1, "fat_g": 0.9, "fiber_g": 2.7},
        "food_group": "fruit",
    },
    "papaya_120g": {
        "name": "Papaya",
        "portion_g": 120,
        "philfct_name": "Papaya fruit",
        "philfct_code": None,
        "per_100g": {"calories": 24, "protein_g": 0.7, "carbs_g": 4.9, "fat_g": 0.2, "fiber_g": 0.7},
        "food_group": "fruit",
    },
    "pineapple_120g": {
        "name": "Pineapple",
        "portion_g": 120,
        "philfct_name": "Pineapple",
        "philfct_code": "E073",
        "per_100g": {"calories": 55, "protein_g": 0.4, "carbs_g": 13.0, "fat_g": 0.2, "fiber_g": 1.4},
        "food_group": "fruit",
    },
    "pechay_80g": {
        "name": "Pechay vegetable side",
        "portion_g": 80,
        "philfct_name": "Pechay, boiled",
        "philfct_code": None,
        "per_100g": {"calories": 20, "protein_g": 1.7, "carbs_g": 3.2, "fat_g": 0.2, "fiber_g": 1.5},
        "food_group": "glowVegetables",
    },
}

PROTEIN_TERMS = {
    "pork", "baboy", "liempo", "pata", "beef", "baka", "chicken", "manok",
    "fish", "bangus", "tilapia", "salmon", "tuna", "galunggong", "shrimp",
    "hipon", "squid", "pusit", "crab", "egg", "itlog", "tofu", "tokwa",
    "monggo", "munggo", "lentil", "beans",
}
CARB_TERMS = {
    "rice", "kanin", "fried rice", "pancit", "bihon", "sotanghon", "misua",
    "noodle", "noodles", "pasta", "spaghetti", "macaroni", "bread", "oats",
    "oat", "malagkit", "corn", "mais", "potato", "patatas", "kamote",
}
VEGETABLE_TERMS = {
    "kalabasa", "squash", "sitaw", "string beans", "okra", "talong",
    "eggplant", "malunggay", "pechay", "kangkong", "ampalaya", "sayote",
    "cabbage", "repolyo", "tomato", "kamatis", "onion", "sibuyas",
    "garlic", "bawang", "carrot", "upo", "patola", "mustasa", "spinach",
    "green beans", "bok choy",
}
FRUIT_TERMS = {
    "banana", "saging", "papaya", "pineapple", "pinya", "mango", "mangga",
    "apple", "orange", "calamansi",
}


def _norm(text: str) -> str:
    return re.sub(r"\s+", " ", str(text or "").lower()).strip()


def _has_any(text: str, terms: set[str]) -> bool:
    haystack = _norm(text)
    return any(term in haystack for term in terms)


def _recipe_text(recipe: dict[str, Any]) -> str:
    parts = [recipe.get("name", ""), recipe.get("sourceIngredientNames", "")]
    for ingredient in recipe.get("ingredients") or []:
        if isinstance(ingredient, dict):
            parts.append(str(ingredient.get("name") or ""))
        else:
            parts.append(str(ingredient))
    return " | ".join(parts)


def _read_corrections(path: Path) -> dict[str, dict[str, Any]]:
    if not path.exists():
        return {}
    rows: dict[str, dict[str, Any]] = {}
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            recipe_id = str(row.get("recipe_id") or "").strip()
            if not recipe_id:
                continue
            rows[recipe_id] = row
    return rows


def _to_float(value: Any) -> float | None:
    try:
        if value is None or str(value).strip() == "":
            return None
        return float(str(value).strip())
    except Exception:
        return None


def _correction_nutrition(row: dict[str, Any]) -> dict[str, float] | None:
    values = {
        "calories": _to_float(row.get("calories")),
        "protein_g": _to_float(row.get("protein_grams")),
        "carbs_g": _to_float(row.get("carbs_grams")),
        "fat_g": _to_float(row.get("fats_grams")),
        "fiber_g": _to_float(row.get("fiber_grams")),
    }
    if values["calories"] is None:
        return None
    return {key: float(value or 0) for key, value in values.items()}


def _recipe_nutrition(recipe: dict[str, Any]) -> dict[str, float] | None:
    nutrition = recipe.get("nutrition") or {}
    values = {key: _to_float(nutrition.get(key)) for key in NUTRIENTS}
    if values["calories"] is None:
        return None
    return {key: float(value or 0) for key, value in values.items()}


def _addon_total(addon: dict[str, Any]) -> dict[str, float]:
    factor = float(addon["portion_g"]) / 100
    return {
        key: factor * float(addon["per_100g"][key])
        for key in NUTRIENTS
    }


def _sum_nutrition(parts: list[dict[str, float]]) -> dict[str, float]:
    return {key: round(sum(part.get(key, 0.0) for part in parts), 2) for key in NUTRIENTS}


def _median_nutrition(recipes: list[dict[str, Any]], corrections: dict[str, dict[str, Any]]) -> dict[str, float]:
    buckets: dict[str, list[float]] = {key: [] for key in NUTRIENTS}
    for recipe in recipes:
        nutrition = None
        row = corrections.get(recipe.get("id"))
        if row:
            nutrition = _correction_nutrition(row)
        nutrition = nutrition or _recipe_nutrition(recipe)
        if not nutrition:
            continue
        for key in NUTRIENTS:
            value = nutrition.get(key)
            if value and value > 0:
                buckets[key].append(value)
    return {
        key: round(float(median(values)), 2) if values else fallback
        for key, values, fallback in [
            ("calories", buckets["calories"], 450),
            ("protein_g", buckets["protein_g"], 25),
            ("carbs_g", buckets["carbs_g"], 40),
            ("fat_g", buckets["fat_g"], 15),
            ("fiber_g", buckets["fiber_g"], 5),
        ]
    }


def _nutrition_source(recipe: dict[str, Any], high: dict[str, dict[str, Any]], draft: dict[str, dict[str, Any]], medians: dict[str, float]) -> tuple[dict[str, float], dict[str, Any]]:
    recipe_id = str(recipe.get("id") or "")
    if recipe_id in high:
        row = high[recipe_id]
        nutrition = _correction_nutrition(row)
        if nutrition:
            return nutrition, {
                "source": row.get("source") or "panlasang_pinoy_nutrition_corrections",
                "confidence": row.get("confidence") or "high",
                "reviewStatus": row.get("review_status") or "source_verified",
                "notes": row.get("notes") or "",
            }
    if recipe_id in draft:
        row = draft[recipe_id]
        nutrition = _correction_nutrition(row)
        if nutrition:
            return nutrition, {
                "source": row.get("source") or "local_reference_nutrition_estimates",
                "confidence": row.get("confidence") or "api_estimate",
                "reviewStatus": row.get("review_status") or "pending_review",
                "notes": row.get("notes") or "",
            }
    nutrition = _recipe_nutrition(recipe)
    if nutrition:
        return nutrition, {
            "source": "runtime_recipe_seed",
            "confidence": "estimated",
            "reviewStatus": "needs_review",
            "notes": "Nutrition came from the current runtime recipe seed.",
        }
    return dict(medians), {
        "source": "catalog_median_imputation",
        "confidence": "imputed",
        "reviewStatus": "needs_review",
        "notes": "No usable recipe nutrition was found; draft uses catalog medians until ingredient-level PhilFCT computation is completed.",
    }


def _meal_type(recipe: dict[str, Any]) -> str:
    raw = str(recipe.get("mealType") or "Universal").strip().title()
    if raw not in {"Breakfast", "Lunch", "Dinner", "Universal"}:
        return "Universal"
    return raw


def _select_addons(recipe: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, list[str]], list[str]]:
    text = _recipe_text(recipe)
    meal_type = _meal_type(recipe)
    has_carb = _has_any(text, CARB_TERMS)
    has_protein = _has_any(text, PROTEIN_TERMS)
    has_vegetable = _has_any(text, VEGETABLE_TERMS)
    has_fruit = _has_any(text, FRUIT_TERMS)

    addons: list[dict[str, Any]] = []
    flags: list[str] = []
    groups = {
        "go": [],
        "grow": [],
        "glowVegetables": [],
        "fruit": [],
        "water": ["1 glass water, excluded from nutrient totals"],
    }

    base_name = str(recipe.get("name") or "Base recipe")
    if has_carb:
        groups["go"].append("Base recipe carbohydrate component")
    else:
        addons.append(dict(PHILFCT_ADDONS["cooked_white_rice_100g"]))
        groups["go"].append("Cooked white rice")
        flags.append("added_go_component")

    if has_protein:
        groups["grow"].append("Base recipe protein component")
    else:
        flags.append("needs_grow_component_review")

    if has_vegetable:
        groups["glowVegetables"].append("Base recipe vegetable component")
    else:
        addons.append(dict(PHILFCT_ADDONS["pechay_80g"]))
        groups["glowVegetables"].append("Pechay vegetable side")
        flags.append("added_glow_component")

    if has_fruit:
        groups["fruit"].append("Base recipe fruit component")
    else:
        if meal_type == "Breakfast":
            addons.append(dict(PHILFCT_ADDONS["banana_100g"]))
            groups["fruit"].append("Banana")
        elif meal_type == "Lunch":
            addons.append(dict(PHILFCT_ADDONS["papaya_120g"]))
            groups["fruit"].append("Papaya")
        else:
            addons.append(dict(PHILFCT_ADDONS["pineapple_120g"]))
            groups["fruit"].append("Pineapple")
        flags.append("added_fruit_component")

    if not has_protein or not has_vegetable:
        flags.append("complete_plate_needs_rnd_review")

    groups["baseRecipe"] = [base_name]
    return addons, groups, flags


def _converted_ingredient(addon: dict[str, Any]) -> dict[str, Any]:
    nutrition = _addon_total(addon)
    return {
        "name": addon["name"],
        "portion_g": addon["portion_g"],
        "philfct_name": addon["philfct_name"],
        "philfct_code": addon.get("philfct_code"),
        "per_100g": addon["per_100g"],
        "computed": {key: round(value, 2) for key, value in nutrition.items()},
        "source": "PhilFCT add-on component",
        "match_status": "confirmed" if addon.get("philfct_code") else "needs_exact_philfct_code",
    }


def _source_ingredients(recipe: dict[str, Any]) -> list[str]:
    values = []
    for ingredient in recipe.get("ingredients") or []:
        if isinstance(ingredient, dict):
            name = str(ingredient.get("name") or "").strip()
            quantity = str(ingredient.get("quantity") or "").strip()
            values.append(" ".join(part for part in [quantity, name] if part).strip())
        else:
            values.append(str(ingredient).strip())
    return [value for value in values if value]


def _adapt_recipe(recipe: dict[str, Any], base_nutrition: dict[str, float], provenance: dict[str, Any]) -> dict[str, Any]:
    addons, groups, flags = _select_addons(recipe)
    addon_ingredients = [_converted_ingredient(addon) for addon in addons]
    addon_nutrition = _sum_nutrition([ingredient["computed"] for ingredient in addon_ingredients])
    total = _sum_nutrition([base_nutrition, addon_nutrition])

    confidence = str(provenance.get("confidence") or "").lower()
    review_status = str(provenance.get("reviewStatus") or "").lower()
    if confidence not in {"high", "source_verified"} or "pending" in review_status or "needs" in review_status:
        flags.append("base_recipe_nutrition_needs_philfct_rebuild")

    original_tags = [str(tag) for tag in recipe.get("tags") or [] if tag]
    tags = sorted(set(original_tags + [
        "complete_plate_draft",
        "philfct_addon_components",
        "filipino",
        f"base_{_meal_type(recipe).lower()}",
    ]))

    return {
        "id": f"{recipe.get('id')}_plate_v2",
        "sourceRecipeId": recipe.get("id"),
        "name": f"{recipe.get('name')} Complete Plate",
        "baseRecipeName": recipe.get("name"),
        "mealType": _meal_type(recipe),
        "tags": tags,
        "mainComponents": [
            str(recipe.get("name") or "Base Filipino recipe"),
            *[ingredient["name"] for ingredient in addon_ingredients],
            "Water",
        ],
        "pinggangPinoyGroups": groups,
        "sourceIngredients": _source_ingredients(recipe),
        "sourceInstructions": [
            str(step).strip()
            for step in recipe.get("instructions") or []
            if str(step).strip()
        ],
        "adaptedIngredients": addon_ingredients,
        "baseDishNutrition": {key: round(float(base_nutrition.get(key, 0)), 2) for key in NUTRIENTS},
        "addonNutrition": addon_nutrition,
        "nutrition": total,
        "nutritionProvenance": {
            "baseDish": provenance,
            "addons": "PhilFCT-style per-100g values from RND seed component table",
            "formula": "ingredient contribution = portion_g / 100 * value_per_100g; meal total = base dish + add-on components",
        },
        "reviewStatus": "draft_needs_review" if flags else "draft_ready_for_review",
        "reviewFlags": sorted(set(flags)),
        "nonNutritiveItems": ["1 glass water", "salt and pepper to taste where applicable"],
        "sourceDataset": recipe.get("sourceDataset"),
        "sourceServings": recipe.get("sourceServings"),
    }


def _seed_meal_to_runtime(seed_meal: dict[str, Any]) -> dict[str, Any]:
    ingredient_totals = []
    converted = []
    flags = []
    for ingredient in seed_meal.get("ingredients") or []:
        factor = float(ingredient["portion_g"]) / 100
        computed = {
            key: round(factor * float(ingredient["per_100g"][key]), 2)
            for key in NUTRIENTS
        }
        ingredient_totals.append(computed)
        status = ingredient.get("match_status") or ""
        if status.startswith("review") or status.startswith("needs"):
            flags.append(f"{ingredient['name']}: {status}")
        converted.append({**ingredient, "computed": computed})
    total = _sum_nutrition(ingredient_totals)
    return {
        "id": seed_meal["id"],
        "sourceRecipeId": None,
        "name": seed_meal["name"],
        "baseRecipeName": seed_meal["name"],
        "mealType": seed_meal["mealType"],
        "tags": sorted(set(seed_meal.get("tags") or []) | {"rnd_seed_runtime_candidate"}),
        "mainComponents": seed_meal.get("mainComponents") or [],
        "pinggangPinoyGroups": seed_meal.get("pinggangPinoyGroups") or {},
        "sourceIngredients": [],
        "sourceInstructions": seed_meal.get("instructions") or [],
        "adaptedIngredients": converted,
        "baseDishNutrition": {key: 0 for key in NUTRIENTS},
        "addonNutrition": total,
        "nutrition": total,
        "nutritionProvenance": {
            "baseDish": {
                "source": "RND seed PhilFCT ingredient computation",
                "confidence": "reviewable",
                "reviewStatus": "needs_final_code_review",
                "notes": "Prepared from the RND evaluation packet format.",
            },
            "addons": "All nutritive ingredients are computed as PhilFCT-style portions.",
            "formula": "ingredient contribution = portion_g / 100 * value_per_100g; meal total = sum of all ingredients",
        },
        "reviewStatus": "draft_needs_review" if flags else "draft_ready_for_review",
        "reviewFlags": flags,
        "nonNutritiveItems": seed_meal.get("non_nutritive_items") or [],
        "sourceDataset": "PCOSina RND evaluation seed",
        "sourceServings": "1",
    }


def build_catalog(recipes_path: Path, rnd_seed_path: Path) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    recipes = json.loads(recipes_path.read_text(encoding="utf-8"))
    high = _read_corrections(PANLASANG_CORRECTIONS)
    draft = _read_corrections(LOCAL_ESTIMATES)
    medians = _median_nutrition(recipes, {**draft, **high})

    converted = []
    for recipe in recipes:
        nutrition, provenance = _nutrition_source(recipe, high, draft, medians)
        converted.append(_adapt_recipe(recipe, nutrition, provenance))

    rnd_seed = json.loads(rnd_seed_path.read_text(encoding="utf-8"))
    seed_meals = [_seed_meal_to_runtime(meal) for meal in rnd_seed.get("meals") or []]
    final_meals = seed_meals + converted

    review_rows = []
    for meal in final_meals:
        review_rows.append({
            "id": meal["id"],
            "sourceRecipeId": meal.get("sourceRecipeId") or "",
            "name": meal["name"],
            "mealType": meal["mealType"],
            "calories": meal["nutrition"]["calories"],
            "protein_g": meal["nutrition"]["protein_g"],
            "carbs_g": meal["nutrition"]["carbs_g"],
            "fat_g": meal["nutrition"]["fat_g"],
            "fiber_g": meal["nutrition"]["fiber_g"],
            "reviewStatus": meal["reviewStatus"],
            "reviewFlags": " | ".join(meal.get("reviewFlags") or []),
            "baseNutritionSource": meal["nutritionProvenance"]["baseDish"].get("source"),
            "baseNutritionConfidence": meal["nutritionProvenance"]["baseDish"].get("confidence"),
            "addedComponents": " | ".join(ingredient["name"] for ingredient in meal.get("adaptedIngredients") or []),
        })

    flags = Counter(flag for meal in final_meals for flag in meal.get("reviewFlags") or [])
    meal_types = Counter(meal["mealType"] for meal in final_meals)
    ready = sum(1 for meal in final_meals if meal["reviewStatus"] == "draft_ready_for_review")
    catalog = {
        "version": "pcosina-philfct-complete-plate-catalog-v2-draft",
        "generatedFrom": str(recipes_path.as_posix()),
        "sourceRecipeCount": len(recipes),
        "rndSeedMealCount": len(seed_meals),
        "totalMealCount": len(final_meals),
        "status": "draft_not_runtime_imported",
        "importantNote": "This draft adapts the current recipe catalog into complete plates. Existing base-dish nutrition is preserved from current correction/estimate sources unless ingredient-level PhilFCT computation is later completed.",
        "nutritionFormula": "ingredient contribution = portion_g / 100 * PhilFCT value per 100g",
        "summary": {
            "mealTypes": dict(meal_types),
            "draftReadyForReview": ready,
            "draftNeedsReview": len(final_meals) - ready,
            "topReviewFlags": flags.most_common(20),
        },
        "meals": final_meals,
    }
    return catalog, review_rows


def write_review_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    headers = [
        "id", "sourceRecipeId", "name", "mealType", "calories", "protein_g",
        "carbs_g", "fat_g", "fiber_g", "reviewStatus", "reviewFlags",
        "baseNutritionSource", "baseNutritionConfidence", "addedComponents",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=headers)
        writer.writeheader()
        writer.writerows(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the PCOSina PhilFCT complete-plate v2 draft catalog.")
    parser.add_argument("--recipes", type=Path, default=DEFAULT_RECIPES)
    parser.add_argument("--rnd-seed", type=Path, default=DEFAULT_RND_SEED)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--review-csv", type=Path, default=DEFAULT_REVIEW_CSV)
    args = parser.parse_args()

    catalog, rows = build_catalog(args.recipes, args.rnd_seed)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
    write_review_csv(args.review_csv, rows)

    print(f"Wrote {catalog['totalMealCount']} draft complete-plate meals")
    print(f"Output: {args.output}")
    print(f"Review queue: {args.review_csv}")
    print(json.dumps(catalog["summary"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
