import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import sync_recipe_source_metadata


def test_duration_to_minutes_parses_source_times():
    assert sync_recipe_source_metadata.duration_to_minutes("1 hour 5 minutes") == 65
    assert sync_recipe_source_metadata.duration_to_minutes("2 hours") == 120
    assert sync_recipe_source_metadata.duration_to_minutes("35 minutes") == 35
    assert sync_recipe_source_metadata.duration_to_minutes("") is None


def test_sync_source_metadata_adds_servings_and_minutes_without_touching_curated_rows():
    recipes = [
        {"id": "ph_1", "name": "Ginataang Kalabasa", "nutrition": {}, "ingredients": [], "instructions": []},
        {"id": "ph_qk_001", "name": "Curated", "minutes": 12, "nutrition": {}, "ingredients": [], "instructions": []},
    ]
    rows = [
        {
            "source_row_index": 0,
            "recipe_name": "Ginataang Kalabasa",
            "prep_time": "15 minutes",
            "cook_time": "50 minutes",
            "total_time": "1 hour 5 minutes",
            "servings": "6",
            "ingredient_names": "kalabasa | sitaw | pork belly",
        }
    ]

    synced, changes = sync_recipe_source_metadata.sync_source_metadata(recipes, rows)

    assert synced[0]["minutes"] == 65
    assert synced[0]["sourceServings"] == "6"
    assert synced[0]["sourcePrepTime"] == "15 minutes"
    assert synced[0]["sourceCookTime"] == "50 minutes"
    assert synced[0]["sourceTotalTime"] == "1 hour 5 minutes"
    assert synced[0]["sourceIngredientNames"] == "kalabasa | sitaw | pork belly"
    assert synced[0]["sourceDataset"] == sync_recipe_source_metadata.HF_DATASET
    assert synced[1]["minutes"] == 12
    assert all(change["id"] == "ph_1" for change in changes)
