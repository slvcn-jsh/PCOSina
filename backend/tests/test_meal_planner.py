import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from domain.models import UserProfile
from services import meal_planner
from services.ml_ranker import Stage1MLRanker


MODEL_ARTIFACT = ROOT.parent / "ml" / "offline_training" / "artifacts" / "model_v1" / "lightgbm_v1_model.txt"
METRICS_ARTIFACT = ROOT.parent / "ml" / "offline_training" / "artifacts" / "model_v1" / "training_metrics.json"


def _require_lightgbm() -> None:
    if importlib.util.find_spec("lightgbm") is None:
        pytest.skip("lightgbm not installed in this environment")


def test_macro_ratios_moderate():
    assert meal_planner.macro_ratios("Moderate") == (0.28, 0.35, 0.37)


def test_macro_ratios_severe():
    assert meal_planner.macro_ratios("Severe") == (0.30, 0.30, 0.40)


def test_resolve_budget_weekly_prefers_weekly_php():
    profile = UserProfile(weeklyBudgetPhp=3000, budgetWeekly=2000, budgetMonthly=8000)
    assert meal_planner.resolve_budget_weekly(profile) == 3000.0


def test_allergy_filter_blocks_recipe():
    profile = UserProfile(allergies=["peanut"])
    recipes = [
        {
            "id": "r1",
            "title": "Peanut Dish",
            "mealType": "Lunch",
            "calories": 500,
            "proteinGrams": 20,
            "carbsGrams": 40,
            "fatsGrams": 15,
            "fiberGrams": 5,
            "ingredients": [{"name": "peanut oil", "quantity": "1 tbsp"}],
        }
    ]
    buckets = meal_planner.shortlist_candidates(profile, recipes)
    assert all(len(v) == 0 for v in buckets.values())


def test_infer_allowed_meals():
    assert meal_planner.infer_allowed_meals("Breakfast") == ["Breakfast"]
    assert set(meal_planner.infer_allowed_meals("Universal")) == set(meal_planner.ALL_SLOT_LABELS)
    assert set(meal_planner.infer_allowed_meals("Snack")) == set(meal_planner.SNACK_LABELS)
    assert set(meal_planner.infer_allowed_meals("Merienda")) == set(meal_planner.SNACK_LABELS)


def test_build_swap_candidates_blocks_current_recipe_and_repetition_overflow():
    profile = UserProfile(
        varietyPreference="High",
        pantryItems=["egg", "oats", "banana"],
        maxCookingTimeMinutes=45,
    )
    recipes = [
        {
            "id": "b_current",
            "title": "Current Breakfast",
            "mealType": "Breakfast",
            "calories": 430,
            "proteinGrams": 24,
            "carbsGrams": 36,
            "fatsGrams": 12,
            "fiberGrams": 6,
            "minutes": 10,
            "ingredients": [{"name": "egg", "quantity": "2 pcs"}],
            "tags": [],
        },
        {
            "id": "b_repeat",
            "title": "Already Repeated Oats",
            "mealType": "Breakfast",
            "calories": 410,
            "proteinGrams": 20,
            "carbsGrams": 41,
            "fatsGrams": 10,
            "fiberGrams": 8,
            "minutes": 12,
            "ingredients": [{"name": "oats", "quantity": "1 cup"}],
            "tags": [],
        },
        {
            "id": "b_safe",
            "title": "Safe Breakfast Swap",
            "mealType": "Breakfast",
            "calories": 440,
            "proteinGrams": 22,
            "carbsGrams": 39,
            "fatsGrams": 13,
            "fiberGrams": 7,
            "minutes": 15,
            "ingredients": [{"name": "banana", "quantity": "1 pc"}],
            "tags": [],
        },
    ]

    swaps = meal_planner.build_swap_candidates(
        profile,
        recipes,
        meal_label="Breakfast",
        current_recipe_id="b_current",
        active_recipe_ids=["b_current", "b_repeat", "b_repeat"],
        limit=10,
    )

    assert [recipe["id"] for recipe in swaps] == ["b_safe"]


def test_build_swap_candidates_respects_budget_and_restrictions():
    profile = UserProfile(
        weeklyBudgetPhp=80,
        dietaryRestrictions=["No Pork"],
        pantryItems=["egg", "rice"],
        maxCookingTimeMinutes=45,
    )
    recipes = [
        {
            "id": "l_current",
            "title": "Current Lunch",
            "mealType": "Lunch",
            "calories": 520,
            "proteinGrams": 25,
            "carbsGrams": 46,
            "fatsGrams": 16,
            "fiberGrams": 7,
            "minutes": 20,
            "ingredients": [{"name": "egg", "quantity": "2 pcs"}],
            "tags": [],
        },
        {
            "id": "l_pork",
            "title": "Pork Lunch",
            "mealType": "Lunch",
            "calories": 540,
            "proteinGrams": 28,
            "carbsGrams": 44,
            "fatsGrams": 20,
            "fiberGrams": 6,
            "minutes": 25,
            "ingredients": [{"name": "pork", "quantity": "200 g"}],
            "tags": [],
        },
        {
            "id": "l_expensive",
            "title": "Expensive Lunch",
            "mealType": "Lunch",
            "calories": 560,
            "proteinGrams": 30,
            "carbsGrams": 42,
            "fatsGrams": 18,
            "fiberGrams": 6,
            "minutes": 25,
            "ingredients": [{"name": "shrimp", "quantity": "2 kg"}],
            "tags": [],
        },
        {
            "id": "l_safe",
            "title": "Budget Safe Lunch",
            "mealType": "Lunch",
            "calories": 510,
            "proteinGrams": 24,
            "carbsGrams": 47,
            "fatsGrams": 14,
            "fiberGrams": 7,
            "minutes": 18,
            "ingredients": [{"name": "rice", "quantity": "1 cup"}],
            "tags": [],
        },
    ]

    swaps = meal_planner.build_swap_candidates(
        profile,
        recipes,
        meal_label="Lunch",
        current_recipe_id="l_current",
        active_recipe_ids=["l_current"],
        limit=10,
    )

    assert [recipe["id"] for recipe in swaps] == ["l_safe"]


def test_solve_meal_plan_emits_telemetry_snapshot():
    profile = UserProfile(
        displayName="Telemetry",
        age=25,
        heightCm=160,
        weightKg=60,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=["egg", "onion"],
        maxCookingTimeMinutes=60,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=1, mealsPerDay=3)
    recipes = [
        {
            "id": "b1",
            "title": "Breakfast One",
            "mealType": "Breakfast",
            "calories": 500,
            "proteinGrams": 30,
            "carbsGrams": 45,
            "fatsGrams": 16,
            "fiberGrams": 6,
            "minutes": 20,
            "ingredients": [{"name": "egg", "quantity": "2 pcs"}],
            "tags": [],
        },
        {
            "id": "l1",
            "title": "Lunch One",
            "mealType": "Lunch",
            "calories": 550,
            "proteinGrams": 28,
            "carbsGrams": 50,
            "fatsGrams": 18,
            "fiberGrams": 7,
            "minutes": 25,
            "ingredients": [{"name": "onion", "quantity": "1 pc"}],
            "tags": [],
        },
        {
            "id": "d1",
            "title": "Dinner One",
            "mealType": "Dinner",
            "calories": 520,
            "proteinGrams": 27,
            "carbsGrams": 48,
            "fatsGrams": 17,
            "fiberGrams": 6,
            "minutes": 30,
            "ingredients": [{"name": "tomato", "quantity": "1 pc"}],
            "tags": [],
        },
    ]
    policy = {
        "planning": {
            "planning_horizon_days": 1,
            "meals_per_day": 3,
            "recipe_repeat_limits": [3],
        },
        "stage1": {
            "max_candidates_per_slot": 10,
            "ranking_cutoff": 1.0,
            "similarity_threshold": 1.0,
            "restricted_shortlist_multiplier": 1.0,
            "budget_keep_min_count": 1,
            "budget_keep_min_ratio": 1.0,
            "pantry_match_threshold": 0,
            "minimum_candidates_required": 1,
            "pool_cap_top_share": 1.0,
            "ML_shadow_enabled": True,
            "ML_canary_enabled": False,
            "ML_score_weight": 0.15,
            "ML_score_cap": 0.3,
        },
        "solver": {
            "solver_time_limit_seconds": 1.0,
            "solver_max_seconds": 2.0,
            "total_solver_seconds": 3.0,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }
    telemetry = {}
    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out=telemetry)
    assert msg == "Success"
    assert plan is not None
    assert explanation is not None
    assert telemetry.get("candidate_count_pre", 0) > 0
    assert telemetry.get("candidate_count_post", 0) > 0
    assert isinstance(telemetry.get("stage1_candidates"), list)
    assert isinstance(telemetry.get("selected_recipe_ids"), list)
    assert telemetry.get("ranking_strategy") == "stage1_heuristic_shadow_only"


def test_solve_meal_plan_canary_applies_ml_ranking_strategy():
    profile = UserProfile(
        displayName="CanaryUser",
        age=29,
        heightCm=162,
        weightKg=63,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=["egg", "onion"],
        maxCookingTimeMinutes=60,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=1, mealsPerDay=3)
    recipes = [
        {
            "id": "b1",
            "title": "Breakfast One",
            "mealType": "Breakfast",
            "calories": 500,
            "proteinGrams": 30,
            "carbsGrams": 45,
            "fatsGrams": 16,
            "fiberGrams": 6,
            "minutes": 20,
            "ingredients": [{"name": "egg", "quantity": "2 pcs"}],
            "tags": [],
        },
        {
            "id": "l1",
            "title": "Lunch One",
            "mealType": "Lunch",
            "calories": 550,
            "proteinGrams": 28,
            "carbsGrams": 50,
            "fatsGrams": 18,
            "fiberGrams": 7,
            "minutes": 25,
            "ingredients": [{"name": "onion", "quantity": "1 pc"}],
            "tags": [],
        },
        {
            "id": "d1",
            "title": "Dinner One",
            "mealType": "Dinner",
            "calories": 520,
            "proteinGrams": 27,
            "carbsGrams": 48,
            "fatsGrams": 17,
            "fiberGrams": 6,
            "minutes": 30,
            "ingredients": [{"name": "tomato", "quantity": "1 pc"}],
            "tags": [],
        },
    ]
    policy = {
        "planning": {
            "planning_horizon_days": 1,
            "meals_per_day": 3,
            "recipe_repeat_limits": [3],
        },
        "stage1": {
            "max_candidates_per_slot": 10,
            "ranking_cutoff": 1.0,
            "similarity_threshold": 1.0,
            "restricted_shortlist_multiplier": 1.0,
            "budget_keep_min_count": 1,
            "budget_keep_min_ratio": 1.0,
            "pantry_match_threshold": 0,
            "minimum_candidates_required": 1,
            "pool_cap_top_share": 1.0,
            "ML_shadow_enabled": True,
            "ML_canary_enabled": True,
            "ML_score_weight": 0.15,
            "ML_score_cap": 0.3,
        },
        "sre": {
            "canary_cohort_percent": 100.0,
        },
        "solver": {
            "solver_time_limit_seconds": 1.0,
            "solver_max_seconds": 2.0,
            "total_solver_seconds": 3.0,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }
    telemetry = {}
    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out=telemetry)
    assert msg == "Success"
    assert plan is not None
    assert explanation is not None
    assert telemetry.get("ranking_strategy") == "stage1_ml_canary_plus_heuristic"


def test_solve_meal_plan_shadow_uses_file_backed_ranker_artifacts(monkeypatch):
    _require_lightgbm()
    assert MODEL_ARTIFACT.exists()
    assert METRICS_ARTIFACT.exists()
    monkeypatch.setenv("PCOSINA_ML_MODEL_PATH", str(MODEL_ARTIFACT))
    monkeypatch.setenv("PCOSINA_ML_METRICS_PATH", str(METRICS_ARTIFACT))
    monkeypatch.setattr(meal_planner, "get_stage1_ranker", lambda: Stage1MLRanker())

    profile = UserProfile(
        displayName="ShadowArtifactUser",
        age=31,
        heightCm=163,
        weightKg=64,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=["egg", "onion", "rice"],
        maxCookingTimeMinutes=60,
        weeklyBudgetPhp=2800,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=1, mealsPerDay=3)
    recipes = [
        {
            "id": "b1",
            "title": "Breakfast One",
            "mealType": "Breakfast",
            "calories": 460,
            "proteinGrams": 24,
            "carbsGrams": 41,
            "fatsGrams": 14,
            "fiberGrams": 5,
            "minutes": 15,
            "ingredients": [{"name": "egg", "quantity": "2 pcs"}],
            "tags": [],
        },
        {
            "id": "l1",
            "title": "Lunch One",
            "mealType": "Lunch",
            "calories": 540,
            "proteinGrams": 30,
            "carbsGrams": 49,
            "fatsGrams": 17,
            "fiberGrams": 7,
            "minutes": 25,
            "ingredients": [{"name": "rice", "quantity": "1 cup"}],
            "tags": [],
        },
        {
            "id": "d1",
            "title": "Dinner One",
            "mealType": "Dinner",
            "calories": 515,
            "proteinGrams": 27,
            "carbsGrams": 45,
            "fatsGrams": 16,
            "fiberGrams": 6,
            "minutes": 30,
            "ingredients": [{"name": "fish", "quantity": "1 fillet"}],
            "tags": [],
        },
    ]
    policy = {
        "planning": {
            "planning_horizon_days": 1,
            "meals_per_day": 3,
            "recipe_repeat_limits": [3],
        },
        "stage1": {
            "max_candidates_per_slot": 10,
            "ranking_cutoff": 1.0,
            "similarity_threshold": 1.0,
            "restricted_shortlist_multiplier": 1.0,
            "budget_keep_min_count": 1,
            "budget_keep_min_ratio": 1.0,
            "pantry_match_threshold": 0,
            "minimum_candidates_required": 1,
            "pool_cap_top_share": 1.0,
            "ML_shadow_enabled": True,
            "ML_canary_enabled": False,
            "ML_score_weight": 0.15,
            "ML_score_cap": 0.3,
        },
        "solver": {
            "solver_time_limit_seconds": 1.0,
            "solver_max_seconds": 2.0,
            "total_solver_seconds": 3.0,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }

    telemetry = {}
    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out=telemetry)

    assert msg == "Success"
    assert plan is not None
    assert explanation is not None
    assert telemetry.get("ranking_strategy") == "stage1_heuristic_shadow_only"
    assert telemetry.get("ml_model_version") == "lightgbm_stage1_ranker_v1"
    stage1_candidates = telemetry.get("stage1_candidates") or []
    assert stage1_candidates
    assert any(float(candidate.get("model_score") or 0.0) > 0.0 for candidate in stage1_candidates)


def test_solve_meal_plan_with_snack_slot_does_not_relax_breakfast_lunch_dinner_constraints():
    profile = UserProfile(
        displayName="SnackUser",
        age=28,
        heightCm=161,
        weightKg=61,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=["egg", "rice"],
        maxCookingTimeMinutes=60,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=1, mealsPerDay=4)
    recipes = [
        {
            "id": "b1",
            "title": "Breakfast One",
            "mealType": "Breakfast",
            "calories": 420,
            "proteinGrams": 20,
            "carbsGrams": 40,
            "fatsGrams": 12,
            "fiberGrams": 4,
            "minutes": 15,
            "ingredients": [{"name": "egg", "quantity": "2 pcs"}],
            "tags": [],
        },
        {
            "id": "l1",
            "title": "Lunch One",
            "mealType": "Lunch",
            "calories": 560,
            "proteinGrams": 28,
            "carbsGrams": 52,
            "fatsGrams": 17,
            "fiberGrams": 6,
            "minutes": 25,
            "ingredients": [{"name": "rice", "quantity": "1 cup"}],
            "tags": [],
        },
        {
            "id": "d1",
            "title": "Dinner One",
            "mealType": "Dinner",
            "calories": 510,
            "proteinGrams": 26,
            "carbsGrams": 45,
            "fatsGrams": 16,
            "fiberGrams": 5,
            "minutes": 30,
            "ingredients": [{"name": "fish", "quantity": "1 fillet"}],
            "tags": [],
        },
    ]
    policy = {
        "planning": {
            "planning_horizon_days": 1,
            "meals_per_day": 4,
            "recipe_repeat_limits": [2],
        },
        "stage1": {
            "max_candidates_per_slot": 10,
            "ranking_cutoff": 1.0,
            "similarity_threshold": 1.0,
            "restricted_shortlist_multiplier": 1.0,
            "budget_keep_min_count": 1,
            "budget_keep_min_ratio": 1.0,
            "pantry_match_threshold": 0,
            "minimum_candidates_required": 1,
            "pool_cap_top_share": 1.0,
            "ML_shadow_enabled": False,
            "ML_canary_enabled": False,
        },
        "solver": {
            "solver_time_limit_seconds": 0.5,
            "solver_max_seconds": 1.0,
            "total_solver_seconds": 1.5,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }

    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out={})

    assert plan is None
    assert msg != "Success"
