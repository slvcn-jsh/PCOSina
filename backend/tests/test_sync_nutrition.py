import csv
import json
import sys
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database
import sync_nutrition
from domain.models import UserProfile
from services import meal_planner


def _temp_dir() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_sync_nutrition"
    path = base / uuid4().hex
    path.mkdir(parents=True, exist_ok=True)
    return path


def _use_temp_db(folder: Path) -> None:
    database.DATABASE_URL = ""
    database.DB_NAME = str(folder / "sync_nutrition.db")
    database.init_db()


def _write_recipes(path: Path) -> None:
    recipes = [
        {
            "id": "missing-1",
            "name": "Missing Nutrition Tinola",
            "nutrition": {
                "calories": None,
                "protein_g": None,
                "carbs_g": None,
                "fat_g": None,
                "fiber_g": None,
            },
        },
        {
            "id": "complete-1",
            "name": "Complete Monggo",
            "nutrition": {
                "calories": 410,
                "protein_g": 21,
                "carbs_g": 45,
                "fat_g": 10,
                "fiber_g": 9,
            },
        },
    ]
    path.write_text(json.dumps(recipes), encoding="utf-8")


def _write_repeated_missing_recipes(path: Path, count: int = 24) -> None:
    meal_types = ["Breakfast", "Lunch", "Dinner"]
    recipes = []
    for index in range(count):
        recipes.append(
            {
                "id": f"bulk-{index}",
                "name": f"Bulk Recipe {index}",
                "mealType": meal_types[index % len(meal_types)],
                "minutes": 20,
                "nutrition": {
                    "calories": None,
                    "protein_g": None,
                    "carbs_g": None,
                    "fat_g": None,
                    "fiber_g": None,
                },
            }
        )
    path.write_text(json.dumps(recipes), encoding="utf-8")


def test_missing_report_does_not_create_fake_corrections():
    folder = _temp_dir()
    _use_temp_db(folder)
    recipes_path = folder / "recipes.json"
    report_path = folder / "missing.csv"
    _write_recipes(recipes_path)

    missing = sync_nutrition.write_missing_report(report_path, recipes_path)

    assert missing == [{"id": "missing-1", "title": "Missing Nutrition Tinola"}]
    assert report_path.exists()
    assert database.list_admin_nutrition_corrections() == []


def test_seeded_recipes_preserve_nutrition_provenance():
    folder = _temp_dir()
    _use_temp_db(folder)
    recipes_path = folder / "recipes.json"
    _write_recipes(recipes_path)

    database.seed_recipes(source_path=str(recipes_path), force_reseed=True)
    recipes = {recipe["id"]: recipe for recipe in database.get_all_recipes()}

    assert recipes["missing-1"]["nutritionConfidence"] == "imputed"
    assert recipes["missing-1"]["nutritionDataSource"] == "seed_imputed_median"
    assert recipes["complete-1"]["nutritionConfidence"] == "estimated"
    assert recipes["complete-1"]["nutritionDataSource"] == "seed_file"


def test_readiness_report_surfaces_imputed_catalog_nutrition():
    folder = _temp_dir()
    _use_temp_db(folder)
    recipes_path = folder / "recipes.json"
    report_path = folder / "catalog_nutrition_readiness.json"
    _write_recipes(recipes_path)
    database.seed_recipes(source_path=str(recipes_path), force_reseed=True)

    status = sync_nutrition.write_readiness_report(report_path, recipes_path)
    saved = json.loads(report_path.read_text(encoding="utf-8"))

    assert status["activeRecipeCount"] == 2
    assert status["rawSeedCompleteNutritionCount"] == 1
    assert status["rawSeedMissingNutritionCount"] == 1
    assert status["imputedNutritionCount"] == 1
    assert status["confidenceCounts"]["imputed"] == 1
    assert status["trustedStrongMealAvailableBySlot"]["Breakfast"] == 0
    assert any("reviewed full-meal nutrition coverage" in item for item in status["errors"])
    assert saved["imputedNutritionCount"] == 1
    assert report_path.exists()


def test_bundled_catalog_nutrition_seed_has_no_fixed_placeholder_profiles():
    folder = _temp_dir()
    _use_temp_db(folder)

    database.seed_recipes(source_path=str(ROOT / "recipes.json"), force_reseed=True)
    database.seed_nutrition_corrections()
    status = database.get_recipe_catalog_nutrition_status(str(ROOT / "recipes.json"))

    assert status["ok"] is True
    assert status["activeRecipeCount"] == 1130
    assert status["completeNutritionProfileCount"] == 1130
    assert status["imputedNutritionCount"] == 0
    assert status["placeholderNutritionProfileCounts"] == {
        "350/20/40/12/5": 0,
        "357/10/49/8/7": 0,
    }
    assert status["dominantActiveNutritionProfile"]["count"] <= 5
    assert status["sourceCounts"]["local_reference_ingredient_sum_draft"] == 876
    assert status["sourceCounts"]["local_reference_missing_quantity_draft"] == 1
    assert status["sourceCounts"]["panlasang_pinoy_recipe_card_per_serving"] == 141
    assert status["sourceCounts"]["panlasang_pinoy_recipe_card_yield_normalized"] == 36

    profile = UserProfile(
        dietaryRestrictions=["Vegetarian", "No Pork", "No Beef", "Lactose Intolerant"],
        allergies=["dairy", "egg", "fish", "gluten", "nuts", "peanut", "shellfish", "soy"],
        maxCookingTimeMinutes=45,
    )
    restricted_safe_strong = []
    for recipe in database.get_all_recipes():
        tags = meal_planner.infer_tags(recipe)
        ing_tokens = meal_planner.normalize_ingredients(recipe.get("ingredients", []))
        if meal_planner.restriction_failure_reasons(profile, tags, ing_tokens):
            continue
        if int(recipe.get("minutes") or 0) > 45:
            continue
        if (
            400 <= int(recipe.get("calories") or 0) <= 650
            and 18 <= int(recipe.get("proteinGrams") or 0) <= 28
            and 45 <= int(recipe.get("carbsGrams") or 0) <= 85
            and 8 <= int(recipe.get("fatsGrams") or 0) <= 22
            and 8 <= int(recipe.get("fiberGrams") or 0) <= 14
        ):
            restricted_safe_strong.append(recipe)

    assert len(restricted_safe_strong) >= 16

    solve_profile = UserProfile(
        displayName="HardRestricted",
        age=27,
        heightCm=160,
        weightKg=60,
        activityLevel="Lightly Active",
        goal="Symptom Management",
        weeklyBudgetPhp=5000,
        maxCookingTimeMinutes=45,
        planningPriority="Nutrition First",
        varietyPreference="Balanced",
        dietaryRestrictions=["Vegetarian", "No Pork", "No Beef", "Lactose Intolerant"],
        allergies=["dairy", "egg", "fish", "gluten", "nuts", "peanut", "shellfish", "soy"],
        symptoms=["Weight gain", "Irregular periods", "Acne", "Hair loss"],
    )
    policy = {
        "planning": {
            "recipe_repeat_limits": [2, 3, 4, 10],
            "infeasibility_relaxation_order": ["daily_tolerance_percent", "recipe_repeat_limits"],
        },
        "stage1": {
            "max_candidates_per_slot": 64,
            "restricted_shortlist_multiplier": 1.15,
            "minimum_candidates_required": 10,
            "ML_shadow_enabled": True,
            "ML_canary_enabled": False,
        },
        "solver": {
            "solver_time_limit_seconds": 6.0,
            "solver_max_seconds": 12.0,
            "total_solver_seconds": 14.0,
            "retry_attempts": 1,
            "optimality_gap_target": 0.05,
            "solver_workers": 1,
        },
    }
    telemetry = {}
    plan, msg, _explanation = meal_planner.solve_meal_plan(
        meal_planner.GeneratePlanRequest(profile=solve_profile, days=7, mealsPerDay=3),
        database.get_all_recipes(),
        policy=policy,
        telemetry_out=telemetry,
    )

    assert msg == "Success"
    assert plan is not None
    assert telemetry["status"] == "success"
    assert telemetry["stage1_diag"]["restricted_nutrition_anchor_reserve"] is True
    assert telemetry["stage1_diag"]["restricted_solver_pair_priority"] is True
    assert telemetry["stage1_diag"]["restricted_nutrition_anchor_count_post_trim"] >= 16
    assert telemetry["stage1_diag"]["repeat_sequence"] == [6, 8, 10]
    assert telemetry["solve_pair_diagnostics"][0]["tol"] == 0.4
    assert telemetry["solve_pair_diagnostics"][0]["maxPerWeek"] == 8
    assert set(telemetry["selected_recipe_ids"]) & {f"ph_qk_{idx:03d}" for idx in range(61, 77)}


def test_review_queue_exports_missing_nutrition_with_source_metadata():
    recipes = [
        {
            "id": "queue-1",
            "name": "Queue Tinola",
            "mealType": "Breakfast",
            "minutes": 40,
            "sourceServings": "4",
            "sourcePrepTime": "10 minutes",
            "sourceCookTime": "30 minutes",
            "sourceTotalTime": "40 minutes",
            "sourceIngredientNames": "chicken | sayote | malunggay",
            "nutrition": {
                "calories": None,
                "protein_g": None,
                "carbs_g": None,
                "fat_g": None,
                "fiber_g": None,
            },
            "ingredients": [
                {"name": "1 lb chicken", "quantity": ""},
                {"name": "1 sayote sliced", "quantity": ""},
            ],
            "instructions": ["Simmer chicken.", "Add vegetables."],
        },
        {
            "id": "complete-1",
            "name": "Complete",
            "mealType": "Lunch",
            "nutrition": {
                "calories": 410,
                "protein_g": 21,
                "carbs_g": 45,
                "fat_g": 10,
                "fiber_g": 9,
            },
            "ingredients": [],
            "instructions": [],
        },
    ]

    rows = sync_nutrition.nutrition_review_queue_rows(recipes)

    assert len(rows) == 1
    assert rows[0]["priority"] == "P0"
    assert rows[0]["recipe_id"] == "queue-1"
    assert rows[0]["source_servings"] == "4"
    assert rows[0]["source_ingredient_names"] == "chicken | sayote | malunggay"
    assert rows[0]["ingredients"] == "1 lb chicken | 1 sayote sliced"
    assert rows[0]["calories"] == ""
    assert "Do not mark reviewed" in rows[0]["notes"]


def test_readiness_report_rejects_repeated_trusted_nutrition_profiles():
    folder = _temp_dir()
    _use_temp_db(folder)
    recipes_path = folder / "recipes.json"
    _write_repeated_missing_recipes(recipes_path)
    database.seed_recipes(source_path=str(recipes_path), force_reseed=True)
    for recipe in database.get_all_recipes():
        database.upsert_nutrition_correction(
            recipe["id"],
            {
                "calories": 420,
                "proteinGrams": 25,
                "carbsGrams": 45,
                "fatsGrams": 12,
                "fiberGrams": 8,
                "notes": "source=manual_correction; confidence=reviewed; review_status=reviewed",
            },
        )

    status = sync_nutrition.write_readiness_report(folder / "readiness.json", recipes_path)

    assert status["trustedNutritionCount"] == 24
    assert status["trustedNutritionProfileUniqueCount"] == 1
    assert status["dominantTrustedNutritionProfile"]["count"] == 24
    assert status["activeNutritionProfileUniqueCount"] == 1
    assert status["dominantActiveNutritionProfile"]["count"] == 24
    assert status["placeholderNutritionProfileCounts"]["350/20/40/12/5"] == 0
    assert any("trusted recipes sharing one nutrition profile" in item for item in status["errors"])
    assert any("active recipes sharing one nutrition profile" in item for item in status["errors"])


def test_purge_repeated_trusted_profile_deletes_placeholder_corrections():
    folder = _temp_dir()
    _use_temp_db(folder)
    recipes_path = folder / "recipes.json"
    _write_repeated_missing_recipes(recipes_path)
    database.seed_recipes(source_path=str(recipes_path), force_reseed=True)
    for recipe in database.get_all_recipes():
        database.upsert_nutrition_correction(
            recipe["id"],
            {
                "calories": 420,
                "proteinGrams": 25,
                "carbsGrams": 45,
                "fatsGrams": 12,
                "fiberGrams": 8,
                "notes": "source=manual_correction; confidence=reviewed; review_status=reviewed",
            },
        )

    purged = sync_nutrition.purge_repeated_trusted_profile(apply=True, recipe_file=recipes_path)

    assert len(purged) == 24
    assert database.list_admin_nutrition_corrections(limit=5000) == []


def test_seed_nutrition_corrections_replaces_placeholder_profile():
    folder = _temp_dir()
    _use_temp_db(folder)
    recipes_path = folder / "recipes.json"
    _write_repeated_missing_recipes(recipes_path, count=2)
    database.seed_recipes(source_path=str(recipes_path), force_reseed=True)
    database.upsert_nutrition_correction(
        "bulk-0",
        {
            "calories": 350,
            "proteinGrams": 20,
            "carbsGrams": 40,
            "fatsGrams": 12,
            "fiberGrams": 5,
            "notes": "Automated analysis integration",
        },
    )
    database.upsert_nutrition_correction(
        "bulk-1",
        {
            "calories": 357,
            "proteinGrams": 10,
            "carbsGrams": 49,
            "fatsGrams": 8,
            "fiberGrams": 7,
            "notes": "Automated analysis integration",
        },
    )
    seed_path = folder / "nutrition_seed.csv"
    with seed_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "recipe_id",
                "calories",
                "protein_grams",
                "carbs_grams",
                "fats_grams",
                "fiber_grams",
                "source",
                "confidence",
                "review_status",
                "notes",
            ],
        )
        writer.writeheader()
        writer.writerow(
            {
                "recipe_id": "bulk-0",
                "calories": "435",
                "protein_grams": "53",
                "carbs_grams": "26",
                "fats_grams": "16",
                "fiber_grams": "9",
                "source": "panlasang_pinoy_recipe_card_per_serving",
                "confidence": "high",
                "review_status": "source_verified",
                "notes": "source_url=https://example.test",
            }
        )
        writer.writerow(
            {
                "recipe_id": "bulk-1",
                "calories": "410",
                "protein_grams": "24",
                "carbs_grams": "44",
                "fats_grams": "10",
                "fiber_grams": "7",
                "source": "local_reference_ingredient_sum_draft",
                "confidence": "api_estimate",
                "review_status": "pending_review",
                "notes": "draft",
            }
        )

    summary = database.seed_nutrition_corrections([str(seed_path)])
    first = database.get_nutrition_correction_by_recipe_id("bulk-0")
    second = database.get_nutrition_correction_by_recipe_id("bulk-1")

    assert summary["updatedCount"] == 2
    assert first["calories"] == 435
    assert second["calories"] == 410
    assert "review_status=source_verified" in first["notes"]


def test_seed_nutrition_corrections_keeps_existing_reviewed_correction():
    folder = _temp_dir()
    _use_temp_db(folder)
    recipes_path = folder / "recipes.json"
    _write_repeated_missing_recipes(recipes_path, count=1)
    database.seed_recipes(source_path=str(recipes_path), force_reseed=True)
    database.upsert_nutrition_correction(
        "bulk-0",
        {
            "calories": 500,
            "proteinGrams": 30,
            "carbsGrams": 50,
            "fatsGrams": 15,
            "fiberGrams": 8,
            "notes": "source=manual_review; confidence=reviewed; review_status=reviewed",
        },
    )
    seed_path = folder / "nutrition_seed.csv"
    with seed_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "recipe_id",
                "calories",
                "protein_grams",
                "carbs_grams",
                "fats_grams",
                "fiber_grams",
                "source",
                "confidence",
                "review_status",
                "notes",
            ],
        )
        writer.writeheader()
        writer.writerow(
            {
                "recipe_id": "bulk-0",
                "calories": "410",
                "protein_grams": "24",
                "carbs_grams": "44",
                "fats_grams": "10",
                "fiber_grams": "7",
                "source": "local_reference_ingredient_sum_draft",
                "confidence": "api_estimate",
                "review_status": "pending_review",
                "notes": "draft",
            }
        )

    summary = database.seed_nutrition_corrections([str(seed_path)])
    correction = database.get_nutrition_correction_by_recipe_id("bulk-0")

    assert summary["skippedExistingCount"] == 1
    assert correction["calories"] == 500


def test_correction_import_is_dry_run_until_apply():
    folder = _temp_dir()
    _use_temp_db(folder)
    corrections_path = folder / "corrections.csv"
    with corrections_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "recipe_id",
                "calories",
                "protein_grams",
                "carbs_grams",
                "fats_grams",
                "fiber_grams",
                "source",
                "confidence",
                "review_status",
                "notes",
            ],
        )
        writer.writeheader()
        writer.writerow(
            {
                "recipe_id": "recipe-reviewed-1",
                "calories": "390",
                "protein_grams": "34",
                "carbs_grams": "12",
                "fats_grams": "11",
                "fiber_grams": "6",
                "source": "external_api",
                "confidence": "high",
                "review_status": "reviewed",
                "notes": "checked by reviewer",
            }
        )

    dry_run = sync_nutrition.import_corrections(corrections_path, apply=False)

    assert dry_run[0]["recipeId"] == "recipe-reviewed-1"
    assert database.get_nutrition_correction_by_recipe_id("recipe-reviewed-1") is None

    saved = sync_nutrition.import_corrections(corrections_path, apply=True)

    assert saved[0]["recipeId"] == "recipe-reviewed-1"
    assert saved[0]["calories"] == 390
    assert "source=external_api" in (saved[0]["notes"] or "")
    assert "confidence=high" in (saved[0]["notes"] or "")


def test_repeated_placeholder_correction_batch_is_rejected():
    folder = _temp_dir()
    _use_temp_db(folder)
    corrections_path = folder / "repeated_corrections.csv"
    with corrections_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "recipe_id",
                "calories",
                "protein_grams",
                "carbs_grams",
                "fats_grams",
                "fiber_grams",
                "source",
                "confidence",
                "review_status",
            ],
        )
        writer.writeheader()
        for index in range(24):
            writer.writerow(
                {
                    "recipe_id": f"bulk-{index}",
                    "calories": "420",
                    "protein_grams": "25",
                    "carbs_grams": "45",
                    "fats_grams": "12",
                    "fiber_grams": "8",
                    "source": "manual_review",
                    "confidence": "high",
                    "review_status": "reviewed",
                }
            )

    try:
        sync_nutrition.import_corrections(corrections_path, apply=False)
    except ValueError as exc:
        assert "placeholder nutrition" in str(exc)
        assert "24/24 rows share one complete nutrition profile" in str(exc)
    else:
        raise AssertionError("Expected repeated placeholder nutrition corrections to be rejected")


def test_invalid_correction_without_values_is_rejected():
    try:
        sync_nutrition.normalize_correction(
            {
                "recipe_id": "bad-1",
                "source": "external_api",
                "confidence": "high",
                "review_status": "reviewed",
            }
        )
    except ValueError as exc:
        assert "at least one nutrition value is required" in str(exc)
    else:
        raise AssertionError("Expected correction without nutrition values to be rejected")


def test_low_confidence_pending_correction_is_rejected_by_default():
    try:
        sync_nutrition.normalize_correction(
            {
                "recipe_id": "pending-1",
                "calories": "400",
                "source": "external_api",
                "confidence": "low",
                "review_status": "pending",
            }
        )
    except ValueError as exc:
        assert "refusing to import" in str(exc)
    else:
        raise AssertionError("Expected low-confidence pending correction to be rejected")


def test_source_verified_correction_is_allowed_by_default():
    recipe_id, correction = sync_nutrition.normalize_correction(
        {
            "recipe_id": "source-verified-1",
            "calories": "435",
            "protein_grams": "53",
            "carbs_grams": "26",
            "fats_grams": "16",
            "fiber_grams": "9",
            "source": "panlasang_pinoy_recipe_card",
            "confidence": "high",
            "review_status": "source_verified",
        }
    )

    assert recipe_id == "source-verified-1"
    assert correction["calories"] == 435
    assert "review_status=source_verified" in correction["notes"]
