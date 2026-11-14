"""Validate recipe nutrition from canonical links, normalized grams, and refs."""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402
from services.meal_planner import recipe_serving_count  # noqa: E402


DEFAULT_OUTPUT_DIR = BACKEND_ROOT / "output" / "canonical_nutrition_validation"
NUTRIENTS = (
    ("calories", "calories_per_100g"),
    ("proteinGrams", "protein_per_100g"),
    ("carbsGrams", "carbs_per_100g"),
    ("fatsGrams", "fat_per_100g"),
    ("fiberGrams", "fiber_per_100g"),
    ("sodiumMg", "sodium_mg_per_100g"),
    ("sugarGrams", "sugar_per_100g"),
)


def build_validation() -> tuple[list[dict[str, Any]], dict[str, Any]]:
    database.init_db()
    recipes = {str(item.get("id") or ""): item for item in database.get_all_recipes()}
    conn = database._connect()  # noqa: SLF001
    try:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT ingredient_id, calories_per_100g, protein_per_100g,
                   carbs_per_100g, fat_per_100g, fiber_per_100g,
                   sodium_mg_per_100g, sugar_per_100g, source_name,
                   confidence, review_status
            FROM ingredient_nutrition_refs
            WHERE active = 1
            ORDER BY ingredient_id, updated_at DESC
            """
        )
        refs: dict[str, dict[str, Any]] = {}
        for row in cur.fetchall():
            raw = dict(row) if hasattr(row, "keys") else {
                "ingredient_id": row[0],
                "calories_per_100g": row[1],
                "protein_per_100g": row[2],
                "carbs_per_100g": row[3],
                "fat_per_100g": row[4],
                "fiber_per_100g": row[5],
                "sodium_mg_per_100g": row[6],
                "sugar_per_100g": row[7],
                "source_name": row[8],
                "confidence": row[9],
                "review_status": row[10],
            }
            refs.setdefault(str(raw["ingredient_id"]), raw)

        cur.execute(
            """
            SELECT recipe_id, ingredient_id, raw_ingredient_text,
                   normalized_grams, mapping_status
            FROM recipe_ingredient_links
            ORDER BY recipe_id, ingredient_index
            """
        )
        links_by_recipe: dict[str, list[dict[str, Any]]] = {}
        for row in cur.fetchall():
            raw = dict(row) if hasattr(row, "keys") else {
                "recipe_id": row[0],
                "ingredient_id": row[1],
                "raw_ingredient_text": row[2],
                "normalized_grams": row[3],
                "mapping_status": row[4],
            }
            links_by_recipe.setdefault(str(raw["recipe_id"]), []).append(raw)
    finally:
        conn.close()

    rows: list[dict[str, Any]] = []
    for recipe_id, recipe in recipes.items():
        links = links_by_recipe.get(recipe_id, [])
        totals = {name: 0.0 for name, _ in NUTRIENTS}
        covered = 0
        reviewed_covered = 0
        grams_count = 0
        missing_grams: list[str] = []
        missing_refs: list[str] = []
        provisional = 0
        for link in links:
            ingredient_id = str(link.get("ingredient_id") or "")
            grams = link.get("normalized_grams")
            if str(link.get("mapping_status") or "") != "mapped":
                provisional += 1
            if grams is None or float(grams or 0) <= 0:
                missing_grams.append(str(link.get("raw_ingredient_text") or ""))
                continue
            grams_count += 1
            ref = refs.get(ingredient_id)
            if not ref:
                missing_refs.append(ingredient_id)
                continue
            covered += 1
            if str(ref.get("review_status") or "").strip().lower() in {
                "reviewed", "verified", "source_verified", "nutritionist_reviewed", "dietitian_reviewed"
            }:
                reviewed_covered += 1
            factor = float(grams) / 100.0
            for output_name, ref_name in NUTRIENTS:
                totals[output_name] += float(ref.get(ref_name) or 0.0) * factor

        servings = recipe_serving_count(recipe)
        if servings and servings > 0:
            totals = {key: value / servings for key, value in totals.items()}
        link_count = len(links)
        complete = bool(link_count and covered == link_count and servings)
        validated_complete = bool(link_count and reviewed_covered == link_count and servings)
        status = "complete" if complete else "partial" if covered else "not_computable"
        row = {
            "recipe_id": recipe_id,
            "title": recipe.get("title") or "",
            "link_count": link_count,
            "links_with_grams": grams_count,
            "links_with_nutrition_refs": covered,
            "links_with_reviewed_nutrition_refs": reviewed_covered,
            "provisional_link_count": provisional,
            "source_servings": servings or "",
            "validation_status": status,
            "quantity_coverage_pct": round(100 * grams_count / max(1, link_count), 2),
            "nutrition_ref_coverage_pct": round(100 * covered / max(1, link_count), 2),
            "reviewed_nutrition_ref_coverage_pct": round(100 * reviewed_covered / max(1, link_count), 2),
            "validated_complete": validated_complete,
            "missing_grams": " | ".join(missing_grams[:12]),
            "missing_refs": " | ".join(sorted(set(missing_refs))[:12]),
        }
        for output_name, _ in NUTRIENTS:
            computed = round(totals[output_name], 1) if covered else ""
            catalog = recipe.get(output_name)
            row[f"computed_{output_name}"] = computed
            row[f"catalog_{output_name}"] = catalog if catalog is not None else ""
            row[f"delta_{output_name}"] = (
                round(float(computed) - float(catalog), 1)
                if computed != "" and catalog not in (None, "")
                else ""
            )
        rows.append(row)

    rows.sort(key=lambda row: (row["validation_status"], float(row["nutrition_ref_coverage_pct"]), row["recipe_id"]))
    status_counts: dict[str, int] = {}
    for row in rows:
        status_counts[row["validation_status"]] = status_counts.get(row["validation_status"], 0) + 1
    summary = {
        "recipe_count": len(rows),
        "complete_recipe_count": status_counts.get("complete", 0),
        "partial_recipe_count": status_counts.get("partial", 0),
        "not_computable_recipe_count": status_counts.get("not_computable", 0),
        "complete_recipe_pct": round(100 * status_counts.get("complete", 0) / max(1, len(rows)), 2),
        "validated_complete_recipe_count": sum(1 for row in rows if row["validated_complete"]),
        "average_quantity_coverage_pct": round(
            sum(float(row["quantity_coverage_pct"]) for row in rows) / max(1, len(rows)), 2
        ),
        "average_nutrition_ref_coverage_pct": round(
            sum(float(row["nutrition_ref_coverage_pct"]) for row in rows) / max(1, len(rows)), 2
        ),
        "expansion_gate_passed": sum(1 for row in rows if row["validated_complete"]) >= 100,
        "expansion_gate": "At least 100 recipes must be fully quantity-computable with reviewed/source-verified references before catalog expansion.",
    }
    return rows, summary


def write_outputs(output_dir: Path, rows: list[dict[str, Any]], summary: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = output_dir / "recipe_nutrition_validation.csv"
    columns = list(rows[0].keys()) if rows else ["recipe_id"]
    with csv_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)
    (output_dir / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--require-expansion-ready", action="store_true")
    args = parser.parse_args()
    rows, summary = build_validation()
    write_outputs(args.output_dir, rows, summary)
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0 if not args.require_expansion_ready or summary["expansion_gate_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
