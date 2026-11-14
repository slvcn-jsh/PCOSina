import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = ROOT / "backend"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from scripts import validate_canonical_recipe_nutrition as nutrition_validation


def _validation_connection(review_status: str) -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(
        """
        CREATE TABLE ingredient_nutrition_refs (
            ingredient_id TEXT,
            calories_per_100g REAL,
            protein_per_100g REAL,
            carbs_per_100g REAL,
            fat_per_100g REAL,
            fiber_per_100g REAL,
            sodium_mg_per_100g REAL,
            sugar_per_100g REAL,
            source_name TEXT,
            confidence TEXT,
            review_status TEXT,
            active INTEGER,
            updated_at INTEGER
        );
        CREATE TABLE recipe_ingredient_links (
            recipe_id TEXT,
            ingredient_id TEXT,
            raw_ingredient_text TEXT,
            normalized_grams REAL,
            mapping_status TEXT,
            ingredient_index INTEGER
        );
        """
    )
    conn.execute(
        """
        INSERT INTO ingredient_nutrition_refs
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            "ing_egg",
            143,
            12.6,
            0.7,
            9.5,
            0,
            142,
            0.4,
            "test source",
            "high",
            review_status,
            1,
            1,
        ),
    )
    for index in range(100):
        conn.execute(
            "INSERT INTO recipe_ingredient_links VALUES (?, ?, ?, ?, ?, ?)",
            (f"recipe-{index}", "ing_egg", "100 g egg", 100, "mapped", index),
        )
    conn.commit()
    return conn


def _recipes() -> list[dict]:
    return [
        {
            "id": f"recipe-{index}",
            "title": f"Recipe {index}",
            "calories": 143,
            "proteinGrams": 12.6,
            "carbsGrams": 0.7,
            "fatsGrams": 9.5,
            "fiberGrams": 0,
            "sodiumMg": 142,
            "sugarGrams": 0.4,
        }
        for index in range(100)
    ]


def test_pending_nutrition_references_do_not_unlock_recipe_expansion(monkeypatch):
    conn = _validation_connection("pending_review")
    monkeypatch.setattr(nutrition_validation.database, "init_db", lambda: None)
    monkeypatch.setattr(nutrition_validation.database, "get_all_recipes", _recipes)
    monkeypatch.setattr(nutrition_validation.database, "_connect", lambda: conn)
    monkeypatch.setattr(nutrition_validation, "recipe_serving_count", lambda recipe: 1)

    _, summary = nutrition_validation.build_validation()

    assert summary["complete_recipe_count"] == 100
    assert summary["validated_complete_recipe_count"] == 0
    assert summary["expansion_gate_passed"] is False


def test_reviewed_complete_nutrition_references_unlock_recipe_expansion(monkeypatch):
    conn = _validation_connection("source_verified")
    monkeypatch.setattr(nutrition_validation.database, "init_db", lambda: None)
    monkeypatch.setattr(nutrition_validation.database, "get_all_recipes", _recipes)
    monkeypatch.setattr(nutrition_validation.database, "_connect", lambda: conn)
    monkeypatch.setattr(nutrition_validation, "recipe_serving_count", lambda recipe: 1)

    _, summary = nutrition_validation.build_validation()

    assert summary["validated_complete_recipe_count"] == 100
    assert summary["expansion_gate_passed"] is True
