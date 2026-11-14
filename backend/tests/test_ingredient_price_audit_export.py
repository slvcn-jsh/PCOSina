import csv
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = ROOT / "backend"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database
import price_catalog
from scripts.export_ingredient_price_audit import (
    CANONICAL_COLUMNS,
    RAW_COLUMNS,
    build_audit_rows,
    canonicalize_ingredient,
    write_outputs,
)


def test_canonicalize_ingredient_groups_common_local_names():
    assert canonicalize_ingredient("8 cloves bawang minced") == ("garlic", "Garlic")
    assert canonicalize_ingredient("3 tablespoons soy sauce") == ("soy_sauce", "Soy Sauce")
    assert canonicalize_ingredient("1 1/2 lbs. pork belly diced") == ("pork_belly", "Pork Belly")


def test_export_rows_include_current_basis_and_blank_research_columns(monkeypatch, tmp_path):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(database, "list_market_multipliers_for_month", lambda month_index: {})
    monkeypatch.setattr(database, "get_market_multiplier", lambda category, month_index: 1.0)

    recipes = [
        {
            "id": "r1",
            "title": "Test Adobo",
            "mealType": "Dinner",
            "ingredients": [
                {"name": "8 cloves bawang minced", "quantity": ""},
                {"name": "3 tablespoons soy sauce", "quantity": ""},
            ],
        },
        {
            "id": "r2",
            "title": "Test Rice Bowl",
            "mealType": "Lunch",
            "ingredients": [{"name": "1 cup bigas", "quantity": ""}],
        },
    ]

    canonical_rows, raw_rows, summary = build_audit_rows(recipes, month_index=5)
    output_paths = write_outputs(tmp_path, canonical_rows, raw_rows, summary)

    assert summary["recipe_count"] == 2
    assert summary["raw_ingredient_occurrence_count"] == 3
    assert {row["canonical_key"] for row in canonical_rows} >= {"garlic", "soy_sauce", "rice"}
    assert all("real_price_php" in row and row["real_price_php"] == "" for row in canonical_rows)
    assert all(int(row["current_estimated_price_php"]) > 0 for row in raw_rows)

    with output_paths["canonical"].open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        assert reader.fieldnames == CANONICAL_COLUMNS
        assert len(list(reader)) == len(canonical_rows)

    with output_paths["raw"].open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        assert reader.fieldnames == RAW_COLUMNS
        assert len(list(reader)) == len(raw_rows)

    summary_payload = json.loads(output_paths["summary"].read_text(encoding="utf-8"))
    assert summary_payload["canonical_ingredient_count"] == len(canonical_rows)
