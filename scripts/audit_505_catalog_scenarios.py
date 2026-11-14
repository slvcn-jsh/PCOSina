"""Audit whether the 505 high-coverage PhilFCT draft recipes satisfy scenarios.

This script is a non-destructive feasibility check. It builds an in-memory
solver catalog from the ingredient-level PhilFCT draft, keeps only recipes with
coverage >= 0.80, runs the 20 realistic profiles plus 10 Comment #5 profiles,
and checks selected meals for hard allergy/restriction token violations.
"""

from __future__ import annotations

import csv
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from domain.models import GeneratePlanRequest, UserProfile  # noqa: E402
from services.meal_planner import shortlist_candidates, solve_meal_plan  # noqa: E402


PORTIONED = BACKEND_ROOT / "seed_data" / "pcosina_philfct_portioned_priced_catalog_v2_draft.json"
RECIPES = BACKEND_ROOT / "recipes.json"
REALISTIC = REPO_ROOT / "benchmarks" / "canonical_scenarios" / "planner_realistic_profiles_20.json"
COMMENT5 = REPO_ROOT / "benchmarks" / "canonical_scenarios" / "comment5_canonical_scenarios.json"
OUTPUT = REPO_ROOT / "docs" / "thesis_validation" / "03_ACTUAL_SYSTEM_DATA_EXPORTS" / "pcosina_505_catalog_30_scenario_audit.csv"
SUMMARY = REPO_ROOT / "docs" / "thesis_validation" / "03_ACTUAL_SYSTEM_DATA_EXPORTS" / "pcosina_505_catalog_30_scenario_audit_summary.json"


FORBIDDEN_BY_RESTRICTION = {
    "vegetarian": {
        "pork", "baboy", "liempo", "lechon", "beef", "baka", "chicken", "manok",
        "fish", "bangus", "tilapia", "salmon", "tuna", "shrimp", "hipon", "crab",
        "pusit", "squid", "mussel", "tahong",
    },
    "no pork": {"pork", "baboy", "liempo", "lechon", "bacon", "ham"},
    "no beef": {"beef", "baka", "bulalo", "tapa"},
    "lactose intolerant": {"dairy", "gatas", "cheese", "keso", "cream", "butter"},
    "pescatarian": {"pork", "baboy", "liempo", "lechon", "beef", "baka", "chicken", "manok"},
}

FORBIDDEN_BY_ALLERGY = {
    "peanut": {"peanut", "peanuts", "mani"},
    "dairy": {"dairy", "gatas", "cheese", "keso", "cream", "butter"},
    "egg": {"egg", "eggs", "itlog"},
    "shellfish": {"shrimp", "hipon", "crab", "alimango", "alimasag", "squid", "pusit", "mussel", "tahong", "clam", "oyster"},
    "gluten": {"wheat", "gluten", "flour", "bread"},
    "wheat": {"wheat", "gluten", "flour", "bread"},
    "soy": {"soy", "toyo", "tofu", "tokwa", "soybean"},
    "fish": {"fish", "isda", "bangus", "tilapia", "salmon", "tuna", "galunggong"},
}


def tokens_for(text: str) -> set[str]:
    text = str(text or "").lower()
    return set(re.findall(r"[a-zA-Z_]+", text))


def recipe_text(recipe: dict[str, Any]) -> str:
    parts = [recipe.get("title") or recipe.get("name") or ""]
    for ing in recipe.get("ingredients") or []:
        if isinstance(ing, dict):
            parts.append(str(ing.get("name") or ""))
        else:
            parts.append(str(ing))
    parts.extend(str(tag) for tag in recipe.get("tags") or [])
    return " ".join(parts).lower()


def profile_from_dict(raw: dict[str, Any], name: str) -> UserProfile:
    return UserProfile(
        displayName=name,
        age=int(raw.get("age", 25) or 25),
        heightCm=int(raw.get("heightCm", 160) or 160),
        weightKg=int(raw.get("weightKg", 65) or 65),
        activityLevel=str(raw.get("activityLevel", "Lightly Active") or "Lightly Active"),
        goal=str(raw.get("goal", "General Health") or "General Health"),
        dietaryRestrictions=list(raw.get("dietaryRestrictions", []) or []),
        allergies=list(raw.get("allergies", []) or []),
        weeklyBudgetPhp=(int(raw["weeklyBudgetPhp"]) if raw.get("weeklyBudgetPhp") is not None else None),
        maxCookingTimeMinutes=int(raw.get("maxCookingTimeMinutes", 45) or 45),
        varietyPreference=str(raw.get("varietyPreference", "Balanced") or "Balanced"),
        planningPriority=str(raw.get("planningPriority", "Balanced") or "Balanced"),
        pantryItems=list(raw.get("pantryItems", []) or []),
    )


def load_scenarios() -> list[dict[str, Any]]:
    data = json.loads(REALISTIC.read_text(encoding="utf-8"))
    scenarios = []
    for case in data["cases"]:
        scenarios.append({
            "id": case["id"],
            "name": case.get("userType") or case["id"],
            "profile": profile_from_dict(case["profile"], case.get("userType") or case["id"]),
        })
    c5 = json.loads(COMMENT5.read_text(encoding="utf-8"))
    for i, raw in enumerate(c5["scenarios"], start=1):
        scenarios.append({
            "id": f"C5-{i:02d}",
            "name": raw["name"],
            "profile": profile_from_dict(raw, raw["name"]),
        })
    return scenarios


def to_solver_recipe(raw: dict[str, Any], computed: dict[str, Any], coverage: float) -> dict[str, Any]:
    nutrition = computed or {}
    return {
        "id": raw.get("id"),
        "title": raw.get("name") or raw.get("title"),
        "mealType": raw.get("mealType", "Universal"),
        "calories": int(round(float(nutrition.get("calories") or 0))),
        "proteinGrams": int(round(float(nutrition.get("protein_g") or 0))),
        "carbsGrams": int(round(float(nutrition.get("carbs_g") or 0))),
        "fatsGrams": int(round(float(nutrition.get("fat_g") or 0))),
        "fiberGrams": int(round(float(nutrition.get("fiber_g") or 0))),
        "tags": list(raw.get("tags") or []) + ["philfct_auto_coverage_80"],
        "minutes": int(raw.get("minutes") or 30),
        "ingredients": raw.get("ingredients") or [],
        "steps": raw.get("instructions") or [],
        "_philfctCoverage": coverage,
    }


def load_505_catalog(threshold: float = 0.80, *, max_cost_php: float | None = None) -> list[dict[str, Any]]:
    candidate_path = os.getenv("PCOSINA_AUDIT_RECIPE_CATALOG", "").strip()
    if candidate_path:
        raw_recipes = json.loads(Path(candidate_path).read_text(encoding="utf-8"))
        return [
            item
            for item in (
            {
                "id": r.get("id"),
                "title": r.get("name") or r.get("title"),
                "mealType": r.get("mealType", "Universal"),
                "calories": int((r.get("nutrition") or {}).get("calories") or 0),
                "proteinGrams": int((r.get("nutrition") or {}).get("protein_g") or 0),
                "carbsGrams": int((r.get("nutrition") or {}).get("carbs_g") or 0),
                "fatsGrams": int((r.get("nutrition") or {}).get("fat_g") or 0),
                "fiberGrams": int((r.get("nutrition") or {}).get("fiber_g") or 0),
                "tags": r.get("tags") or [],
                "minutes": int(r.get("minutes") or 30),
                "ingredients": r.get("ingredients") or [],
                "steps": r.get("instructions") or [],
                "sourceServings": "1",
                "_portion_cost_php": float(r.get("estimatedCostPhp") or 0),
                "_cost_est": int(round(float(r.get("estimatedCostPhp") or 0))),
            }
            for r in raw_recipes
            )
            if not os.getenv("PCOSINA_AUDIT_MAX_RECIPE_COST")
            or float(item.get("_portion_cost_php") or 0) <= float(os.getenv("PCOSINA_AUDIT_MAX_RECIPE_COST") or 0)
        ]
    recipes_by_id = {r["id"]: r for r in json.loads(RECIPES.read_text(encoding="utf-8"))}
    portioned = json.loads(PORTIONED.read_text(encoding="utf-8"))
    solver_recipes = []
    for meal in portioned["meals"]:
        coverage = float(meal.get("coverage") or 0)
        if coverage < threshold:
            continue
        raw = recipes_by_id.get(meal["sourceRecipeId"])
        if not raw:
            continue
        computed = meal.get("computedPhilFctNutrition") or {}
        if not computed.get("calories"):
            continue
        item = to_solver_recipe(raw, computed, coverage)
        portion_cost = float((meal.get("portionPricing") or {}).get("estimatedMealCostPhp") or 0)
        if max_cost_php is not None and portion_cost > max_cost_php:
            continue
        item["_cost_est"] = int(round(portion_cost)) if portion_cost > 0 else item.get("_cost_est", 0)
        item["_portion_cost_php"] = round(portion_cost, 2)
        item["_portion_pricing_source"] = "philfct_portioned_priced_catalog"
        solver_recipes.append(item)
    return solver_recipes


def extract_plan_meals(result: Any) -> list[Any]:
    if result is None:
        return []
    if isinstance(result, tuple) and result:
        return extract_plan_meals(result[0])
    if isinstance(result, list):
        return result
    plan = getattr(result, "plan", None) or getattr(result, "days", None)
    if plan:
        return plan
    return []


def selected_recipes(result: Any, recipe_by_id: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    meals = []
    for day in extract_plan_meals(result):
        day_meals = getattr(day, "meals", None)
        if day_meals is None and isinstance(day, dict):
            day_meals = day.get("meals")
        for meal in day_meals or []:
            recipe = None
            if isinstance(meal, dict):
                recipe = meal.get("recipe") or meal
            else:
                recipe = getattr(meal, "recipe", None) or meal
            if recipe:
                if hasattr(recipe, "model_dump"):
                    recipe = recipe.model_dump()
                elif not isinstance(recipe, dict):
                    recipe = recipe.__dict__
                recipe_id = str(recipe.get("recipeId") or recipe.get("id") or "")
                meals.append(recipe_by_id.get(recipe_id, recipe))
    return meals


def hard_violations(profile: UserProfile, recipes: list[dict[str, Any]]) -> list[str]:
    violations = []
    restriction_terms = set()
    for restriction in profile.dietaryRestrictions or []:
        restriction_terms |= FORBIDDEN_BY_RESTRICTION.get(str(restriction).lower(), set())
    allergy_terms = set()
    for allergy in profile.allergies or []:
        allergy_terms |= FORBIDDEN_BY_ALLERGY.get(str(allergy).lower(), {str(allergy).lower()})

    for recipe in recipes:
        text = recipe_text(recipe)
        toks = tokens_for(text)
        for term in sorted(restriction_terms):
            if term in toks:
                violations.append(f"{recipe.get('id')} {recipe.get('title') or recipe.get('name')} violates restriction term '{term}'")
                break
        for term in sorted(allergy_terms):
            if term in toks:
                violations.append(f"{recipe.get('id')} {recipe.get('title') or recipe.get('name')} violates allergy term '{term}'")
                break
    return violations


def main() -> int:
    recipes = load_505_catalog()
    import services.meal_planner as meal_planner
    original_estimate_cost = meal_planner.estimate_cost

    def portion_price_estimate(recipe: dict[str, Any], **_: Any) -> int:
        if recipe.get("_portion_cost_php"):
            return int(max(1, round(float(recipe.get("_portion_cost_php") or 0))))
        return original_estimate_cost(recipe)

    meal_planner.estimate_cost = portion_price_estimate
    recipe_by_id = {str(recipe.get("id")): recipe for recipe in recipes}
    scenarios = load_scenarios()
    rows = []
    success = 0
    zero_violation_success = 0
    for scenario in scenarios:
        profile = scenario["profile"]
        buckets = shortlist_candidates(profile, recipes)
        candidate_count = sum(len(v) for v in buckets.values())
        started = time.perf_counter()
        status = "success"
        detail = ""
        picked = []
        violations = []
        try:
            result = solve_meal_plan(GeneratePlanRequest(profile=profile, days=7, mealsPerDay=3), recipes)
            picked = selected_recipes(result, recipe_by_id)
            if len(picked) < 21:
                status = "incomplete"
                detail = f"selected {len(picked)} meals"
            else:
                success += 1
            violations = hard_violations(profile, picked)
            if status == "success" and not violations:
                zero_violation_success += 1
        except Exception as exc:
            status = "failed"
            detail = str(exc)[:500]
        elapsed = time.perf_counter() - started
        rows.append({
            "scenario_id": scenario["id"],
            "scenario_name": scenario["name"],
            "status": status,
            "candidate_count": candidate_count,
            "selected_count": len(picked),
            "hard_violation_count": len(violations),
            "hard_violations": " | ".join(violations[:10]),
            "elapsed_seconds": round(elapsed, 3),
            "detail": detail,
            "restrictions": "|".join(profile.dietaryRestrictions or []),
            "allergies": "|".join(profile.allergies or []),
            "weeklyBudgetPhp": profile.weeklyBudgetPhp,
        })

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", encoding="utf-8-sig", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    summary = {
        "catalogRecipeCount": len(recipes),
        "scenarioCount": len(scenarios),
        "successCount": success,
        "zeroHardViolationSuccessCount": zero_violation_success,
        "failedOrIncomplete": [row for row in rows if row["status"] != "success" or row["hard_violation_count"]],
        "outputCsv": str(OUTPUT),
    }
    SUMMARY.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 0 if zero_violation_success == len(scenarios) else 1


if __name__ == "__main__":
    raise SystemExit(main())
