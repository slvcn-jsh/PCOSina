import json
import sys
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import diagnose_recipes
import sanitize_recipes


def _temp_dir() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_recipe_quality_tools"
    path = base / uuid4().hex
    path.mkdir(parents=True, exist_ok=True)
    return path


def _sample_recipes():
    return [
        {
            "id": "ph_494",
            "name": "Adobong Pusit Recipe (Note 1)",
            "ingredients": [
                {"name": "1 1/2 lbs squid (pusit)", "quantity": ""},
                {"name": "45 cloves garlic; crushed", "quantity": ""},
            ],
            "instructions": ["Saute garlic.", "Serve."],
            "tags": [],
            "minutes": 25,
        },
        {
            "id": "ph_4",
            "name": "How to Fry Pork Belly (Lechon Kawali)",
            "ingredients": [{"name": "3 lbs. pork belly", "quantity": ""}],
            "instructions": [
                "Boil pork belly.",
                "Soak it under the sun until the skin completely dries.",
                "Deep fry until crispy.",
            ],
            "tags": [],
            "minutes": 90,
        },
    ]


def test_diagnose_recipes_flags_known_quality_risks():
    issues = diagnose_recipes.diagnose_recipes(_sample_recipes())
    by_type = {issue["issue"] for issue in issues}

    assert "metadata_artifact" in by_type
    assert "extreme_ingredient_quantity" in by_type
    assert "weekend_complexity" in by_type
    assert "deep_fry_heavy" in by_type
    assert "missing_complexity_tag" in by_type


def test_sanitize_recipes_cleans_artifacts_and_known_typos():
    cleaned, changes = sanitize_recipes.sanitize_recipes(_sample_recipes())
    pusit = next(recipe for recipe in cleaned if recipe["id"] == "ph_494")
    lechon = next(recipe for recipe in cleaned if recipe["id"] == "ph_4")

    assert pusit["name"] == "Adobong Pusit Recipe"
    assert pusit["ingredients"][1]["name"] == "4-5 cloves garlic; crushed"
    assert "quick" in pusit["tags"]
    assert "needs_review" not in pusit["tags"]
    assert "weekend" in lechon["tags"]
    assert "needs_review" in lechon["tags"]
    assert any(change["field"] == "ingredient.name" for change in changes)


def test_sanitize_main_is_dry_run_unless_apply():
    folder = _temp_dir()
    recipes_path = folder / "recipes.json"
    original = _sample_recipes()
    recipes_path.write_text(json.dumps(original, ensure_ascii=True, indent=2), encoding="utf-8")

    # Exercise the core functions here to avoid relying on process-level argv.
    cleaned, _ = sanitize_recipes.sanitize_recipes(sanitize_recipes.load_recipes(recipes_path))

    assert json.loads(recipes_path.read_text(encoding="utf-8")) == original
    sanitize_recipes.write_recipes(recipes_path, cleaned)
    assert json.loads(recipes_path.read_text(encoding="utf-8")) != original
