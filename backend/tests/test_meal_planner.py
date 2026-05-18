import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database
import price_catalog
from domain.models import UserProfile
from services import meal_planner
from services.ml_ranker import RankerState, Stage1MLRanker


MODEL_ARTIFACT = ROOT.parent / "ml" / "offline_training" / "artifacts" / "model_v1" / "lightgbm_v1_model.txt"
METRICS_ARTIFACT = ROOT.parent / "ml" / "offline_training" / "artifacts" / "model_v1" / "training_metrics.json"


def _require_lightgbm() -> None:
    if importlib.util.find_spec("lightgbm") is None:
        pytest.skip("lightgbm not installed in this environment")


def _recipe(
    recipe_id: str,
    title: str,
    meal_type: str,
    *,
    calories: int = 500,
    protein: int = 24,
    carbs: int = 40,
    fats: int = 15,
    fiber: int = 7,
    minutes: int = 20,
    ingredients: list[dict] | None = None,
    sugar: int | None = None,
) -> dict:
    return {
        "id": recipe_id,
        "title": title,
        "mealType": meal_type,
        "calories": calories,
        "proteinGrams": protein,
        "carbsGrams": carbs,
        "fatsGrams": fats,
        "fiberGrams": fiber,
        "minutes": minutes,
        "ingredients": ingredients or [{"name": "egg", "quantity": "2 pcs"}],
        "tags": [],
        "sugarGrams": sugar,
    }


def test_macro_ratios_moderate():
    assert meal_planner.macro_ratios("Moderate") == (0.28, 0.35, 0.37)


def test_macro_ratios_severe():
    assert meal_planner.macro_ratios("Severe") == (0.30, 0.30, 0.40)


def test_stage1_pricing_uses_request_scoped_market_multiplier_cache(monkeypatch):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(
        database,
        "list_market_multipliers_for_month",
        lambda month_index: (_ for _ in ()).throw(RuntimeError("preload unavailable")),
    )
    calls: list[tuple[str, int]] = []

    def fake_market_multiplier(category: str, month_index: int) -> float:
        calls.append((category, month_index))
        return 1.0

    monkeypatch.setattr(database, "get_market_multiplier", fake_market_multiplier)
    profile = UserProfile(
        displayName="Pricing Cache",
        age=28,
        heightCm=160,
        weightKg=62,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=[],
        maxCookingTimeMinutes=60,
    )
    recipes = [
        _recipe(
            f"r{i}",
            f"Recipe {i}",
            ["Breakfast", "Lunch", "Dinner"][i % 3],
            ingredients=[
                {"name": "tomato", "quantity": "1 kg"},
                {"name": "onion", "quantity": "1 kg"},
                {"name": "chicken", "quantity": "1 kg"},
                {"name": "tilapia", "quantity": "1 kg"},
            ],
        )
        for i in range(80)
    ]
    policy = {
        "stage1": {
            "ML_shadow_enabled": False,
            "ML_canary_enabled": False,
            "max_candidates_per_slot": 80,
            "ranking_cutoff": 1.0,
            "similarity_threshold": 1.0,
            "budget_keep_min_count": 1,
            "budget_keep_min_ratio": 1.0,
            "minimum_candidates_required": 1,
        }
    }
    diagnostics: dict = {}

    buckets = meal_planner.shortlist_candidates(profile, recipes, policy=policy, stage1_diag=diagnostics)
    selected_count = sum(len(v) for v in buckets.values())
    pricing = diagnostics["pricing_diagnostics"]

    assert selected_count > 0
    assert pricing["ingredientPriceEstimateCount"] == 320
    assert pricing["ingredientPriceCacheHits"] >= 300
    assert pricing["distinctIngredientPriceKeys"] == 4
    assert pricing["marketMultiplierDbCalls"] <= 3
    assert pricing["marketMultiplierCacheHits"] >= 2
    assert len(calls) == 2


def test_estimate_cost_uses_request_local_recipe_cache(monkeypatch):
    calls = 0

    def fake_estimate_recipe_cost(ingredients, pricing_context=None):
        nonlocal calls
        calls += 1
        return 123

    monkeypatch.setattr(meal_planner, "estimate_recipe_cost", fake_estimate_recipe_cost)
    recipe = _recipe("cached_recipe", "Cached Recipe", "Lunch")
    cost_cache = {}
    cost_cache_stats = {"recipeCostCacheHits": 0, "recipeCostCacheMisses": 0}
    pricing_context = price_catalog.create_pricing_context(month_index=5)

    first = meal_planner.estimate_cost(
        recipe,
        household_size=2,
        pricing_context=pricing_context,
        cost_cache=cost_cache,
        cost_cache_stats=cost_cache_stats,
    )
    second = meal_planner.estimate_cost(
        recipe,
        household_size=2,
        pricing_context=pricing_context,
        cost_cache=cost_cache,
        cost_cache_stats=cost_cache_stats,
    )

    assert first == second == 246
    assert calls == 1
    assert cost_cache_stats["recipeCostCacheMisses"] == 1
    assert cost_cache_stats["recipeCostCacheHits"] == 1


def test_pre_pricing_prunes_broad_profile_before_cost_estimation(monkeypatch):
    cost_calls = 0

    def fake_estimate_recipe_cost(ingredients, pricing_context=None):
        nonlocal cost_calls
        cost_calls += 1
        if pricing_context is not None:
            pricing_context.recipe_cost_estimates += 1
        return 100

    monkeypatch.setattr(meal_planner, "estimate_recipe_cost", fake_estimate_recipe_cost)
    profile = UserProfile(
        displayName="Broad Prepricing",
        age=28,
        heightCm=162,
        weightKg=64,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=[],
        maxCookingTimeMinutes=60,
        weeklyBudgetPhp=None,
    )
    recipes = [
        _recipe(
            f"broad_{i}",
            f"Broad Recipe {i}",
            ["Breakfast", "Lunch", "Dinner"][i % 3],
            calories=470 + (i % 80),
            protein=20 + (i % 16),
            fiber=4 + (i % 8),
            ingredients=[{"name": f"ingredient_{i}", "quantity": "1 cup"}],
        )
        for i in range(500)
    ]
    diagnostics: dict = {}
    policy = {
        "stage1": {
            "ML_shadow_enabled": False,
            "ML_canary_enabled": False,
            "max_candidates_per_slot": 64,
            "pre_pricing_candidate_cap": 120,
            "pre_pricing_bucket_reserve": 20,
            "similarity_threshold": 1.0,
            "minimum_candidates_required": 1,
        }
    }

    buckets = meal_planner.shortlist_candidates(profile, recipes, policy=policy, stage1_diag=diagnostics)

    assert sum(len(v) for v in buckets.values()) > 0
    assert diagnostics["safe_recipe_count_pre_pricing"] == 500
    assert diagnostics["pre_pricing_pruned"] is True
    assert diagnostics["pre_pricing_retained_count"] == 120
    assert diagnostics["cost_estimated_recipe_count"] == 120
    assert cost_calls == 120


def test_pre_pricing_keeps_allergy_filter_before_pricing(monkeypatch):
    priced_ingredient_names: list[str] = []

    def fake_estimate_recipe_cost(ingredients, pricing_context=None):
        for item in ingredients:
            priced_ingredient_names.append(str(item.get("name") if isinstance(item, dict) else item))
        if pricing_context is not None:
            pricing_context.recipe_cost_estimates += 1
        return 100

    monkeypatch.setattr(meal_planner, "estimate_recipe_cost", fake_estimate_recipe_cost)
    profile = UserProfile(
        displayName="Allergy Prepricing",
        allergies=["peanut"],
        maxCookingTimeMinutes=60,
    )
    recipes = [
        _recipe(
            f"unsafe_{i}",
            f"Unsafe {i}",
            "Lunch",
            ingredients=[{"name": "peanut sauce", "quantity": "1 tbsp"}],
        )
        for i in range(20)
    ] + [
        _recipe(
            f"safe_{i}",
            f"Safe {i}",
            "Lunch",
            ingredients=[{"name": f"safe ingredient {i}", "quantity": "1 cup"}],
        )
        for i in range(20)
    ]
    diagnostics: dict = {}
    policy = {
        "stage1": {
            "ML_shadow_enabled": False,
            "ML_canary_enabled": False,
            "pre_pricing_candidate_cap": 5,
            "pre_pricing_restricted_enabled": True,
            "similarity_threshold": 1.0,
            "minimum_candidates_required": 1,
        }
    }

    meal_planner.shortlist_candidates(profile, recipes, policy=policy, stage1_diag=diagnostics)

    assert diagnostics["exclusion_summary"]["allergy"] == 20
    assert diagnostics["safe_recipe_count_pre_pricing"] == 20
    assert diagnostics["pre_pricing_pruned"] is True
    assert all("peanut" not in name.lower() for name in priced_ingredient_names)


def test_broad_profile_generates_basic_seven_day_plan(monkeypatch):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(database, "list_market_multipliers_for_month", lambda month_index: {})
    monkeypatch.setattr(database, "get_market_multiplier", lambda category, month_index: 1.0)
    profile = UserProfile(
        displayName="Broad Profile",
        age=28,
        heightCm=162,
        weightKg=64,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=[],
        maxCookingTimeMinutes=60,
        weeklyBudgetPhp=None,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=7, mealsPerDay=3)
    recipes = [
        _recipe(
            "breakfast_base",
            "Balanced Breakfast",
            "Breakfast",
            calories=500,
            protein=28,
            carbs=55,
            fats=15,
            fiber=8,
            ingredients=[{"name": "egg", "quantity": "2 pcs"}, {"name": "tomato", "quantity": "1 pc"}],
        ),
        _recipe(
            "lunch_base",
            "Balanced Lunch",
            "Lunch",
            calories=600,
            protein=35,
            carbs=70,
            fats=18,
            fiber=9,
            ingredients=[{"name": "chicken", "quantity": "120 g"}, {"name": "rice", "quantity": "1 cup"}],
        ),
        _recipe(
            "dinner_base",
            "Balanced Dinner",
            "Dinner",
            calories=520,
            protein=30,
            carbs=55,
            fats=17,
            fiber=8,
            ingredients=[{"name": "tilapia", "quantity": "120 g"}, {"name": "pechay", "quantity": "1 cup"}],
        ),
    ]
    policy = {
        "planning": {
            "planning_horizon_days": 7,
            "meals_per_day": 3,
            "recipe_repeat_limits": [7],
        },
        "stage1": {
            "ML_shadow_enabled": False,
            "ML_canary_enabled": False,
            "max_candidates_per_slot": 10,
            "ranking_cutoff": 1.0,
            "similarity_threshold": 1.0,
            "budget_keep_min_count": 1,
            "budget_keep_min_ratio": 1.0,
            "minimum_candidates_required": 1,
            "pool_cap_top_share": 1.0,
        },
        "solver": {
            "solver_time_limit_seconds": 1.0,
            "solver_max_seconds": 2.0,
            "total_solver_seconds": 5.0,
            "timeout_ms": 5000,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }
    telemetry: dict = {}

    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out=telemetry)

    assert msg == "Success"
    assert plan is not None
    assert len(plan) == 7
    assert all(len(day.meals) == 3 for day in plan)
    assert explanation is not None
    assert telemetry.get("candidate_count_pre") == 3
    assert telemetry.get("candidate_count_post") == 3
    assert telemetry.get("pricing_diagnostics", {}).get("marketMultiplierDbCalls", 0) <= 5


def test_seeded_recipe_database_broad_profile_generates_plan_with_bounded_market_calls(tmp_path, monkeypatch):
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(tmp_path / "planner_seeded_smoke.db"))
    database.init_db()
    database.seed_recipes()
    price_catalog.invalidate_override_cache()
    original_market_multiplier = database.get_market_multiplier
    market_calls: list[tuple[str, int]] = []

    def counting_market_multiplier(category: str, month_index: int) -> float:
        market_calls.append((category, month_index))
        return original_market_multiplier(category, month_index)

    monkeypatch.setattr(database, "get_market_multiplier", counting_market_multiplier)
    profile = UserProfile(
        displayName="Seeded Broad Profile",
        age=28,
        heightCm=162,
        weightKg=64,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=[],
        maxCookingTimeMinutes=60,
        weeklyBudgetPhp=None,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=7, mealsPerDay=3)
    recipes = database.get_all_recipes()
    policy = {
        "stage1": {
            "ML_shadow_enabled": False,
            "ML_canary_enabled": False,
        },
        "solver": {
            "total_solver_seconds": 14.0,
            "timeout_ms": 120000,
            "retry_attempts": 1,
            "solver_workers": 1,
        },
    }
    telemetry: dict = {}

    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out=telemetry)

    assert msg == "Success"
    assert plan is not None
    assert len(plan) == 7
    assert explanation is not None
    assert telemetry.get("candidate_count_pre", 0) > 0
    assert telemetry.get("candidate_count_post", 0) > 0
    assert telemetry.get("phase_timings_ms", {}).get("stage1_preprocess", 999999) < 5000
    assert telemetry.get("pricing_diagnostics", {}).get("marketMultiplierDbCalls", 999999) <= 12
    assert len(market_calls) <= 12


def test_solver_returns_no_safe_plan_when_required_nutrition_bounds_are_impossible():
    profile = UserProfile(
        displayName="Hard Nutrition User",
        age=30,
        heightCm=160,
        weightKg=65,
        activityLevel="Lightly Active",
        goal="General Health",
        dietaryRestrictions=[],
        allergies=[],
        pantryItems=[],
        maxCookingTimeMinutes=60,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=1, mealsPerDay=3)
    recipes = [
        _recipe("low_b", "Low Breakfast", "Breakfast", protein=10, carbs=40, fats=15, fiber=2, sugar=30),
        _recipe("low_l", "Low Lunch", "Lunch", protein=10, carbs=40, fats=15, fiber=2, sugar=30),
        _recipe("low_d", "Low Dinner", "Dinner", protein=10, carbs=40, fats=15, fiber=2, sugar=30),
    ]
    policy = {
        "planning": {
            "planning_horizon_days": 1,
            "meals_per_day": 3,
            "recipe_repeat_limits": [3],
        },
        "nutrition": {
            "calorie_min": 1000,
            "calorie_max": 2200,
            "protein_min": 80,
            "protein_max": 200,
            "carb_min": 0,
            "carb_max": 500,
            "fat_min": 0,
            "fat_max": 250,
            "fiber_min": 20,
            "sodium_max": 2300,
            "sugar_max": 20,
            "daily_tolerance_percent": 0.2,
        },
        "stage1": {
            "ML_shadow_enabled": False,
            "ML_canary_enabled": False,
            "max_candidates_per_slot": 10,
            "ranking_cutoff": 1.0,
            "similarity_threshold": 1.0,
            "budget_keep_min_count": 1,
            "budget_keep_min_ratio": 1.0,
            "minimum_candidates_required": 1,
            "pool_cap_top_share": 1.0,
        },
        "solver": {
            "solver_time_limit_seconds": 1.0,
            "solver_max_seconds": 2.0,
            "total_solver_seconds": 3.0,
            "timeout_ms": 3000,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }

    telemetry = {}
    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out=telemetry)

    assert plan is None
    assert msg == "Catalog nutrition coverage is insufficient for this profile."
    assert explanation is None
    assert telemetry["stage1_diag"]["nutrition_feasibility"]["ok"] is False
    assert any(
        gap["nutrient"] in {"protein", "fiber", "sugar"}
        for gap in telemetry["stage1_diag"]["nutrition_feasibility"]["gaps"]
    )


def test_resolve_budget_weekly_prefers_weekly_php():
    profile = UserProfile(weeklyBudgetPhp=3000, budgetWeekly=2000, budgetMonthly=8000)
    assert meal_planner.resolve_budget_weekly(profile) == 3000.0


def test_build_plan_day_labels_respects_start_date_anchor():
    labels = meal_planner.build_plan_day_labels(7, "2026-04-10")

    assert labels == ["Fri", "Sat", "Sun", "Mon", "Tue", "Wed", "Thu"]


def test_build_plan_day_labels_stays_english_and_deterministic():
    labels = meal_planner.build_plan_day_labels(4, "2026-04-12")

    assert labels == ["Sun", "Mon", "Tue", "Wed"]
    assert set(labels).issubset({"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"})


def test_budget_aware_pool_limit_shrinks_for_tight_solver_budgets():
    assert meal_planner._budget_aware_pool_limit(
        max_pool_size=192,
        slot_count=21,
        total_time_limit=14.0,
        minimum_candidates_required=10,
    ) == 120

    assert meal_planner._budget_aware_pool_limit(
        max_pool_size=192,
        slot_count=21,
        total_time_limit=25.0,
        minimum_candidates_required=10,
    ) == 192


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


@pytest.mark.parametrize(
    ("allergy", "ingredient_name"),
    [
        ("fish", "bangus fillet"),
        ("fish", "tilapia"),
        ("fish", "galunggong"),
        ("fish", "salmon"),
        ("fish", "tuna steak"),
        ("shellfish", "shrimp"),
        ("shellfish", "crab"),
        ("shellfish", "squid"),
        ("dairy", "cheese"),
        ("dairy", "milk"),
        ("egg", "egg"),
        ("egg", "eggs"),
        ("peanut", "peanuts"),
        ("soy", "tofu"),
        ("gluten", "wheat flour"),
    ],
)
def test_allergy_filter_blocks_descendant_ingredients(allergy, ingredient_name):
    profile = UserProfile(allergies=[allergy], maxCookingTimeMinutes=45)
    stage1_diag = {}
    buckets = meal_planner.shortlist_candidates(
        profile,
        [
            _recipe(
                "r1",
                "Allergen Recipe",
                "Lunch",
                ingredients=[{"name": ingredient_name, "quantity": "1 kg"}],
            )
        ],
        stage1_diag=stage1_diag,
    )

    assert all(len(v) == 0 for v in buckets.values())
    assert stage1_diag["exclusion_summary"]["allergy"] == 1
    assert stage1_diag["exclusion_detail_counts"][f"allergy:{allergy}"] == 1


def test_custom_allergy_filter_blocks_direct_ingredient_token():
    profile = UserProfile(allergies=["chicken"], maxCookingTimeMinutes=45)
    stage1_diag = {}
    recipes = [
        _recipe(
            "unsafe",
            "Chicken Adobo",
            "Lunch",
            ingredients=[{"name": "chicken breast", "quantity": "200 g"}],
        ),
        _recipe(
            "safe",
            "Rice Bowl",
            "Lunch",
            ingredients=[{"name": "brown rice", "quantity": "1 cup"}],
        ),
    ]

    buckets = meal_planner.shortlist_candidates(profile, recipes, stage1_diag=stage1_diag)

    assert [recipe["id"] for recipe in buckets["Lunch"]] == ["safe"]
    assert stage1_diag["exclusion_summary"]["allergy"] == 1
    assert stage1_diag["exclusion_detail_counts"]["allergy:chicken"] == 1


def test_custom_allergy_filter_uses_filipino_ingredient_synonyms():
    profile = UserProfile(allergies=["manok"], maxCookingTimeMinutes=45)
    stage1_diag = {}
    recipes = [
        _recipe(
            "unsafe",
            "Tinola",
            "Dinner",
            ingredients=[{"name": "manok", "quantity": "200 g"}],
        ),
    ]

    buckets = meal_planner.shortlist_candidates(profile, recipes, stage1_diag=stage1_diag)

    assert all(len(v) == 0 for v in buckets.values())
    assert stage1_diag["exclusion_detail_counts"]["allergy:chicken"] == 1


def test_known_allergy_phrase_does_not_promote_generic_descriptor_to_custom_allergy():
    profile = UserProfile(allergies=["soy sauce"], maxCookingTimeMinutes=45)

    buckets = meal_planner.shortlist_candidates(
        profile,
        [
            _recipe(
                "tomato_sauce",
                "Tomato Sauce Bowl",
                "Lunch",
                ingredients=[{"name": "tomato sauce", "quantity": "2 tbsp"}],
            )
        ],
    )

    assert [recipe["id"] for recipe in buckets["Lunch"]] == ["tomato_sauce"]


def test_seafood_allergy_blocks_fish_and_shellfish_families():
    profile = UserProfile(allergies=["seafood"], maxCookingTimeMinutes=45)
    stage1_diag = {}

    buckets = meal_planner.shortlist_candidates(
        profile,
        [
            _recipe(
                "fish",
                "Bangus",
                "Lunch",
                ingredients=[{"name": "bangus", "quantity": "1 fillet"}],
            ),
            _recipe(
                "shellfish",
                "Shrimp",
                "Dinner",
                ingredients=[{"name": "shrimp", "quantity": "1 cup"}],
            ),
        ],
        stage1_diag=stage1_diag,
    )

    assert all(len(v) == 0 for v in buckets.values())
    assert stage1_diag["exclusion_summary"]["allergy"] == 2
    assert stage1_diag["exclusion_detail_counts"]["allergy:fish"] == 1
    assert stage1_diag["exclusion_detail_counts"]["allergy:shellfish"] == 1


def test_build_swap_candidates_blocks_descendant_allergy_matches():
    profile = UserProfile(
        allergies=["fish"],
        pantryItems=["egg", "banana"],
        maxCookingTimeMinutes=45,
    )
    recipes = [
        _recipe("b_current", "Current Breakfast", "Breakfast", ingredients=[{"name": "egg", "quantity": "2 pcs"}]),
        _recipe("b_fish", "Tilapia Breakfast", "Breakfast", ingredients=[{"name": "tilapia", "quantity": "1 fillet"}]),
        _recipe("b_safe", "Banana Breakfast", "Breakfast", ingredients=[{"name": "banana", "quantity": "1 pc"}]),
    ]

    swaps = meal_planner.build_swap_candidates(
        profile,
        recipes,
        meal_label="Breakfast",
        current_recipe_id="b_current",
        active_recipe_ids=["b_current"],
        limit=10,
    )

    assert [recipe["id"] for recipe in swaps] == ["b_safe"]


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
            "fiberGrams": 7,
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
        policy={
            "planning": {
                "recipe_repeat_limits": [2],
            }
        },
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
            "fiberGrams": 7,
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
            "fiberGrams": 7,
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


def test_shortlist_candidates_scales_cost_estimates_for_households():
    profile = UserProfile(
        householdSize=4,
        pantryItems=["rice"],
        maxCookingTimeMinutes=45,
    )
    recipe = {
        "id": "l_family",
        "title": "Family Lunch",
        "mealType": "Lunch",
        "calories": 520,
        "proteinGrams": 24,
        "carbsGrams": 52,
        "fatsGrams": 14,
        "fiberGrams": 6,
        "minutes": 20,
        "ingredients": [{"name": "rice", "quantity": "1 cup"}],
        "tags": [],
    }

    buckets = meal_planner.shortlist_candidates(profile, [recipe])
    shortlisted = buckets["Lunch"]

    assert len(shortlisted) == 1
    assert shortlisted[0]["_cost_est"] == meal_planner.estimate_cost(recipe, household_size=4)


def test_estimate_cost_scales_monotonically_with_household_size():
    recipe = _recipe(
        "family_scale",
        "Scaled Meal",
        "Lunch",
        ingredients=[{"name": "rice", "quantity": "1 cup"}, {"name": "egg", "quantity": "2 pcs"}],
    )

    costs = [
        meal_planner.estimate_cost(recipe, household_size=size)
        for size in (1, 2, 4, 6)
    ]

    assert costs[0] < costs[1] < costs[2] < costs[3]


def test_shortlist_candidates_batches_ml_shadow_scoring(monkeypatch):
    class FakeRanker:
        def __init__(self):
            self.score_many_calls = 0

        def state(self):
            return RankerState(
                ready=True,
                model_version="fake_v1",
                feature_columns=["recipe_calories"],
                error=None,
            )

        def score_many(self, features_list):
            self.score_many_calls += 1
            return [0.42 for _ in features_list]

    fake_ranker = FakeRanker()
    monkeypatch.setattr(meal_planner, "get_stage1_ranker", lambda: fake_ranker)

    profile = UserProfile(
        displayName="BatchTest",
        age=28,
        activityLevel="Lightly Active",
        goal="General Health",
        pantryItems=["egg"],
        maxCookingTimeMinutes=45,
    )
    recipes = [
        {
            "id": "b1",
            "title": "Breakfast One",
            "mealType": "Breakfast",
            "calories": 400,
            "proteinGrams": 20,
            "carbsGrams": 40,
            "fatsGrams": 10,
            "fiberGrams": 4,
            "minutes": 10,
            "ingredients": [{"name": "egg", "quantity": "2 pcs"}],
            "tags": [],
        },
        {
            "id": "l1",
            "title": "Lunch One",
            "mealType": "Lunch",
            "calories": 520,
            "proteinGrams": 28,
            "carbsGrams": 48,
            "fatsGrams": 16,
            "fiberGrams": 7,
            "minutes": 20,
            "ingredients": [{"name": "rice", "quantity": "1 cup"}],
            "tags": [],
        },
    ]

    stage1_diag = {}
    buckets = meal_planner.shortlist_candidates(profile, recipes, policy={"stage1": {"ML_shadow_enabled": True}}, stage1_diag=stage1_diag)

    assert fake_ranker.score_many_calls == 1
    assert stage1_diag["ml_candidate_count"] == 2
    assert stage1_diag["ranker_ready"] is True
    assert buckets["Breakfast"][0]["_ml_model_version"] == "fake_v1"
    assert buckets["Breakfast"][0]["_ml_shadow_score"] == pytest.approx(0.42)


def test_finalize_stage1_scoring_preserves_existing_goal_boost_when_ml_is_not_applied():
    recipe = {
        "_symptom_goal_boost": 2.5,
        "_stage1_score_boost": 1.5,
    }

    meal_planner._finalize_stage1_scoring(
        recipe,
        ml_score=0.9,
        ml_model_version="fake_v1",
        apply_ml_to_ranking=False,
        ml_weight=0.3,
        prep_penalty=1.0,
    )

    assert recipe["_ml_applied_to_ranking"] is False
    assert recipe["_stage1_score_boost"] == pytest.approx(1.5)


def test_shortlist_candidates_legacy_ml_flag_applies_live_ranking(monkeypatch):
    class FakeRanker:
        def state(self):
            return RankerState(
                ready=True,
                model_version="fake_v2",
                feature_columns=["recipe_calories"],
                error=None,
            )

        def score_many(self, features_list):
            assert len(features_list) == 2
            return [0.1, 0.9]

    monkeypatch.setattr(meal_planner, "get_stage1_ranker", lambda: FakeRanker())

    profile = UserProfile(
        displayName="LiveML",
        age=28,
        activityLevel="Lightly Active",
        goal="General Health",
        maxCookingTimeMinutes=45,
    )
    recipes = [
        _recipe("b_low", "Breakfast Low", "Breakfast", ingredients=[{"name": "egg", "quantity": "2 pcs"}]),
        _recipe("b_high", "Breakfast High", "Breakfast", ingredients=[{"name": "egg", "quantity": "2 pcs"}]),
    ]
    policy = {
        "stage1": {
            "ML_shadow_enabled": True,
            "ML_canary_enabled": False,
            "ML_score_weight": 0.15,
            "ML_score_cap": 0.3,
            "max_candidates_per_slot": 10,
            "ranking_cutoff": 1.0,
            "similarity_threshold": 1.0,
            "restricted_shortlist_multiplier": 1.0,
            "budget_keep_min_count": 1,
            "budget_keep_min_ratio": 1.0,
            "pantry_match_threshold": 0,
            "minimum_candidates_required": 1,
            "pool_cap_top_share": 1.0,
        }
    }

    buckets = meal_planner.shortlist_candidates(profile, recipes, policy=policy, stage1_diag={})

    assert [recipe["id"] for recipe in buckets["Breakfast"]] == ["b_high", "b_low"]
    assert buckets["Breakfast"][0]["_ml_applied_to_ranking"] is True


def test_build_swap_candidates_allows_repeats_up_to_policy_limit():
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
            "fiberGrams": 7,
            "minutes": 10,
            "ingredients": [{"name": "egg", "quantity": "2 pcs"}],
            "tags": [],
        },
        {
            "id": "b_repeat_once",
            "title": "Seen Once Already",
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
            "id": "b_fresh",
            "title": "Fresh Breakfast Swap",
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
    policy = {
        "planning": {
            "recipe_repeat_limits": [2],
        }
    }

    swaps = meal_planner.build_swap_candidates(
        profile,
        recipes,
        meal_label="Breakfast",
        current_recipe_id="b_current",
        active_recipe_ids=["b_current", "b_repeat_once"],
        limit=10,
        policy=policy,
    )

    swap_ids = {recipe["id"] for recipe in swaps}
    assert "b_repeat_once" in swap_ids
    assert "b_fresh" in swap_ids


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
            "fiberGrams": 7,
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
            "fiberGrams": 7,
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
    assert telemetry.get("ranking_strategy") == "stage1_ml_canary_plus_heuristic"
    assert isinstance(telemetry.get("phase_timings_ms"), dict)
    assert telemetry["phase_timings_ms"].get("stage1_shortlist", -1) >= 0
    assert telemetry["phase_timings_ms"].get("stage1_price_estimation", -1) >= 0
    assert telemetry["phase_timings_ms"].get("stage1_ml_score", -1) >= 0
    assert telemetry["phase_timings_ms"].get("planner_total", -1) >= 0
    assert telemetry.get("solver_budget", {}).get("totalTimeLimitSeconds") == 3.0
    assert isinstance(telemetry.get("stage1_diag"), dict)
    assert isinstance(telemetry.get("solve_pair_diagnostics"), list)
    assert explanation.get("phaseTimingsMs", {}).get("planner_total", -1) >= 0
    assert explanation.get("solverBudget", {}).get("totalTimeLimitSeconds") == 3.0


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
            "fiberGrams": 7,
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
            "fiberGrams": 7,
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


def test_solve_meal_plan_stage1_ml_uses_file_backed_ranker_artifacts(monkeypatch):
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
            "fiberGrams": 7,
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
            "fiberGrams": 7,
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
    assert telemetry.get("ranking_strategy") == "stage1_ml_canary_plus_heuristic"
    model_version = telemetry.get("ml_model_version")
    assert model_version in {"lightgbm_stage1_ranker_v1", "shadow_v0"}
    if model_version == "lightgbm_stage1_ranker_v1":
        assert telemetry.get("stage1_diag", {}).get("ranker_ready") is True
    stage1_candidates = telemetry.get("stage1_candidates") or []
    assert stage1_candidates
    assert any(float(candidate.get("model_score") or 0.0) > 0.0 for candidate in stage1_candidates)


def test_budget_cost_objective_only_applies_for_budget_priority():
    assert meal_planner._should_optimize_cost(
        UserProfile(planningPriority="Budget First", weeklyBudgetPhp=2500)
    ) is True
    assert meal_planner._should_optimize_cost(
        UserProfile(planningPriority="Balanced", weeklyBudgetPhp=2500)
    ) is False


def test_validate_profile_rejects_semantically_conflicting_inputs():
    assert meal_planner.validate_profile(
        UserProfile(dietaryRestrictions=["Vegetarian", "Pescatarian"])
    ) == "Conflicting restrictions: Vegetarian and Pescatarian cannot both be active."

    assert meal_planner.validate_profile(
        UserProfile(dietaryRestrictions=["Pescatarian"], allergies=["fish", "shellfish"])
    ) == "Conflicting profile: Pescatarian cannot be combined with both fish and shellfish allergies."

    assert meal_planner.validate_profile(
        UserProfile(planningPriority="Budget First", weeklyBudgetPhp=0)
    ) == "Budget First priority requires a weekly budget."

    assert meal_planner.validate_profile(
        UserProfile(planningPriority="Variety First", varietyPreference="Low")
    ) == "Variety First priority conflicts with Low variety preference."

    assert meal_planner.validate_profile(
        UserProfile.model_construct(householdSize=0)
    ) == "Household size must stay between 1 and 6."

    assert meal_planner.validate_profile(
        UserProfile.model_construct(maxCookingTimeMinutes=5)
    ) == "Max cooking time must stay between 10 and 240 minutes."


def test_symptoms_create_deterministic_planner_adjustments():
    symptom_state = meal_planner.symptom_adjustments(
        UserProfile(symptoms=["Weight gain", "Acne", "Hair loss"]),
        "Symptom Management",
    )

    assert symptom_state["calorieTargetDelta"] < 0
    assert symptom_state["fiberMinBonus"] > 0
    assert symptom_state["proteinTargetBonus"] > 0
    assert symptom_state["sugarMaxDelta"] < 0
    assert symptom_state["dairyPenalty"] > 0
    assert symptom_state["notes"]


def test_goal_and_symptoms_are_reflected_in_planner_explanation():
    recipes = [
        _recipe("b1", "Breakfast", "Breakfast", ingredients=[{"name": "egg", "quantity": "2 pcs"}], fiber=10, sugar=4),
        _recipe("l1", "Lunch", "Lunch", ingredients=[{"name": "rice", "quantity": "1 cup"}], fiber=10, sugar=6),
        _recipe("d1", "Dinner", "Dinner", ingredients=[{"name": "chicken", "quantity": "200 g"}], fiber=10, sugar=5),
    ]
    policy = {
        "planning": {"planning_horizon_days": 1, "meals_per_day": 3, "recipe_repeat_limits": [3]},
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
            "solver_time_limit_seconds": 1.0,
            "solver_max_seconds": 2.0,
            "total_solver_seconds": 3.0,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }
    general_request = meal_planner.GeneratePlanRequest(
        profile=UserProfile(
            displayName="GoalGeneral",
            age=28,
            heightCm=160,
            weightKg=62,
            activityLevel="Lightly Active",
            goal="General Health",
            maxCookingTimeMinutes=60,
        ),
        days=1,
        mealsPerDay=3,
    )
    symptom_request = meal_planner.GeneratePlanRequest(
        profile=UserProfile(
            displayName="GoalSymptom",
            age=28,
            heightCm=160,
            weightKg=62,
            activityLevel="Lightly Active",
            goal="Symptom Management",
            symptoms=["Acne", "Hair loss"],
            maxCookingTimeMinutes=60,
        ),
        days=1,
        mealsPerDay=3,
    )
    weight_loss_request = meal_planner.GeneratePlanRequest(
        profile=UserProfile(
            displayName="GoalLoss",
            age=28,
            heightCm=160,
            weightKg=62,
            activityLevel="Lightly Active",
            goal="Weight Loss",
            maxCookingTimeMinutes=60,
        ),
        days=1,
        mealsPerDay=3,
    )

    _, general_msg, general_explanation = meal_planner.solve_meal_plan(general_request, recipes, policy=policy, telemetry_out={})
    _, symptom_msg, symptom_explanation = meal_planner.solve_meal_plan(symptom_request, recipes, policy=policy, telemetry_out={})
    _, loss_msg, loss_explanation = meal_planner.solve_meal_plan(weight_loss_request, recipes, policy=policy, telemetry_out={})

    assert general_msg == "Success"
    assert symptom_msg == "Success"
    assert loss_msg == "Success"
    assert symptom_explanation["fiberMinTarget"] > general_explanation["fiberMinTarget"]
    assert symptom_explanation["sugarMaxTarget"] < general_explanation["sugarMaxTarget"]
    assert symptom_explanation["symptomStrategy"]
    assert symptom_explanation["selectionReasonsByRecipeId"]
    assert symptom_explanation["selectionReasonCounts"]
    assert loss_explanation["targetCalories"] < general_explanation["targetCalories"]
    assert any("Weight Loss lowers calorie target." == item for item in loss_explanation["goalStrategy"])


def test_solve_meal_plan_enforces_budget_as_hard_cap():
    profile = UserProfile(
        displayName="BudgetGuard",
        age=27,
        heightCm=160,
        weightKg=60,
        activityLevel="Lightly Active",
        goal="General Health",
        weeklyBudgetPhp=150,
        maxCookingTimeMinutes=60,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=1, mealsPerDay=3)
    recipes = [
        _recipe("b1", "Breakfast", "Breakfast", ingredients=[{"name": "egg", "quantity": "2 pcs"}]),
        _recipe("l1", "Lunch", "Lunch", ingredients=[{"name": "rice", "quantity": "1 cup"}]),
        _recipe("d1", "Dinner", "Dinner", ingredients=[{"name": "tomato", "quantity": "1 kg"}]),
    ]
    policy = {
        "planning": {"planning_horizon_days": 1, "meals_per_day": 3, "recipe_repeat_limits": [3]},
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
            "solver_time_limit_seconds": 1.0,
            "solver_max_seconds": 2.0,
            "total_solver_seconds": 3.0,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }

    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out={})

    assert msg == "Success"
    assert plan is not None
    assert explanation["estimatedWeeklyCost"] <= 150
    assert explanation["budgetHardCapApplied"] is True


def test_solve_meal_plan_returns_no_safe_plan_when_budget_makes_model_infeasible():
    profile = UserProfile(
        displayName="BudgetTooLow",
        age=27,
        heightCm=160,
        weightKg=60,
        activityLevel="Lightly Active",
        goal="General Health",
        weeklyBudgetPhp=20,
        maxCookingTimeMinutes=60,
    )
    request = meal_planner.GeneratePlanRequest(profile=profile, days=1, mealsPerDay=3)
    recipes = [
        _recipe("b1", "Breakfast", "Breakfast", ingredients=[{"name": "egg", "quantity": "2 pcs"}]),
        _recipe("l1", "Lunch", "Lunch", ingredients=[{"name": "rice", "quantity": "1 cup"}]),
        _recipe("d1", "Dinner", "Dinner", ingredients=[{"name": "tomato", "quantity": "1 kg"}]),
    ]
    policy = {
        "planning": {"planning_horizon_days": 1, "meals_per_day": 3, "recipe_repeat_limits": [3]},
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
            "solver_time_limit_seconds": 1.0,
            "solver_max_seconds": 2.0,
            "total_solver_seconds": 3.0,
            "retry_attempts": 0,
            "optimality_gap_target": 0.1,
            "solver_workers": 1,
        },
    }

    plan, msg, explanation = meal_planner.solve_meal_plan(request, recipes, policy=policy, telemetry_out={})

    assert plan is None
    assert msg != "Success"
    assert explanation is None


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
