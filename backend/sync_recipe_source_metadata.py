"""Sync source recipe metadata that was lost during the first HF export.

The Filipino 1K source dataset includes prep time, cook time, total time, and
serving count. Those fields are not nutrition facts, but they are required
inputs for credible per-serving nutrition review.
"""

from __future__ import annotations

import argparse
import json
import re
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


BACKEND_ROOT = Path(__file__).resolve().parent
DEFAULT_RECIPE_FILE = BACKEND_ROOT / "recipes.json"
DEFAULT_ROWS_CACHE = BACKEND_ROOT / "seed_data" / "hf_filipino_recipes_1k_rows.json"
HF_ROWS_ENDPOINT = "https://datasets-server.huggingface.co/rows"
HF_DATASET = "joackimagno/FILIPINO_RECIPES_1K"
HF_CONFIG = "default"
HF_SPLIT = "train"


def load_recipes(path: Path = DEFAULT_RECIPE_FILE) -> list[dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError("recipes.json must contain a list of recipes")
    return [item for item in payload if isinstance(item, dict)]


def fetch_hf_rows(page_size: int = 100) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    offset = 0
    total: int | None = None
    while total is None or offset < total:
        query = urllib.parse.urlencode(
            {
                "dataset": HF_DATASET,
                "config": HF_CONFIG,
                "split": HF_SPLIT,
                "offset": offset,
                "length": page_size,
            }
        )
        with urllib.request.urlopen(f"{HF_ROWS_ENDPOINT}?{query}", timeout=30) as response:
            payload = json.loads(response.read().decode("utf-8"))
        total = int(payload.get("num_rows_total") or 0)
        page_rows = payload.get("rows") or []
        for item in page_rows:
            if isinstance(item, dict) and isinstance(item.get("row"), dict):
                row = dict(item["row"])
                row["source_row_index"] = int(item.get("row_idx") or len(rows))
                rows.append(row)
        offset += max(1, len(page_rows))
        if not page_rows:
            break
    return rows


def load_or_fetch_rows(cache_path: Path) -> list[dict[str, Any]]:
    if cache_path.exists():
        payload = json.loads(cache_path.read_text(encoding="utf-8"))
        if isinstance(payload, dict):
            payload = payload.get("rows") or []
        if not isinstance(payload, list):
            raise ValueError("Rows cache must be a list or an object with rows.")
        return [dict(item) for item in payload if isinstance(item, dict)]
    rows = fetch_hf_rows()
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    cache_path.write_text(json.dumps({"dataset": HF_DATASET, "rows": rows}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return rows


def sync_source_metadata(recipes: list[dict[str, Any]], rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    cleaned = json.loads(json.dumps(recipes, ensure_ascii=False))
    changes: list[dict[str, Any]] = []
    rows_by_id = {f"ph_{int(row.get('source_row_index') or 0) + 1}": row for row in rows}
    for recipe in cleaned:
        recipe_id = str(recipe.get("id") or "").strip()
        source = rows_by_id.get(recipe_id)
        if not source:
            continue
        _set_if_changed(changes, recipe, recipe_id, "sourceDataset", HF_DATASET)
        _set_if_changed(changes, recipe, recipe_id, "sourceRowIndex", int(source.get("source_row_index") or 0))
        _set_if_changed(changes, recipe, recipe_id, "sourceServings", str(source.get("servings") or "").strip())
        _set_if_changed(changes, recipe, recipe_id, "sourcePrepTime", str(source.get("prep_time") or "").strip())
        _set_if_changed(changes, recipe, recipe_id, "sourceCookTime", str(source.get("cook_time") or "").strip())
        _set_if_changed(changes, recipe, recipe_id, "sourceTotalTime", str(source.get("total_time") or "").strip())
        ingredient_names = str(source.get("ingredient_names") or "").strip()
        if ingredient_names:
            _set_if_changed(changes, recipe, recipe_id, "sourceIngredientNames", ingredient_names)
        total_minutes = duration_to_minutes(str(source.get("total_time") or ""))
        if total_minutes and not _positive_int(recipe.get("minutes")):
            _set_if_changed(changes, recipe, recipe_id, "minutes", total_minutes)
    return cleaned, changes


def duration_to_minutes(value: str) -> int | None:
    text = str(value or "").strip().lower()
    if not text:
        return None
    hours = 0
    minutes = 0
    for amount, unit in re.findall(r"(\d+(?:\.\d+)?)\s*(hours?|hrs?|minutes?|mins?)", text):
        parsed = float(amount)
        if unit.startswith(("hour", "hr")):
            hours += int(round(parsed))
        else:
            minutes += int(round(parsed))
    total = hours * 60 + minutes
    return total if total > 0 else None


def write_recipes(path: Path, recipes: list[dict[str, Any]]) -> None:
    path.write_text(json.dumps(recipes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_report(path: Path, changes: list[dict[str, Any]], recipe_count: int, row_count: int, applied: bool) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    field_counts: dict[str, int] = {}
    for change in changes:
        field = str(change.get("field") or "unknown")
        field_counts[field] = int(field_counts.get(field, 0)) + 1
    payload = {
        "applied": applied,
        "recipeCount": recipe_count,
        "sourceRowCount": row_count,
        "changeCount": len(changes),
        "fieldCounts": dict(sorted(field_counts.items())),
        "changes": changes,
    }
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding="utf-8")


def _set_if_changed(changes: list[dict[str, Any]], recipe: dict[str, Any], recipe_id: str, field: str, value: Any) -> None:
    if value in (None, ""):
        return
    before = recipe.get(field)
    if before == value:
        return
    recipe[field] = value
    changes.append({"id": recipe_id, "field": field, "before": before, "after": value})


def _positive_int(value: Any) -> int | None:
    try:
        parsed = int(float(str(value).strip()))
    except Exception:
        return None
    return parsed if parsed > 0 else None


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync upstream source metadata into PCOSina recipes.json.")
    parser.add_argument("--recipes", type=Path, default=DEFAULT_RECIPE_FILE)
    parser.add_argument("--rows-cache", type=Path, default=DEFAULT_ROWS_CACHE)
    parser.add_argument("--report", type=Path, help="Write JSON sync report.")
    parser.add_argument("--apply", action="store_true", help="Write metadata updates back to recipes.json.")
    args = parser.parse_args()

    recipes = load_recipes(args.recipes)
    rows = load_or_fetch_rows(args.rows_cache)
    synced, changes = sync_source_metadata(recipes, rows)
    print(f"Prepared {len(changes)} source metadata change(s) across {len(recipes)} recipe(s).")
    if args.report:
        write_report(args.report, changes, len(recipes), len(rows), bool(args.apply))
        print(f"Wrote source metadata report to {args.report}")
    if args.apply:
        write_recipes(args.recipes, synced)
        print(f"Applied source metadata to {args.recipes}")
    else:
        print("Dry run only. Re-run with --apply to write changes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
