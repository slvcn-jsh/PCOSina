"""Export runtime candidate recipes with one-person portioned ingredients."""

from __future__ import annotations

import json
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


def tokens(text: str) -> set[str]:
    import re
    return set(re.findall(r"[a-zA-Z_]+", str(text or "").lower()))


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
            "name": name,
            "quantity": f"{round(float(portion), 2)} g",
            "sourceText": item.get("source_text"),
            "philfctName": (item.get("philfct") or {}).get("philfct_name"),
            "philfctCode": (item.get("philfct") or {}).get("philfct_code"),
            "priceCostPhp": (item.get("pricing") or {}).get("cost_php"),
        })
    return rows


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
        recipe = {
            **raw,
            "id": raw["id"],
            "name": raw.get("name"),
            "title": raw.get("name"),
            "ingredients": ingredients,
            "nutrition": {
                "calories": int(round(float(nutrition.get("calories") or 0))),
                "protein_g": int(round(float(nutrition.get("protein_g") or 0))),
                "carbs_g": int(round(float(nutrition.get("carbs_g") or 0))),
                "fat_g": int(round(float(nutrition.get("fat_g") or 0))),
                "fiber_g": int(round(float(nutrition.get("fiber_g") or 0))),
            },
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
            "estimatedCostPhp": round(cost, 2),
            "tags": sorted(set((raw.get("tags") or []) + safety_tags(raw, meal) + ["philfct_portioned_runtime_candidate"])),
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
