"""Audit runtime recipe-catalog diversity across representative planner scenarios.

This is intentionally a backend-facing script. It calls the same deterministic
Stage 1 filtering and CP-SAT solver path used by plan generation, then exports
small JSON/CSV summaries that can be reviewed after catalog or policy changes.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import Counter
from copy import deepcopy
from pathlib import Path
from typing import Any, Dict, Iterable, List


ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from domain.models import GeneratePlanRequest, UserProfile  # noqa: E402
from policy_config import default_policy, resolve_policy_for_environment  # noqa: E402
from services import meal_planner  # noqa: E402
import policy_store  # noqa: E402


DEFAULT_CATALOG = BACKEND_ROOT / "recipes.json"
DEFAULT_OUTPUT_DIR = ROOT / "docs" / "thesis_validation" / "03_ACTUAL_SYSTEM_DATA_EXPORTS"
DEFAULT_JSON = DEFAULT_OUTPUT_DIR / "pcosina_current_catalog_diversity_audit_summary.json"
DEFAULT_CSV = DEFAULT_OUTPUT_DIR / "pcosina_current_catalog_diversity_audit_summary.csv"
DEFAULT_50_JSON = DEFAULT_OUTPUT_DIR / "pcosina_current_catalog_diversity_audit_50_scenarios.json"
DEFAULT_50_CSV = DEFAULT_OUTPUT_DIR / "pcosina_current_catalog_diversity_audit_50_scenarios.csv"


def _num(value: Any, default: float = 0.0) -> float:
    try:
        if value is None or value == "":
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def _int(value: Any, default: int = 0) -> int:
    return int(round(_num(value, float(default))))


def normalize_recipe(raw: Dict[str, Any]) -> Dict[str, Any]:
    nutrition = raw.get("nutrition") if isinstance(raw.get("nutrition"), dict) else {}
    recipe = dict(raw)
    recipe["id"] = str(raw.get("id") or "").strip()
    recipe["title"] = str(raw.get("title") or raw.get("name") or recipe["id"]).strip()
    recipe["mealType"] = str(raw.get("mealType") or raw.get("meal") or "Universal").strip() or "Universal"
    recipe["calories"] = _int(raw.get("calories", nutrition.get("calories")))
    recipe["proteinGrams"] = _int(raw.get("proteinGrams", nutrition.get("protein_g", nutrition.get("proteinGrams"))))
    recipe["carbsGrams"] = _int(raw.get("carbsGrams", nutrition.get("carbs_g", nutrition.get("carbsGrams"))))
    recipe["fatsGrams"] = _int(raw.get("fatsGrams", nutrition.get("fat_g", nutrition.get("fatsGrams"))))
    recipe["fiberGrams"] = _int(raw.get("fiberGrams", nutrition.get("fiber_g", nutrition.get("fiberGrams"))))
    recipe["sodiumMg"] = raw.get("sodiumMg", nutrition.get("sodium_mg", nutrition.get("sodiumMg")))
    recipe["sugarGrams"] = raw.get("sugarGrams", nutrition.get("sugar_g", nutrition.get("sugarGrams")))
    recipe["minutes"] = _int(raw.get("minutes", raw.get("prepTimeMinutes", raw.get("cookingTimeMinutes", 30))), 30)
    recipe["tags"] = list(raw.get("tags") or [])
    recipe["ingredients"] = list(raw.get("ingredients") or [])
    recipe["steps"] = list(raw.get("steps") or raw.get("instructions") or [])
    return recipe


def load_catalog(path: Path) -> List[Dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    if not isinstance(payload, list):
        raise ValueError(f"Catalog must be a list: {path}")
    recipes = [normalize_recipe(item) for item in payload if isinstance(item, dict)]
    missing_ids = [index for index, recipe in enumerate(recipes) if not recipe.get("id")]
    if missing_ids:
        raise ValueError(f"Catalog has recipes without ids at indexes: {missing_ids[:10]}")
    return recipes


def production_policy() -> Dict[str, Any]:
    bootstrapped = policy_store._apply_runtime_bootstrap(default_policy().to_runtime_dict())
    return resolve_policy_for_environment(bootstrapped, "production")


def base_profile(**overrides: Any) -> UserProfile:
    payload: Dict[str, Any] = {
        "displayName": "Catalog Diversity Audit",
        "age": 28,
        "heightCm": 160,
        "weightKg": 65,
        "activityLevel": "Lightly Active",
        "goal": "Weight Loss, Symptom Management",
        "symptoms": ["Weight gain", "Irregular periods", "Acne"],
        "weeklyBudgetPhp": 3000,
        "maxCookingTimeMinutes": 45,
        "varietyPreference": "Balanced",
        "planningPriority": "Balanced",
        "dietaryRestrictions": [],
        "allergies": [],
        "pantryItems": [],
    }
    payload.update(overrides)
    return UserProfile.model_validate(payload)


def core_scenario_profiles() -> Dict[str, UserProfile]:
    return {
        "broad_balanced": base_profile(),
        "broad_high_variety": base_profile(varietyPreference="High", planningPriority="Variety First"),
        "vegetarian": base_profile(dietaryRestrictions=["Vegetarian"]),
        "pescatarian": base_profile(dietaryRestrictions=["Pescatarian"]),
        "soy_allergy": base_profile(allergies=["soy"]),
        "strict_no_pork_lactose_egg_fish": base_profile(
            dietaryRestrictions=["No Pork", "Lactose Intolerant"],
            allergies=["egg", "fish"],
        ),
        "strict_no_pork_lactose_egg_fish_high_variety": base_profile(
            dietaryRestrictions=["No Pork", "Lactose Intolerant"],
            allergies=["egg", "fish"],
            varietyPreference="High",
            planningPriority="Variety First",
        ),
        "no_pork_no_beef_fish_allergy": base_profile(
            dietaryRestrictions=["No Pork", "No Beef"],
            allergies=["fish"],
        ),
        "custom_chicken_allergy": base_profile(allergies=["chicken"]),
    }


def extended_50_scenario_profiles() -> Dict[str, UserProfile]:
    scenarios: Dict[str, UserProfile] = {
        "broad_balanced": base_profile(),
        "broad_high_variety": base_profile(varietyPreference="High", planningPriority="Variety First"),
        "broad_budget_first": base_profile(planningPriority="Budget First"),
        "broad_variety_first": base_profile(planningPriority="Variety First", varietyPreference="High"),
        "budget_2000": base_profile(weeklyBudgetPhp=2000, planningPriority="Budget First"),
        "budget_2500": base_profile(weeklyBudgetPhp=2500, planningPriority="Budget First"),
        "quick_30_minutes": base_profile(maxCookingTimeMinutes=30, planningPriority="Quick Prep"),
        "balanced_low_variety": base_profile(varietyPreference="Low"),
        "general_health_no_symptoms": base_profile(goal="General Health", symptoms=[]),
        "weight_loss_only": base_profile(goal="Weight Loss", symptoms=["Weight gain"]),
        "vegetarian": base_profile(dietaryRestrictions=["Vegetarian"]),
        "vegetarian_high_variety": base_profile(dietaryRestrictions=["Vegetarian"], varietyPreference="High", planningPriority="Variety First"),
        "vegetarian_soy_allergy": base_profile(dietaryRestrictions=["Vegetarian"], allergies=["soy"]),
        "vegetarian_peanut_allergy": base_profile(dietaryRestrictions=["Vegetarian"], allergies=["peanuts"]),
        "vegetarian_gluten_allergy": base_profile(dietaryRestrictions=["Vegetarian"], allergies=["gluten/wheat"]),
        "vegetarian_lactose": base_profile(dietaryRestrictions=["Vegetarian", "Lactose Intolerant"]),
        "vegetarian_budget_2500": base_profile(dietaryRestrictions=["Vegetarian"], weeklyBudgetPhp=2500, planningPriority="Budget First"),
        "pescatarian": base_profile(dietaryRestrictions=["Pescatarian"]),
        "pescatarian_high_variety": base_profile(dietaryRestrictions=["Pescatarian"], varietyPreference="High", planningPriority="Variety First"),
        "pescatarian_shellfish_allergy": base_profile(dietaryRestrictions=["Pescatarian"], allergies=["shellfish"]),
        "pescatarian_egg_allergy": base_profile(dietaryRestrictions=["Pescatarian"], allergies=["egg"]),
        "pescatarian_lactose": base_profile(dietaryRestrictions=["Pescatarian", "Lactose Intolerant"]),
        "no_pork": base_profile(dietaryRestrictions=["No Pork"]),
        "no_pork_lactose": base_profile(dietaryRestrictions=["No Pork", "Lactose Intolerant"]),
        "no_pork_egg_allergy": base_profile(dietaryRestrictions=["No Pork"], allergies=["egg"]),
        "no_pork_fish_allergy": base_profile(dietaryRestrictions=["No Pork"], allergies=["fish"]),
        "no_pork_shellfish_allergy": base_profile(dietaryRestrictions=["No Pork"], allergies=["shellfish"]),
        "no_beef": base_profile(dietaryRestrictions=["No Beef"]),
        "no_pork_no_beef": base_profile(dietaryRestrictions=["No Pork", "No Beef"]),
        "no_pork_no_beef_fish_allergy": base_profile(dietaryRestrictions=["No Pork", "No Beef"], allergies=["fish"]),
        "no_pork_no_beef_shellfish_allergy": base_profile(dietaryRestrictions=["No Pork", "No Beef"], allergies=["shellfish"]),
        "egg_allergy": base_profile(allergies=["egg"]),
        "fish_allergy": base_profile(allergies=["fish"]),
        "shellfish_allergy": base_profile(allergies=["shellfish"]),
        "fish_shellfish_allergy": base_profile(allergies=["fish", "shellfish"]),
        "dairy_allergy": base_profile(allergies=["dairy"]),
        "soy_allergy": base_profile(allergies=["soy"]),
        "gluten_wheat_allergy": base_profile(allergies=["gluten/wheat"]),
        "peanut_allergy": base_profile(allergies=["peanuts"]),
        "tree_nut_allergy": base_profile(allergies=["tree nuts"]),
        "custom_chicken_allergy": base_profile(allergies=["chicken"]),
        "custom_beef_allergy": base_profile(allergies=["beef"]),
        "custom_pork_allergy": base_profile(allergies=["pork"]),
        "custom_tuna_allergy": base_profile(allergies=["tuna"]),
        "custom_shrimp_allergy": base_profile(allergies=["shrimp"]),
        "strict_no_pork_lactose_egg_fish": base_profile(dietaryRestrictions=["No Pork", "Lactose Intolerant"], allergies=["egg", "fish"]),
        "strict_no_pork_lactose_egg_fish_high_variety": base_profile(
            dietaryRestrictions=["No Pork", "Lactose Intolerant"],
            allergies=["egg", "fish"],
            varietyPreference="High",
            planningPriority="Variety First",
        ),
        "no_pork_lactose_soy_allergy": base_profile(dietaryRestrictions=["No Pork", "Lactose Intolerant"], allergies=["soy"]),
        "no_beef_lactose_shellfish_allergy": base_profile(dietaryRestrictions=["No Beef", "Lactose Intolerant"], allergies=["shellfish"]),
        "custom_chicken_soy_allergy": base_profile(allergies=["chicken", "soy"]),
    }
    if len(scenarios) != 50:
        raise AssertionError(f"extended_50_scenario_profiles must contain 50 scenarios, got {len(scenarios)}")
    return scenarios


def scenario_profiles(suite: str = "core") -> Dict[str, UserProfile]:
    if str(suite or "").strip().lower() == "extended50":
        return extended_50_scenario_profiles()
    return core_scenario_profiles()


def count_by_meal_type(recipes: Iterable[Dict[str, Any]]) -> Dict[str, int]:
    counts: Counter[str] = Counter()
    for recipe in recipes:
        counts[str(recipe.get("mealType") or "Universal")] += 1
    return dict(sorted(counts.items()))


def selected_ids_from_plan(plan: Any) -> List[str]:
    ids: List[str] = []
    for day in plan or []:
        for meal in getattr(day, "meals", []) or []:
            ids.append(str(getattr(meal, "recipeId", "") or ""))
    return ids


def slot_repeat_metrics(plan: Any) -> tuple[Dict[str, int], Dict[str, int]]:
    by_slot: Dict[str, Counter[str]] = {}
    for day in plan or []:
        for meal in getattr(day, "meals", []) or []:
            label = str(getattr(meal, "mealLabel", "") or "Unknown")
            recipe_id = str(getattr(meal, "recipeId", "") or "")
            by_slot.setdefault(label, Counter())[recipe_id] += 1
    slot_unique = {label: len(counter) for label, counter in sorted(by_slot.items())}
    slot_max = {label: max(counter.values(), default=0) for label, counter in sorted(by_slot.items())}
    return slot_unique, slot_max


def summarize_selected_titles(selected_ids: List[str], recipe_by_id: Dict[str, Dict[str, Any]]) -> Dict[str, int]:
    counts: Counter[str] = Counter()
    for recipe_id in selected_ids:
        title = str(recipe_by_id.get(recipe_id, {}).get("title") or recipe_id)
        counts[title] += 1
    return dict(sorted((title, count) for title, count in counts.items() if count > 1))


def run_scenario(
    name: str,
    profile: UserProfile,
    recipes: List[Dict[str, Any]],
    policy: Dict[str, Any],
    *,
    days: int,
    meals_per_day: int,
) -> Dict[str, Any]:
    telemetry: Dict[str, Any] = {}
    request = GeneratePlanRequest(profile=profile, days=days, mealsPerDay=meals_per_day)
    scenario_recipes = deepcopy(recipes)
    recipe_by_id = {str(recipe.get("id") or ""): recipe for recipe in scenario_recipes}
    plan, message, explanation = meal_planner.solve_meal_plan(
        request,
        scenario_recipes,
        policy=deepcopy(policy),
        telemetry_out=telemetry,
    )
    selected_ids = selected_ids_from_plan(plan)
    selected_counts = Counter(selected_ids)
    slot_unique, slot_max = slot_repeat_metrics(plan)
    stage1_diag = telemetry.get("stage1_diag") if isinstance(telemetry.get("stage1_diag"), dict) else {}
    solve_pairs = telemetry.get("solve_pair_diagnostics") if isinstance(telemetry.get("solve_pair_diagnostics"), list) else []
    final_attempt = solve_pairs[-1] if solve_pairs else {}
    family_counts = {}
    if isinstance(explanation, dict):
        family_counts = dict(explanation.get("ingredientFamilyCounts") or {})
    dominant_count = int((explanation or {}).get("dominantIngredientFamilyCount") or 0) if isinstance(explanation, dict) else 0
    selected_slots = len(selected_ids)
    return {
        "scenario": name,
        "status": "success" if plan is not None else "failed",
        "message": message,
        "days": days,
        "mealsPerDay": meals_per_day,
        "slotCount": days * meals_per_day,
        "catalogRecipeCount": len(recipes),
        "catalogMealTypeCounts": count_by_meal_type(recipes),
        "restrictionCount": len(profile.dietaryRestrictions or []),
        "allergyCount": len(profile.allergies or []),
        "dietaryRestrictions": list(profile.dietaryRestrictions or []),
        "allergies": list(profile.allergies or []),
        "budgetWeeklyPhp": profile.weeklyBudgetPhp,
        "safeRecipeCountPrePricing": stage1_diag.get("safe_recipe_count_pre_pricing"),
        "restrictedNutritionAnchorCountSafe": stage1_diag.get("restricted_nutrition_anchor_count_safe"),
        "candidateCountPre": telemetry.get("candidate_count_pre"),
        "candidateCountPost": telemetry.get("candidate_count_post"),
        "safeIngredientFamilyCounts": stage1_diag.get("safe_ingredient_family_counts") or {},
        "candidateIngredientFamilyCounts": stage1_diag.get("candidate_ingredient_family_counts") or {},
        "candidateDuplicateTitleCounts": stage1_diag.get("candidate_duplicate_title_counts") or {},
        "selectedRecipeIds": selected_ids,
        "selectedRecipeCount": selected_slots,
        "selectedUniqueRecipeCount": len(selected_counts),
        "selectedMaxRecipeRepeatCount": max(selected_counts.values(), default=0),
        "selectedRepeatedRecipeCount": sum(1 for count in selected_counts.values() if count > 1),
        "selectedRepeatedTitles": summarize_selected_titles(selected_ids, recipe_by_id),
        "slotUniqueCounts": slot_unique,
        "slotMaxRepeatCounts": slot_max,
        "selectedIngredientFamilyCounts": family_counts,
        "dominantIngredientFamily": (explanation or {}).get("dominantIngredientFamily") if isinstance(explanation, dict) else None,
        "dominantIngredientFamilyCount": dominant_count,
        "dominantIngredientFamilyShareOfSlots": round(dominant_count / selected_slots, 3) if selected_slots else 0.0,
        "estimatedWeeklyCostPhp": (explanation or {}).get("estimatedWeeklyCost") if isinstance(explanation, dict) else None,
        "confidenceScore": (explanation or {}).get("confidenceScore") if isinstance(explanation, dict) else None,
        "maxPerWeekUsed": (explanation or {}).get("maxPerWeek") if isinstance(explanation, dict) else None,
        "repeatSequence": stage1_diag.get("repeat_sequence") or [],
        "solvePairStatuses": [
            {
                "status": item.get("status"),
                "maxPerWeek": item.get("maxPerWeek"),
                "sameSlotConsecutiveRepeatBlocked": item.get("sameSlotConsecutiveRepeatBlocked"),
                "ingredientFamilyRepeatCaps": item.get("ingredientFamilyRepeatCaps") or {},
                "ingredientFamilyRepeatCapsEnforced": item.get("ingredientFamilyRepeatCapsEnforced"),
            }
            for item in solve_pairs
            if isinstance(item, dict)
        ],
        "finalSolvePair": final_attempt,
        "exclusionDetailCounts": stage1_diag.get("exclusion_detail_counts") or {},
        "nutritionFeasibility": stage1_diag.get("nutrition_feasibility") or {},
    }


def apply_audit_requirements(summary: Dict[str, Any], *, max_repeat_limit: int) -> Dict[str, Any]:
    violations: List[str] = []
    if summary.get("status") != "success":
        violations.append("plan_generation_failed")
    if int(summary.get("selectedRecipeCount") or 0) != int(summary.get("slotCount") or 0):
        violations.append("slot_count_not_filled")
    if int(summary.get("selectedMaxRecipeRepeatCount") or 0) > int(max_repeat_limit):
        violations.append("repeat_limit_exceeded")
    summary["auditMaxRepeatLimit"] = int(max_repeat_limit)
    summary["auditPass"] = not violations
    summary["auditViolations"] = violations
    return summary


def csv_row(summary: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "scenario": summary["scenario"],
        "auditPass": summary.get("auditPass"),
        "auditViolations": json.dumps(summary.get("auditViolations") or []),
        "auditMaxRepeatLimit": summary.get("auditMaxRepeatLimit"),
        "status": summary["status"],
        "message": summary["message"],
        "catalogRecipeCount": summary["catalogRecipeCount"],
        "safeRecipeCountPrePricing": summary["safeRecipeCountPrePricing"],
        "restrictedNutritionAnchorCountSafe": summary["restrictedNutritionAnchorCountSafe"],
        "candidateCountPre": summary["candidateCountPre"],
        "candidateCountPost": summary["candidateCountPost"],
        "selectedUniqueRecipeCount": summary["selectedUniqueRecipeCount"],
        "selectedMaxRecipeRepeatCount": summary["selectedMaxRecipeRepeatCount"],
        "selectedRepeatedRecipeCount": summary["selectedRepeatedRecipeCount"],
        "dominantIngredientFamily": summary["dominantIngredientFamily"],
        "dominantIngredientFamilyCount": summary["dominantIngredientFamilyCount"],
        "dominantIngredientFamilyShareOfSlots": summary["dominantIngredientFamilyShareOfSlots"],
        "maxPerWeekUsed": summary["maxPerWeekUsed"],
        "estimatedWeeklyCostPhp": summary["estimatedWeeklyCostPhp"],
        "confidenceScore": summary["confidenceScore"],
        "slotUniqueCounts": json.dumps(summary["slotUniqueCounts"], sort_keys=True),
        "selectedIngredientFamilyCounts": json.dumps(summary["selectedIngredientFamilyCounts"], sort_keys=True),
        "repeatSequence": json.dumps(summary["repeatSequence"]),
        "dietaryRestrictions": json.dumps(summary["dietaryRestrictions"]),
        "allergies": json.dumps(summary["allergies"]),
    }


def write_outputs(
    summaries: List[Dict[str, Any]],
    json_path: Path,
    csv_path: Path,
    *,
    suite: str,
    max_repeat_limit: int,
) -> None:
    json_path.parent.mkdir(parents=True, exist_ok=True)
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "catalog": {
            "path": str(DEFAULT_CATALOG),
            "recipeCount": summaries[0]["catalogRecipeCount"] if summaries else 0,
            "mealTypeCounts": summaries[0]["catalogMealTypeCounts"] if summaries else {},
        },
        "suite": str(suite),
        "scenarioCount": len(summaries),
        "passCount": sum(1 for summary in summaries if summary.get("auditPass")),
        "failCount": sum(1 for summary in summaries if not summary.get("auditPass")),
        "requirements": {
            "slotCountMustBeFilled": True,
            "maxRecipeRepeatCount": int(max_repeat_limit),
        },
        "scenarios": summaries,
    }
    json_path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
    rows = [csv_row(summary) for summary in summaries]
    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()) if rows else [])
        if rows:
            writer.writeheader()
            writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--json-out", type=Path, default=None)
    parser.add_argument("--csv-out", type=Path, default=None)
    parser.add_argument("--suite", choices=["core", "extended50"], default="core")
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--meals-per-day", type=int, default=3)
    parser.add_argument("--max-repeat-limit", type=int, default=3)
    parser.add_argument("--scenario", action="append", help="Run only the named scenario. Can be repeated.")
    parser.add_argument("--no-write", action="store_true", help="Print summaries without writing JSON/CSV files.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    recipes = load_catalog(args.catalog)
    scenarios = scenario_profiles(args.suite)
    selected_names = set(args.scenario or scenarios.keys())
    unknown = selected_names - set(scenarios)
    if unknown:
        raise SystemExit(f"Unknown scenario(s): {', '.join(sorted(unknown))}")

    policy = production_policy()
    summaries = [
        apply_audit_requirements(
            run_scenario(
                name,
                scenarios[name],
                recipes,
                policy,
                days=int(args.days),
                meals_per_day=int(args.meals_per_day),
            ),
            max_repeat_limit=int(args.max_repeat_limit),
        )
        for name in scenarios
        if name in selected_names
    ]
    json_out = args.json_out or (DEFAULT_50_JSON if args.suite == "extended50" else DEFAULT_JSON)
    csv_out = args.csv_out or (DEFAULT_50_CSV if args.suite == "extended50" else DEFAULT_CSV)
    if not args.no_write:
        write_outputs(
            summaries,
            json_out,
            csv_out,
            suite=str(args.suite),
            max_repeat_limit=int(args.max_repeat_limit),
        )

    print(json.dumps({
        "catalogRecipeCount": len(recipes),
        "suite": str(args.suite),
        "scenarioCount": len(summaries),
        "passCount": sum(1 for item in summaries if item.get("auditPass")),
        "failCount": sum(1 for item in summaries if not item.get("auditPass")),
        "jsonOut": None if args.no_write else str(json_out),
        "csvOut": None if args.no_write else str(csv_out),
        "summary": [
            {
                "scenario": item["scenario"],
                "auditPass": item.get("auditPass"),
                "violations": item.get("auditViolations"),
                "status": item["status"],
                "safe": item["safeRecipeCountPrePricing"],
                "candidates": item["candidateCountPost"],
                "unique": item["selectedUniqueRecipeCount"],
                "maxRepeat": item["selectedMaxRecipeRepeatCount"],
                "dominantFamily": item["dominantIngredientFamily"],
                "dominantFamilyCount": item["dominantIngredientFamilyCount"],
            }
            for item in summaries
        ],
    }, indent=2, sort_keys=True))
    return 0 if all(item.get("auditPass") for item in summaries) else 1


if __name__ == "__main__":
    raise SystemExit(main())
