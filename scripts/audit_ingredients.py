"""Audit recipe ingredient coverage against the canonical ingredient seed.

The script is read-only. It never changes recipes or recipe_ingredient_links.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402
from canonical_ingredients import (  # noqa: E402
    ingredient_name_and_quantity,
    provisional_ingredient,
    resolve_ingredient,
)


DEFAULT_RECIPES_JSON = BACKEND_ROOT / "recipes.json"
DEFAULT_OUTPUT_DIR = BACKEND_ROOT / "output" / "ingredient_audit"

RAW_COLUMNS = [
    "recipe_id",
    "recipe_title",
    "meal_type",
    "ingredient_index",
    "raw_ingredient_text",
    "raw_quantity_text",
    "normalized_text",
    "mapping_status",
    "ingredient_id",
    "canonical_name",
    "matched_alias",
    "candidate_ids",
    "mapping_confidence",
    "classification_status",
    "classified_ingredient_id",
    "classification_quality_status",
    "parsed_quantity_value",
    "parsed_quantity_unit",
    "needs_review",
]

UNIQUE_COLUMNS = [
    "raw_ingredient_text",
    "occurrence_count",
    "recipe_count",
    "mapping_status",
    "ingredient_id",
    "canonical_name",
    "matched_alias",
    "candidate_ids",
    "mapping_confidence",
    "classification_status",
    "classified_ingredient_id",
    "classification_quality_status",
    "sample_recipe_ids",
]

TOKEN_COLUMNS = ["token", "occurrence_count", "recipe_count"]
TOP_COLUMNS = ["ingredient_id", "canonical_name", "occurrence_count", "recipe_count", "sample_raw_ingredients"]
RECIPE_COLUMNS = [
    "recipe_id",
    "recipe_title",
    "ingredient_count",
    "mapped_count",
    "unmapped_count",
    "ambiguous_count",
    "quantity_parsed_count",
    "unit_parsed_count",
    "identity_coverage_pct",
    "quantity_coverage_pct",
    "unit_coverage_pct",
    "quality_level",
]


def _load_json_recipes(path: Path) -> list[dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError(f"Expected a recipe list in {path}")
    return [item for item in payload if isinstance(item, dict)]


def load_recipes(source: str, recipes_json: Path = DEFAULT_RECIPES_JSON) -> tuple[list[dict[str, Any]], str]:
    normalized_source = source.strip().lower()
    if normalized_source not in {"auto", "db", "json"}:
        raise ValueError("source must be one of: auto, db, json")
    if normalized_source in {"auto", "db"}:
        recipes = database.get_all_recipes()
        if recipes:
            return recipes, "db"
        if normalized_source == "db":
            raise RuntimeError("No active recipes were found in the database.")
    return _load_json_recipes(recipes_json), "json"


def _sample(values: Iterable[str], limit: int = 8) -> str:
    result: list[str] = []
    for value in values:
        token = str(value or "").strip()
        if token and token not in result:
            result.append(token)
        if len(result) >= limit:
            break
    return " | ".join(result)


def _quality_level(ingredient_count: int, mapped: int, ambiguous: int, quantity: int, unit: int) -> str:
    if ingredient_count == 0:
        return "E"
    identity_coverage = mapped / ingredient_count
    quantity_coverage = quantity / ingredient_count
    unit_coverage = unit / ingredient_count
    if identity_coverage == 1 and ambiguous == 0 and quantity_coverage == 1 and unit_coverage == 1:
        return "A"
    if identity_coverage >= 0.95 and ambiguous == 0 and quantity_coverage >= 0.8:
        return "B"
    if identity_coverage >= 0.8 and ambiguous == 0:
        return "C"
    if identity_coverage > 0:
        return "D"
    return "E"


def build_audit(
    recipes: list[dict[str, Any]],
    *,
    complete_classification: bool = False,
) -> tuple[dict[str, list[dict[str, Any]]], dict[str, Any]]:
    raw_rows: list[dict[str, Any]] = []
    unique_groups: dict[str, dict[str, Any]] = defaultdict(
        lambda: {"rows": [], "recipe_ids": []}
    )
    token_counts: Counter[str] = Counter()
    token_recipes: dict[str, set[str]] = defaultdict(set)
    canonical_groups: dict[str, dict[str, Any]] = defaultdict(
        lambda: {"canonical_name": "", "occurrences": 0, "recipe_ids": set(), "raw": []}
    )
    recipe_rows: list[dict[str, Any]] = []

    for recipe in recipes:
        recipe_id = str(recipe.get("id") or "").strip()
        title = str(recipe.get("title") or recipe.get("name") or "").strip()
        meal_type = str(recipe.get("mealType") or recipe.get("meal_type") or "").strip()
        mapped_count = 0
        unmapped_count = 0
        ambiguous_count = 0
        quantity_count = 0
        unit_count = 0
        ingredients = recipe.get("ingredients") or []

        for ingredient_index, ingredient in enumerate(ingredients):
            raw_name, raw_quantity = ingredient_name_and_quantity(ingredient)
            if not raw_name:
                continue
            combined_text = f"{raw_quantity} {raw_name}".strip()
            resolution = resolve_ingredient(combined_text)
            provisional = None
            if complete_classification and resolution.status != "mapped":
                provisional = provisional_ingredient(combined_text, resolution.status)
            if resolution.status == "mapped":
                mapped_count += 1
            elif resolution.status == "ambiguous":
                ambiguous_count += 1
            else:
                unmapped_count += 1
            if resolution.quantity_value is not None:
                quantity_count += 1
            if resolution.quantity_unit:
                unit_count += 1

            row = {
                "recipe_id": recipe_id,
                "recipe_title": title,
                "meal_type": meal_type,
                "ingredient_index": ingredient_index,
                "raw_ingredient_text": raw_name,
                "raw_quantity_text": raw_quantity,
                "normalized_text": resolution.normalized_text,
                "mapping_status": resolution.status,
                "ingredient_id": resolution.ingredient_id or "",
                "canonical_name": resolution.canonical_name or "",
                "matched_alias": resolution.matched_alias or "",
                "candidate_ids": " | ".join(resolution.candidate_ids),
                "mapping_confidence": resolution.confidence,
                "classification_status": (
                    "curated" if resolution.status == "mapped" else
                    "provisional" if provisional else resolution.status
                ),
                "classified_ingredient_id": (
                    resolution.ingredient_id or
                    (provisional.ingredient_id if provisional else "")
                ),
                "classification_quality_status": (
                    "seeded" if resolution.status == "mapped" else
                    (provisional.quality_status if provisional else "")
                ),
                "parsed_quantity_value": resolution.quantity_value if resolution.quantity_value is not None else "",
                "parsed_quantity_unit": resolution.quantity_unit or "",
                "needs_review": "yes" if resolution.status != "mapped" else "no",
            }
            raw_rows.append(row)
            unique_groups[raw_name.casefold()]["rows"].append(row)
            unique_groups[raw_name.casefold()]["recipe_ids"].append(recipe_id)

            for token in re.findall(r"[a-z0-9]+", resolution.normalized_text):
                token_counts[token] += 1
                token_recipes[token].add(recipe_id)

            if resolution.ingredient_id:
                group = canonical_groups[resolution.ingredient_id]
                group["canonical_name"] = resolution.canonical_name or ""
                group["occurrences"] += 1
                group["recipe_ids"].add(recipe_id)
                group["raw"].append(raw_name)

        ingredient_count = mapped_count + unmapped_count + ambiguous_count
        recipe_rows.append(
            {
                "recipe_id": recipe_id,
                "recipe_title": title,
                "ingredient_count": ingredient_count,
                "mapped_count": mapped_count,
                "unmapped_count": unmapped_count,
                "ambiguous_count": ambiguous_count,
                "quantity_parsed_count": quantity_count,
                "unit_parsed_count": unit_count,
                "identity_coverage_pct": round(100 * mapped_count / max(1, ingredient_count), 2),
                "quantity_coverage_pct": round(100 * quantity_count / max(1, ingredient_count), 2),
                "unit_coverage_pct": round(100 * unit_count / max(1, ingredient_count), 2),
                "quality_level": _quality_level(
                    ingredient_count, mapped_count, ambiguous_count, quantity_count, unit_count
                ),
            }
        )

    unique_rows: list[dict[str, Any]] = []
    for group in unique_groups.values():
        rows = group["rows"]
        first = rows[0]
        unique_rows.append(
            {
                "raw_ingredient_text": first["raw_ingredient_text"],
                "occurrence_count": len(rows),
                "recipe_count": len(set(group["recipe_ids"])),
                "mapping_status": first["mapping_status"],
                "ingredient_id": first["ingredient_id"],
                "canonical_name": first["canonical_name"],
                "matched_alias": first["matched_alias"],
                "candidate_ids": first["candidate_ids"],
                "mapping_confidence": first["mapping_confidence"],
                "classification_status": first["classification_status"],
                "classified_ingredient_id": first["classified_ingredient_id"],
                "classification_quality_status": first["classification_quality_status"],
                "sample_recipe_ids": _sample(group["recipe_ids"]),
            }
        )

    token_rows = [
        {
            "token": token,
            "occurrence_count": count,
            "recipe_count": len(token_recipes[token]),
        }
        for token, count in token_counts.items()
    ]
    top_rows = [
        {
            "ingredient_id": ingredient_id,
            "canonical_name": group["canonical_name"],
            "occurrence_count": group["occurrences"],
            "recipe_count": len(group["recipe_ids"]),
            "sample_raw_ingredients": _sample(group["raw"]),
        }
        for ingredient_id, group in canonical_groups.items()
    ]

    raw_rows.sort(key=lambda row: (row["mapping_status"], row["raw_ingredient_text"].casefold(), row["recipe_id"]))
    unique_rows.sort(key=lambda row: (-int(row["occurrence_count"]), row["raw_ingredient_text"].casefold()))
    token_rows.sort(key=lambda row: (-int(row["occurrence_count"]), row["token"]))
    top_rows.sort(key=lambda row: (-int(row["recipe_count"]), row["ingredient_id"]))
    recipe_rows.sort(key=lambda row: (float(row["identity_coverage_pct"]), row["recipe_id"]))

    status_counts = Counter(row["mapping_status"] for row in raw_rows)
    summary = {
        "recipe_count": len(recipes),
        "ingredient_occurrence_count": len(raw_rows),
        "unique_raw_ingredient_count": len(unique_rows),
        "mapped_occurrence_count": status_counts.get("mapped", 0),
        "unmapped_occurrence_count": status_counts.get("unmapped", 0),
        "ambiguous_occurrence_count": status_counts.get("ambiguous", 0),
        "mapping_coverage_pct": round(
            100 * status_counts.get("mapped", 0) / max(1, len(raw_rows)), 2
        ),
        "classified_occurrence_count": sum(
            1 for row in raw_rows if row["classified_ingredient_id"]
        ),
        "classification_coverage_pct": round(
            100 * sum(1 for row in raw_rows if row["classified_ingredient_id"]) / max(1, len(raw_rows)),
            2,
        ),
        "provisional_occurrence_count": sum(
            1 for row in raw_rows if row["classification_status"] == "provisional"
        ),
        "recipes_with_full_identity_coverage": sum(
            1 for row in recipe_rows if float(row["identity_coverage_pct"]) == 100
        ),
        "quality_level_counts": dict(sorted(Counter(row["quality_level"] for row in recipe_rows).items())),
        "note": "Read-only shadow audit; planner and keyword/token fallbacks remain unchanged.",
    }
    outputs = {
        "raw": raw_rows,
        "unique": unique_rows,
        "tokens": token_rows,
        "unmapped": [row for row in unique_rows if row["mapping_status"] == "unmapped"],
        "ambiguous": [row for row in unique_rows if row["mapping_status"] == "ambiguous"],
        "top": top_rows,
        "recipes": recipe_rows,
    }
    return outputs, summary


def _write_csv(path: Path, columns: list[str], rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def write_outputs(
    output_dir: Path,
    outputs: dict[str, list[dict[str, Any]]],
    summary: dict[str, Any],
) -> dict[str, Path]:
    paths = {
        "raw": output_dir / "raw_ingredient_occurrences.csv",
        "unique": output_dir / "unique_raw_ingredient_strings.csv",
        "tokens": output_dir / "ingredient_token_frequency.csv",
        "unmapped": output_dir / "unmapped_ingredients.csv",
        "ambiguous": output_dir / "ambiguous_ingredients.csv",
        "top": output_dir / "top_ingredients_by_recipe_count.csv",
        "recipes": output_dir / "recipes_by_mapping_coverage.csv",
        "summary": output_dir / "mapping_summary.json",
    }
    _write_csv(paths["raw"], RAW_COLUMNS, outputs["raw"])
    _write_csv(paths["unique"], UNIQUE_COLUMNS, outputs["unique"])
    _write_csv(paths["tokens"], TOKEN_COLUMNS, outputs["tokens"])
    _write_csv(paths["unmapped"], UNIQUE_COLUMNS, outputs["unmapped"])
    _write_csv(paths["ambiguous"], UNIQUE_COLUMNS, outputs["ambiguous"])
    _write_csv(paths["top"], TOP_COLUMNS, outputs["top"])
    _write_csv(paths["recipes"], RECIPE_COLUMNS, outputs["recipes"])
    paths["summary"].write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return paths


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", choices=("auto", "db", "json"), default="auto")
    parser.add_argument("--recipes-json", type=Path, default=DEFAULT_RECIPES_JSON)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument(
        "--strict-only",
        action="store_true",
        help="Do not assign provisional IDs to unresolved phrases.",
    )
    args = parser.parse_args()

    recipes, source = load_recipes(args.source, args.recipes_json)
    outputs, summary = build_audit(
        recipes,
        complete_classification=not args.strict_only,
    )
    summary["source"] = source
    summary["recipes_json"] = str(args.recipes_json)
    paths = write_outputs(args.output_dir, outputs, summary)
    print(json.dumps({"summary": summary, "outputs": {key: str(value) for key, value in paths.items()}}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
