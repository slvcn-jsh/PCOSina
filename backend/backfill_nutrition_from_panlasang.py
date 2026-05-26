"""Backfill source-published recipe nutrition from Panlasang Pinoy pages.

This is a source extractor, not a nutrition generator. It only writes rows when
the source page exposes structured NutritionInformation with calories,
protein, carbohydrates, fat, and fiber.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


BACKEND_ROOT = Path(__file__).resolve().parent
DEFAULT_QUEUE = BACKEND_ROOT.parent / "docs" / "production_readiness" / "nutrition_review_queue.csv"
DEFAULT_OUTPUT = BACKEND_ROOT / "seed_data" / "panlasang_pinoy_nutrition_corrections.csv"
DEFAULT_AUDIT = BACKEND_ROOT.parent / "docs" / "production_readiness" / "panlasang_pinoy_nutrition_backfill_audit.json"
USER_AGENT = "PCOSina nutrition provenance audit (local development)"
OUTPUT_FIELDS = [
    "recipe_id",
    "calories",
    "protein_grams",
    "carbs_grams",
    "fats_grams",
    "fiber_grams",
    "sodium_mg",
    "sugar_grams",
    "source",
    "confidence",
    "review_status",
    "notes",
]
WHOLE_RECIPE_CALORIE_THRESHOLD = 1400
WHOLE_RECIPE_PROTEIN_THRESHOLD = 120
WHOLE_RECIPE_CARBS_THRESHOLD = 220
WHOLE_RECIPE_FAT_THRESHOLD = 100


def slug_candidates(title: str) -> list[str]:
    base = str(title or "").strip().lower()
    variants = [base]
    variants.append(re.sub(r"\([^)]*\)", "", base))
    variants.append(re.sub(r"\b(recipe|how to cook|how to fry)\b", "", variants[-1]))
    variants.append(re.sub(r"\bfilipino\b", "", variants[-1]))
    seen: set[str] = set()
    slugs: list[str] = []
    for item in variants:
        slug = re.sub(r"[^a-z0-9]+", "-", item).strip("-")
        if slug and slug not in seen:
            seen.add(slug)
            slugs.append(slug)
    return slugs


def page_url_for_slug(slug: str) -> str:
    return f"https://panlasangpinoy.com/{slug}/"


def fetch_page(url: str) -> str | None:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            if response.status != 200:
                return None
            return response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        if exc.code in {403, 404, 410}:
            return None
        raise
    except Exception:
        return None


def extract_nutrition(page: str) -> dict[str, Any] | None:
    for payload in re.findall(
        r'<script[^>]+type=["\']application/ld\+json["\'][^>]*>(.*?)</script>',
        page,
        flags=re.IGNORECASE | re.DOTALL,
    ):
        try:
            data = json.loads(payload)
        except Exception:
            continue
        found = find_nutrition_json(data)
        if found:
            return found
    return extract_nutrition_text(page)


def find_nutrition_json(data: Any) -> dict[str, Any] | None:
    if isinstance(data, list):
        for item in data:
            found = find_nutrition_json(item)
            if found:
                return found
    if not isinstance(data, dict):
        return None
    if "nutrition" in data and isinstance(data["nutrition"], dict):
        parsed = parse_nutrition_object(data["nutrition"])
        if parsed:
            parsed["source_recipe_yield"] = parse_serving_count(data.get("recipeYield"))
            return parsed
    graph = data.get("@graph")
    if isinstance(graph, list):
        for item in graph:
            found = find_nutrition_json(item)
            if found:
                return found
    return None


def parse_nutrition_object(nutrition: dict[str, Any]) -> dict[str, Any] | None:
    calories = parse_number(nutrition.get("calories"))
    carbs = parse_number(nutrition.get("carbohydrateContent"))
    protein = parse_number(nutrition.get("proteinContent"))
    fat = parse_number(nutrition.get("fatContent"))
    fiber = parse_number(nutrition.get("fiberContent"))
    if not all(value is not None and value > 0 for value in (calories, carbs, protein, fat, fiber)):
        return None
    return {
        "calories": int(round(float(calories))),
        "carbs_grams": round(float(carbs), 1),
        "protein_grams": round(float(protein), 1),
        "fats_grams": round(float(fat), 1),
        "fiber_grams": round(float(fiber), 1),
        "sodium_mg": _optional_int(nutrition.get("sodiumContent")),
        "sugar_grams": _optional_float(nutrition.get("sugarContent")),
    }


def extract_nutrition_text(page: str) -> dict[str, Any] | None:
    text = re.sub(r"<[^>]+>", " ", page)
    pattern = re.compile(
        r"Calories:\s*([0-9,.]+)\s*kcal.*?"
        r"Carbohydrates:\s*([0-9,.]+)\s*g.*?"
        r"Protein:\s*([0-9,.]+)\s*g.*?"
        r"Fat:\s*([0-9,.]+)\s*g.*?"
        r"Fiber:\s*([0-9,.]+)\s*g",
        flags=re.IGNORECASE | re.DOTALL,
    )
    match = pattern.search(text)
    if not match:
        return None
    calories, carbs, protein, fat, fiber = [parse_number(value) for value in match.groups()]
    if not all(value is not None and value > 0 for value in (calories, carbs, protein, fat, fiber)):
        return None
    return {
        "calories": int(round(float(calories))),
        "carbs_grams": round(float(carbs), 1),
        "protein_grams": round(float(protein), 1),
        "fats_grams": round(float(fat), 1),
        "fiber_grams": round(float(fiber), 1),
        "sodium_mg": "",
        "sugar_grams": "",
        "source_recipe_yield": None,
    }


def parse_number(value: Any) -> float | None:
    if value in (None, ""):
        return None
    match = re.search(r"[-+]?\d+(?:,\d{3})*(?:\.\d+)?", str(value))
    if not match:
        return None
    try:
        return float(match.group(0).replace(",", ""))
    except Exception:
        return None


def parse_serving_count(value: Any) -> float | None:
    if isinstance(value, list):
        for item in value:
            parsed = parse_serving_count(item)
            if parsed is not None:
                return parsed
        return None
    parsed = parse_number(value)
    if parsed is None or parsed <= 0:
        return None
    return parsed


def normalize_per_serving(nutrition: dict[str, Any], row: dict[str, str]) -> tuple[dict[str, Any], str, float | None]:
    servings = parse_serving_count(nutrition.get("source_recipe_yield")) or parse_serving_count(row.get("source_servings"))
    normalized = {
        "calories": nutrition["calories"],
        "protein_grams": nutrition["protein_grams"],
        "carbs_grams": nutrition["carbs_grams"],
        "fats_grams": nutrition["fats_grams"],
        "fiber_grams": nutrition["fiber_grams"],
        "sodium_mg": nutrition.get("sodium_mg") or "",
        "sugar_grams": nutrition.get("sugar_grams") or "",
    }
    if servings is None or servings <= 1 or not looks_like_whole_recipe_total(nutrition):
        return normalized, "source_published_per_serving", servings
    divisor = float(servings)
    normalized["calories"] = int(round(float(nutrition["calories"]) / divisor))
    for key in ("protein_grams", "carbs_grams", "fats_grams", "fiber_grams", "sugar_grams"):
        value = nutrition.get(key)
        if value not in (None, ""):
            normalized[key] = round(float(value) / divisor, 1)
    sodium = nutrition.get("sodium_mg")
    if sodium not in (None, ""):
        normalized["sodium_mg"] = int(round(float(sodium) / divisor))
    return normalized, "normalized_by_source_recipe_yield", servings


def looks_like_whole_recipe_total(nutrition: dict[str, Any]) -> bool:
    return (
        float(nutrition.get("calories") or 0) > WHOLE_RECIPE_CALORIE_THRESHOLD
        or float(nutrition.get("protein_grams") or 0) > WHOLE_RECIPE_PROTEIN_THRESHOLD
        or float(nutrition.get("carbs_grams") or 0) > WHOLE_RECIPE_CARBS_THRESHOLD
        or float(nutrition.get("fats_grams") or 0) > WHOLE_RECIPE_FAT_THRESHOLD
    )


def source_corrections(
    queue_rows: list[dict[str, str]],
    *,
    priority: str | None,
    offset: int,
    limit: int | None,
    delay_seconds: float,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    corrections: list[dict[str, Any]] = []
    audit_rows: list[dict[str, Any]] = []
    considered = 0
    skipped = 0
    for row in queue_rows:
        if priority and str(row.get("priority") or "").upper() != priority.upper():
            continue
        if skipped < offset:
            skipped += 1
            continue
        if limit is not None and considered >= limit:
            break
        considered += 1
        title = row.get("title") or ""
        matched_url = ""
        nutrition = None
        for slug in slug_candidates(title):
            url = page_url_for_slug(slug)
            page = fetch_page(url)
            if not page:
                continue
            nutrition = extract_nutrition(page)
            if nutrition:
                matched_url = url
                break
        if nutrition:
            per_serving, source_basis, source_yield = normalize_per_serving(nutrition, row)
            corrections.append(
                {
                    "recipe_id": row.get("recipe_id"),
                    "calories": per_serving["calories"],
                    "protein_grams": per_serving["protein_grams"],
                    "carbs_grams": per_serving["carbs_grams"],
                    "fats_grams": per_serving["fats_grams"],
                    "fiber_grams": per_serving["fiber_grams"],
                    "sodium_mg": per_serving.get("sodium_mg") or "",
                    "sugar_grams": per_serving.get("sugar_grams") or "",
                    "source": (
                        "panlasang_pinoy_recipe_card_per_serving"
                        if source_basis == "source_published_per_serving"
                        else "panlasang_pinoy_recipe_card_yield_normalized"
                    ),
                    "confidence": "high",
                    "review_status": "source_verified",
                    "notes": (
                        f"title={title}; source_url={matched_url}; source_servings={row.get('source_servings') or ''}; "
                        f"source_recipe_yield={source_yield or ''}; source_basis={source_basis}"
                    ),
                }
            )
            audit_rows.append(
                {
                    "recipe_id": row.get("recipe_id"),
                    "title": title,
                    "status": "matched",
                    "url": matched_url,
                    "sourceBasis": source_basis,
                    "sourceRecipeYield": source_yield,
                }
            )
        else:
            audit_rows.append({"recipe_id": row.get("recipe_id"), "title": title, "status": "unmatched", "url": ""})
        if delay_seconds > 0:
            time.sleep(delay_seconds)
    summary = {
        "offsetRows": offset,
        "consideredRows": considered,
        "matchedRows": len(corrections),
        "unmatchedRows": considered - len(corrections),
        "auditRows": audit_rows,
    }
    return corrections, summary


def read_queue(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return [dict(row) for row in csv.DictReader(handle)]


def read_existing_corrections(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return [dict(row) for row in csv.DictReader(handle)]


def merge_corrections(existing: list[dict[str, Any]], new_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_recipe_id: dict[str, dict[str, Any]] = {}
    order: list[str] = []
    for row in [*existing, *new_rows]:
        recipe_id = str(row.get("recipe_id") or "").strip()
        if not recipe_id:
            continue
        if recipe_id not in by_recipe_id:
            order.append(recipe_id)
        by_recipe_id[recipe_id] = row
    return [by_recipe_id[recipe_id] for recipe_id in order]


def write_corrections(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def write_audit(path: Path, summary: dict[str, Any], output: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "source": "Panlasang Pinoy structured recipe NutritionInformation",
        "outputCsv": str(output),
        "summary": summary,
    }
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding="utf-8")


def _optional_int(value: Any) -> int | str:
    parsed = parse_number(value)
    return int(round(parsed)) if parsed is not None else ""


def _optional_float(value: Any) -> float | str:
    parsed = parse_number(value)
    return round(parsed, 1) if parsed is not None else ""


def main() -> int:
    parser = argparse.ArgumentParser(description="Backfill Panlasang Pinoy source-published nutrition corrections.")
    parser.add_argument("--input", type=Path, default=DEFAULT_QUEUE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--audit", type=Path, default=DEFAULT_AUDIT)
    parser.add_argument("--priority", default="P0")
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--limit", type=int, default=30)
    parser.add_argument("--delay", type=float, default=0.2)
    parser.add_argument("--merge-existing", action="store_true")
    args = parser.parse_args()
    priority = args.priority.strip() if args.priority and args.priority.strip().lower() not in {"all", "*"} else None
    corrections, summary = source_corrections(
        read_queue(args.input),
        priority=priority,
        offset=max(0, args.offset),
        limit=args.limit,
        delay_seconds=max(0.0, args.delay),
    )
    if args.merge_existing:
        corrections = merge_corrections(read_existing_corrections(args.output), corrections)
        summary["outputRows"] = len(corrections)
    write_corrections(args.output, corrections)
    write_audit(args.audit, summary, args.output)
    print(f"Wrote {len(corrections)} source nutrition correction row(s) to {args.output}")
    print(json.dumps({key: summary[key] for key in ("consideredRows", "matchedRows", "unmatchedRows")}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
