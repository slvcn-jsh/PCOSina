"""Truth-preserving nutrition correction pipeline.

This script intentionally does not invent nutrition facts. It can:

1. Audit recipes with missing nutrition in recipes.json.
2. Import reviewed/API-produced nutrition corrections from CSV or JSON.
3. Store corrections in recipe_nutrition_corrections without modifying recipes.json.

Expected CSV columns:
recipe_id,calories,protein_grams,carbs_grams,fats_grams,fiber_grams,sodium_mg,sugar_grams,source,confidence,review_status,notes

JSON input may be either a list of objects or {"items": [...]} using camelCase
or snake_case field names.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path
from typing import Any, Iterable

BACKEND_ROOT = Path(__file__).resolve().parent
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402


NUTRITION_FIELDS = {
    "calories": ("calories",),
    "proteinGrams": ("proteinGrams", "protein_grams", "protein_g"),
    "carbsGrams": ("carbsGrams", "carbs_grams", "carbs_g"),
    "fatsGrams": ("fatsGrams", "fats_grams", "fat_g"),
    "fiberGrams": ("fiberGrams", "fiber_grams", "fiber_g"),
    "sodiumMg": ("sodiumMg", "sodium_mg"),
    "sugarGrams": ("sugarGrams", "sugar_grams"),
}

DEFAULT_RECIPE_FILE = BACKEND_ROOT / "recipes.json"
TRUSTED_REVIEW_STATUSES = {"reviewed", "nutritionist_reviewed", "dietitian_reviewed", "verified"}
ACCEPTED_CONFIDENCE = {"high", "medium", "api_estimate", "reviewed"}


def load_recipes(recipe_file: Path = DEFAULT_RECIPE_FILE) -> list[dict[str, Any]]:
    return json.loads(recipe_file.read_text(encoding="utf-8"))


def has_complete_nutrition(recipe: dict[str, Any]) -> bool:
    nutrition = recipe.get("nutrition") or {}
    required = ("calories", "protein_g", "carbs_g", "fat_g", "fiber_g")
    return all(_positive_int(nutrition.get(key)) is not None for key in required)


def missing_nutrition_recipes(recipes: Iterable[dict[str, Any]]) -> list[dict[str, str]]:
    missing: list[dict[str, str]] = []
    for recipe in recipes:
        if has_complete_nutrition(recipe):
            continue
        recipe_id = str(recipe.get("id") or "").strip()
        if not recipe_id:
            continue
        missing.append(
            {
                "id": recipe_id,
                "title": str(recipe.get("name") or recipe.get("title") or "Untitled").strip(),
            }
        )
    return missing


def load_corrections(path: Path) -> list[dict[str, Any]]:
    suffix = path.suffix.lower()
    if suffix == ".csv":
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            return [dict(row) for row in csv.DictReader(handle)]
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, dict):
        payload = payload.get("items") or payload.get("corrections") or []
    if not isinstance(payload, list):
        raise ValueError("Nutrition correction JSON must be a list or an object with items/corrections.")
    return [dict(item) for item in payload if isinstance(item, dict)]


def normalize_correction(row: dict[str, Any], *, require_reviewed: bool = True) -> tuple[str, dict[str, Any]]:
    recipe_id = _string_value(row, "recipe_id", "recipeId", "id")
    if not recipe_id:
        raise ValueError("recipe_id is required")
    source = _string_value(row, "source", "nutrition_source") or "external_nutrition_analysis"
    confidence = (_string_value(row, "confidence", "nutrition_confidence") or "").lower()
    review_status = (_string_value(row, "review_status", "reviewStatus") or "pending_review").lower()
    if require_reviewed and review_status not in TRUSTED_REVIEW_STATUSES and confidence not in ACCEPTED_CONFIDENCE:
        raise ValueError(
            f"{recipe_id}: refusing to import unreviewed low-confidence nutrition correction "
            f"(confidence={confidence or 'blank'}, review_status={review_status})"
        )

    correction: dict[str, Any] = {"active": _bool_value(row.get("active"), default=True)}
    provided_any = False
    for output_key, aliases in NUTRITION_FIELDS.items():
        raw = _first_present(row, aliases)
        parsed = _non_negative_int(raw)
        if parsed is not None:
            correction[output_key] = parsed
            provided_any = True
    if not provided_any:
        raise ValueError(f"{recipe_id}: at least one nutrition value is required")
    notes = _string_value(row, "notes") or ""
    correction["notes"] = _metadata_notes(
        source=source,
        confidence=confidence or "medium",
        review_status=review_status,
        notes=notes,
    )
    return recipe_id, correction


def import_corrections(path: Path, *, apply: bool, require_reviewed: bool = True) -> list[dict[str, Any]]:
    database.init_db()
    rows = load_corrections(path)
    normalized = [normalize_correction(row, require_reviewed=require_reviewed) for row in rows]
    if not apply:
        return [
            {"recipeId": recipe_id, **correction}
            for recipe_id, correction in normalized
        ]
    saved = []
    for recipe_id, correction in normalized:
        saved.append(database.upsert_nutrition_correction(recipe_id, correction))
    return saved


def write_missing_report(path: Path, recipe_file: Path = DEFAULT_RECIPE_FILE) -> list[dict[str, str]]:
    missing = missing_nutrition_recipes(load_recipes(recipe_file))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["id", "title"])
        writer.writeheader()
        writer.writerows(missing)
    return missing


def _metadata_notes(source: str, confidence: str, review_status: str, notes: str) -> str:
    chunks = [
        f"source={source}",
        f"confidence={confidence}",
        f"review_status={review_status}",
    ]
    if notes:
        chunks.append(f"notes={notes}")
    return "; ".join(chunks)


def _positive_int(value: Any) -> int | None:
    parsed = _non_negative_int(value)
    if parsed is None or parsed <= 0:
        return None
    return parsed


def _non_negative_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return max(0, int(float(str(value).strip())))
    except Exception:
        return None


def _first_present(row: dict[str, Any], aliases: Iterable[str]) -> Any:
    for alias in aliases:
        if alias in row and row[alias] not in (None, ""):
            return row[alias]
    return None


def _string_value(row: dict[str, Any], *keys: str) -> str:
    for key in keys:
        if key in row and row[key] is not None:
            value = str(row[key]).strip()
            if value:
                return value
    return ""


def _bool_value(value: Any, *, default: bool) -> bool:
    if value is None or value == "":
        return default
    return str(value).strip().lower() not in {"0", "false", "no", "inactive"}


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit or import PCOSina nutrition corrections.")
    parser.add_argument("--recipes", type=Path, default=DEFAULT_RECIPE_FILE, help="Path to recipes.json.")
    parser.add_argument("--report-missing", type=Path, help="Write CSV report of recipes with missing raw nutrition.")
    parser.add_argument("--input", type=Path, help="CSV/JSON nutrition correction file to validate or import.")
    parser.add_argument("--apply", action="store_true", help="Write imported corrections to the database.")
    parser.add_argument(
        "--allow-pending",
        action="store_true",
        help="Allow pending/low-confidence correction rows. Avoid this for production imports.",
    )
    args = parser.parse_args()

    if args.report_missing:
        missing = write_missing_report(args.report_missing, args.recipes)
        print(f"Wrote {len(missing)} missing-nutrition row(s) to {args.report_missing}")
    if args.input:
        saved = import_corrections(args.input, apply=args.apply, require_reviewed=not args.allow_pending)
        action = "Imported" if args.apply else "Validated"
        print(f"{action} {len(saved)} nutrition correction row(s).")
    if not args.report_missing and not args.input:
        missing_count = len(missing_nutrition_recipes(load_recipes(args.recipes)))
        print(f"{missing_count} recipe(s) have incomplete raw nutrition. Use --report-missing or --input.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
