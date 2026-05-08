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
