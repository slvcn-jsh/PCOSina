"""Export runtime candidate recipes with one-person portioned ingredients."""

from __future__ import annotations

import json
import re
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
RECIPES = BACKEND_ROOT / "recipes.json"
PRICED = BACKEND_ROOT / "seed_data" / "pcosina_philfct_portioned_priced_catalog_v2_draft.json"
OUT_80 = BACKEND_ROOT / "seed_data" / "pcosina_philfct_runtime_candidate_catalog_v2_coverage80.json"
OUT_70 = BACKEND_ROOT / "seed_data" / "pcosina_philfct_runtime_candidate_catalog_v2_coverage70.json"

PORK = {"pork", "baboy", "liempo", "lechon", "bacon", "ham", "pata"}
BEEF = {"beef", "baka", "bulalo", "tapa"}
CHICKEN = {"chicken", "manok"}
FISH = {"fish", "bangus", "milkfish", "tilapia", "salmon", "tuna", "galunggong"}
SHELLFISH = {"shrimp", "hipon", "crab", "alimango", "alimasag", "squid", "pusit", "mussel", "tahong"}
DAIRY = {"milk", "gatas", "cheese", "keso", "butter", "cream"}
EGG = {"egg", "eggs", "itlog"}
CARB_TOKENS = {"rice", "oats", "oatmeal", "adlai", "corn", "kamote", "potato", "noodle", "noodles", "pancit", "bihon"}
VEG_TOKENS = {
    "malunggay", "pechay", "okra", "sitaw", "string", "beans", "eggplant", "talong",
    "tomato", "kamatis", "onion", "garlic", "kalabasa", "squash", "sayote",
    "kangkong", "ampalaya", "cabbage", "carrot", "vegetable", "gulay",
}
FRUIT_TOKENS = {"banana", "saging", "pineapple", "mango", "apple"}

ADDON_FCT = {
    "cooked white rice": {
        "quantity": "100 g",
        "portion_g": 100.0,
        "philfctName": "Rice, well-milled, boiled",
        "philfctCode": "A020",
        "nutrition": {"calories": 129.0, "protein_g": 2.1, "carbs_g": 29.7, "fat_g": 0.2, "fiber_g": 0.4},
        "cost": 2.04,
        "component": "Go / carbohydrate",
    },
    "pechay and tomato vegetable side": {
        "quantity": "100 g",
        "portion_g": 100.0,
        "philfctName": "Pechay and tomato side, PhilFCT component estimate",
        "philfctCode": "PCOSINA-GLOW-001",
        "nutrition": {"calories": 23.0, "protein_g": 1.4, "carbs_g": 4.2, "fat_g": 0.2, "fiber_g": 1.2},
        "cost": 7.0,
        "component": "Glow / vegetables",
    },
    "banana": {
        "quantity": "80 g",
        "portion_g": 80.0,
        "philfctName": "Saging, cavendish, hinog",
        "philfctCode": "E085",
        "nutrition": {"calories": 83.2, "protein_g": 0.72, "carbs_g": 18.48, "fat_g": 0.72, "fiber_g": 2.16},
        "cost": 7.2,
        "component": "Fruit / additional glow",
    },
    "cooked munggo": {
        "quantity": "150 g",
        "portion_g": 150.0,
        "philfctName": "Mung bean, boiled",
        "philfctCode": "D132",
        "nutrition": {"calories": 81.0, "protein_g": 7.2, "carbs_g": 10.5, "fat_g": 0.75, "fiber_g": 2.4},
        "cost": 7.2,
        "component": "Grow / plant protein",
    },
}

NUTRIENT_KEYS = ("calories", "protein_g", "carbs_g", "fat_g", "fiber_g")
MAX_RUNTIME_MEAL_CALORIES = 1200
MAX_RUNTIME_MEAL_FAT_G = 80
MAX_RUNTIME_MEAL_CARBS_G = 180


def tokens(text: str) -> set[str]:
    return set(re.findall(r"[a-zA-Z_]+", str(text or "").lower()))


def sanitize_recipe_name(name: str, meal_type: str) -> str:
    value = str(name or "Filipino Meal").strip()
    replacements = [
        (r"\s*-\s*paano lutuin at mga sangkap\s*$", ""),
        (r"\s*-\s*mga sangkap at paano lutuin\s*$", ""),
        (r"\s*-\s*paano lutuin\s*$", ""),
        (r"^how to cook\s+", ""),
        (r"\s+tagalog recipe\s*$", ""),
        (r"\s+panlasang pinoy\s*$", ""),
        (r"\s*\([^)]*recipe[^)]*\)\s*", " "),
        (r"\brecipe\b", " "),
        (r"\bpanlasang pinoy\b", " "),
    ]
    for pattern, repl in replacements:
        value = re.sub(pattern, repl, value, flags=re.I)
    value = re.sub(r"\s+", " ", value).strip(" -")
    if not value:
        value = "Filipino Meal"
    value = re.sub(r"\bcomplete\s+(?:plate|meal|bowl)\b", " ", value, flags=re.I)
    value = re.sub(r"\bcomplete\b", " ", value, flags=re.I)
    value = re.sub(r"\b(bowl|plate|meal)\b", " ", value, flags=re.I)
    value = re.sub(r"\s+", " ", value).strip(" -")
    value = f"{value} Complete Plate"
    return value


def ingredient_text(ingredients: list[dict]) -> str:
    return " ".join(
        " ".join(str(item.get(key) or "") for key in ("name", "sourceText", "philfctName"))
        for item in ingredients
    ).lower()


def addon_rows(ingredients: list[dict], nutrition: dict, tags: set[str]) -> tuple[list[dict], dict, float, list[str]]:
    text = ingredient_text(ingredients)
    toks = tokens(text)
    fruit_text = " ".join(str(item.get("name") or "") for item in ingredients).lower()
    fruit_text = re.sub(r"\bbanana\s+(?:flower|blossom|blossoms|bud|buds)\b", " ", fruit_text)
    fruit_text = re.sub(r"\bpuso\s+ng\s+banana\b", " ", fruit_text)
    fruit_toks = tokens(fruit_text)
    additions: list[str] = []
    extra_cost = 0.0
    updated = {key: float(nutrition.get(key) or 0) for key in NUTRIENT_KEYS}
    rows: list[dict] = []

    for derived_tag in (
        "complete_plate",
        "includes_water",
        "includes_fruit",
        "includes_glow_vegetables",
        "includes_go_carbohydrate",
        "includes_grow_protein",
    ):
        tags.discard(derived_tag)

    needed: list[str] = []
    if not (toks & CARB_TOKENS) and updated["carbs_g"] < 45:
        needed.append("cooked white rice")
    if updated["protein_g"] < 18 and "cooked munggo" not in needed:
        needed.append("cooked munggo")
    if updated["calories"] < 400 and "cooked white rice" not in needed:
        needed.append("cooked white rice")
    if not (toks & VEG_TOKENS):
        needed.append("pechay and tomato vegetable side")
    if not (fruit_toks & FRUIT_TOKENS):
        needed.append("banana")

    for key in needed:
        addon = ADDON_FCT[key]
        rows.append({
            "name": key,
            "quantity": addon["quantity"],
            "sourceText": f"PCOSina complete-plate companion: {addon['component']}",
            "philfctName": addon["philfctName"],
            "philfctCode": addon["philfctCode"],
            "priceCostPhp": addon["cost"],
            "completePlateAddon": True,
        })
        for nutrient_key in NUTRIENT_KEYS:
            updated[nutrient_key] += float(addon["nutrition"][nutrient_key])
        extra_cost += float(addon["cost"])
        additions.append(key)

    rows.append({
        "name": "water",
        "quantity": "1 glass",
        "sourceText": "PCOSina complete-plate companion: water; excluded from nutrient totals",
        "philfctName": "Water",
        "philfctCode": "PCOSINA-WATER",
        "priceCostPhp": 0.0,
        "completePlateAddon": True,
        "excludedFromNutritionTotals": True,
    })
    additions.append("water")
    tags.update({"complete_plate", "includes_water"})
    if any(item in needed for item in ("banana",)):
        tags.add("includes_fruit")
    elif fruit_toks & FRUIT_TOKENS:
        tags.add("includes_fruit")
    if any(item in needed for item in ("pechay and tomato vegetable side",)):
        tags.add("includes_glow_vegetables")
    if any(item in needed for item in ("cooked white rice",)):
        tags.add("includes_go_carbohydrate")
    if any(item in needed for item in ("cooked munggo",)):
        tags.add("includes_grow_protein")
    return rows, updated, extra_cost, additions


def safety_tags(raw: dict, meal: dict) -> list[str]:
    text_parts = [raw.get("name", ""), raw.get("sourceIngredientNames", "")]
    for item in raw.get("ingredients") or []:
        text_parts.append(str(item.get("name") if isinstance(item, dict) else item))
    for item in meal.get("portionIngredients") or []:
        text_parts.append(str(item.get("source_text") or ""))
        text_parts.append(str(item.get("clean_name") or ""))
    toks = tokens(" ".join(text_parts))
    tags = set()
    if toks & PORK:
        tags.update({"contains_pork", "contains_meat"})
    if toks & BEEF:
        tags.update({"contains_beef", "contains_meat"})
    if toks & CHICKEN:
        tags.update({"contains_chicken", "contains_meat"})
    if toks & FISH:
        tags.update({"contains_fish", "contains_seafood"})
    if toks & SHELLFISH:
        tags.update({"contains_shellfish", "contains_seafood"})
    if toks & DAIRY:
        # Coconut milk/cream is not dairy milk. Only tag dairy if the actual
        # source text has non-coconut dairy terms.
        joined = " ".join(text_parts).lower()
        if "coconut milk" not in joined and "coconut cream" not in joined:
            tags.add("contains_dairy")
    if toks & EGG:
        tags.add("contains_egg")
    return sorted(tags)


def ingredient_rows(meal: dict) -> list[dict]:
    rows = []
    for item in meal.get("portionIngredients") or []:
        portion = item.get("portion_g")
        if portion is None or float(portion or 0) <= 0:
            continue
        if not item.get("computed"):
            continue
        name = item.get("clean_name") or item.get("source_text") or "ingredient"
        rows.append({
            "name": display_ingredient_name(name),
            "quantity": f"{round(float(portion), 2)} g",
            "sourceText": item.get("source_text"),
            "philfctName": (item.get("philfct") or {}).get("philfct_name"),
            "philfctCode": (item.get("philfct") or {}).get("philfct_code"),
            "priceCostPhp": (item.get("pricing") or {}).get("cost_php"),
        })
    return rows


def display_ingredient_name(name: str) -> str:
    value = str(name or "ingredient").strip().lower()
    value = re.sub(
        r"\b(?:to taste|taste|preferably|optional|garnish|serve|serving|"
        r"hiniwa|binalatan|pahaba|dinikdik|tinadtad|hiwa|piraso|maliliit|"
        r"nilaga|pinakuluan|ginayat|hiniwang|piniraso|dahon|at)\b",
        " ",
        value,
    )
    replacements = {
        "kamote": "sweet potato",
        "saging": "banana",
        "sibuyas": "onion",
        "bawang": "garlic",
        "patis": "fish sauce",
        "kalamansi": "calamansi",
        "talong": "eggplant",
        "kamatis": "tomato",
    }
    for src, dst in replacements.items():
        value = re.sub(rf"\b{re.escape(src)}\b", dst, value)
    value = value.replace("adodo", "adobo")
    cleanup_phrases = {
        "fish sauce fish sauce": "fish sauce",
        "sweet potatoes sweet potato": "sweet potato",
        "sweet potato sweet potato": "sweet potato",
        "banana bananas": "banana",
        "bananas banana": "banana",
        "onion onion": "onion",
        "garlic garlic": "garlic",
        "tomato tomato": "tomato",
        "eggplant eggplant": "eggplant",
    }
    for src, dst in cleanup_phrases.items():
        value = value.replace(src, dst)
    value = re.sub(r"\s+", " ", value).strip()
    return value or "ingredient"


def english_instructions(title: str, additions: list[str]) -> list[str]:
    companion_text = ", ".join(item for item in additions if item != "water")
    steps = [
        "Prepare the listed ingredients for one serving.",
        f"Cook or reheat {title} using standard safe cooking practices.",
    ]
    if companion_text:
        steps.append(f"Serve with the listed side items: {companion_text}.")
    steps.append("Serve with one glass of water. Water is not included in the nutrition totals.")
    return steps


def export(threshold: float, out_path: Path) -> dict:
    recipes_by_id = {r["id"]: r for r in json.loads(RECIPES.read_text(encoding="utf-8"))}
    priced = json.loads(PRICED.read_text(encoding="utf-8"))
    out = []
    for meal in priced["meals"]:
        coverage = float(meal.get("coverage") or 0)
        if coverage < threshold:
            continue
        raw = recipes_by_id.get(meal["sourceRecipeId"])
        if not raw:
            continue
        nutrition = meal.get("computedPhilFctNutrition") or {}
        if not nutrition.get("calories"):
            continue
        ingredients = ingredient_rows(meal)
        if not ingredients:
            continue
        cost = float((meal.get("portionPricing") or {}).get("estimatedMealCostPhp") or 0)
        tags = set((raw.get("tags") or []) + safety_tags(raw, meal) + ["philfct_portioned_runtime_candidate"])
        addon_ingredients, updated_nutrition, addon_cost, additions = addon_rows(ingredients, nutrition, tags)
        calories = float(updated_nutrition.get("calories") or 0)
        protein = float(updated_nutrition.get("protein_g") or 0)
        fat = float(updated_nutrition.get("fat_g") or 0)
        carbs = float(updated_nutrition.get("carbs_g") or 0)
        if (
            calories < 400
            or protein < 10
            or calories > MAX_RUNTIME_MEAL_CALORIES
            or fat > MAX_RUNTIME_MEAL_FAT_G
            or carbs > MAX_RUNTIME_MEAL_CARBS_G
        ):
            continue
        ingredients = ingredients + addon_ingredients
        clean_name = sanitize_recipe_name(raw.get("name") or raw.get("title"), raw.get("mealType", "Universal"))
        recipe = {
            **raw,
            "id": raw["id"],
            "name": clean_name,
            "title": clean_name,
            "ingredients": ingredients,
            "nutrition": {
                "calories": int(round(float(updated_nutrition.get("calories") or 0))),
                "protein_g": int(round(float(updated_nutrition.get("protein_g") or 0))),
                "carbs_g": int(round(float(updated_nutrition.get("carbs_g") or 0))),
                "fat_g": int(round(float(updated_nutrition.get("fat_g") or 0))),
                "fiber_g": int(round(float(updated_nutrition.get("fiber_g") or 0))),
            },
            "instructions": english_instructions(clean_name, additions),
            "sourceServings": "1",
            "sourceDataset": "PCOSina PhilFCT portioned runtime candidate v2",
            "nutritionDataSource": "philfct_ingredient_sum_auto",
            "nutritionConfidence": "high" if coverage >= 0.80 else "medium",
            "nutritionReviewStatus": "source_mapped_needs_final_review",
            "nutritionNotes": (
                "Nutrition computed from one-person ingredient portions using PhilFCT per-100g values; "
                "automated ingredient matches remain reviewable where applicable."
            ),
            "philfctCoverage": round(coverage, 3),
            "estimatedCostPhp": round(cost + addon_cost, 2),
            "completePlateAdditions": additions,
            "tags": sorted(tags),
        }
        out.append(recipe)
    out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"threshold": threshold, "count": len(out), "path": str(out_path)}


def main() -> int:
    summaries = [export(0.80, OUT_80), export(0.70, OUT_70)]
    print(json.dumps(summaries, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
