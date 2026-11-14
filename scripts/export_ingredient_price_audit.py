"""Export recipe ingredient pricing audit CSVs.

This produces two files:
- ingredient_price_audit.csv: canonical ingredient rows for manual market review.
- raw_ingredient_occurrences.csv: every raw ingredient occurrence for traceability.

The manual columns are intentionally blank so the reviewed CSV can later be
converted into ingredient_price_rules.
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
import price_catalog  # noqa: E402


DEFAULT_OUTPUT_DIR = BACKEND_ROOT / "output" / "pricing_audit"
DEFAULT_RECIPES_JSON = BACKEND_ROOT / "recipes.json"

CANONICAL_COLUMNS = [
    "canonical_key",
    "display_name",
    "suggested_keywords",
    "category",
    "suggested_unit",
    "current_rule_price_php",
    "current_estimated_1_unit_price_php",
    "current_source",
    "current_source_label",
    "current_confidence",
    "matched_keywords",
    "market_multiplier",
    "category_multiplier",
    "occurrence_count",
    "recipe_count",
    "meal_types",
    "sample_recipe_ids",
    "sample_recipe_titles",
    "sample_raw_ingredients",
    "avg_current_occurrence_price_php",
    "max_current_occurrence_price_php",
    "sum_current_occurrence_price_php",
    "real_price_php",
    "real_unit",
    "price_min_php",
    "price_max_php",
    "market_source",
    "source_url",
    "survey_date",
    "reviewer_notes",
]

RAW_COLUMNS = [
    "recipe_id",
    "recipe_title",
    "meal_type",
    "raw_ingredient_name",
    "raw_quantity",
    "canonical_key",
    "display_name",
    "parsed_quantity_value",
    "parsed_quantity_unit",
    "current_estimated_price_php",
    "current_base_rule_price_php",
    "current_target_unit",
    "category",
    "source",
    "source_label",
    "confidence",
    "matched_keywords",
    "quantity_factor",
    "market_multiplier",
    "tingi_multiplier",
    "safety_buffer_multiplier",
]

MANUAL_PRICE_COLUMNS = [
    "real_price_php",
    "real_unit",
    "price_min_php",
    "price_max_php",
    "market_source",
    "source_url",
    "survey_date",
    "reviewer_notes",
]

UNIT_WORDS = {
    "bag",
    "bags",
    "bottle",
    "bottles",
    "box",
    "boxes",
    "bunch",
    "bunches",
    "can",
    "cans",
    "clove",
    "cloves",
    "cup",
    "cups",
    "g",
    "gram",
    "grams",
    "head",
    "heads",
    "kg",
    "kilo",
    "kilogram",
    "kilograms",
    "lb",
    "lbs",
    "liter",
    "liters",
    "litre",
    "litres",
    "ml",
    "ounce",
    "ounces",
    "oz",
    "pack",
    "packet",
    "packets",
    "packs",
    "pc",
    "pcs",
    "piece",
    "pieces",
    "piraso",
    "slice",
    "slices",
    "stalk",
    "stalks",
    "tablespoon",
    "tablespoons",
    "tbsp",
    "teaspoon",
    "teaspoons",
    "thumb",
    "thumbs",
    "tsp",
}

DESCRIPTOR_WORDS = {
    "a",
    "about",
    "amount",
    "and",
    "an",
    "as",
    "beaten",
    "boiled",
    "boneless",
    "chopped",
    "cleaned",
    "coarsely",
    "cold",
    "cooked",
    "crushed",
    "cubed",
    "cut",
    "dash",
    "deboned",
    "diced",
    "divided",
    "drained",
    "dried",
    "each",
    "finely",
    "fresh",
    "freshly",
    "fried",
    "for",
    "frozen",
    "grated",
    "ground",
    "halved",
    "handful",
    "hot",
    "inch",
    "inches",
    "into",
    "julienned",
    "large",
    "lean",
    "lightly",
    "medium",
    "minced",
    "optional",
    "or",
    "of",
    "peeled",
    "pinch",
    "pitted",
    "powdered",
    "raw",
    "ripe",
    "roughly",
    "seeded",
    "shredded",
    "sliced",
    "small",
    "thinly",
    "the",
    "to",
    "trimmed",
    "whole",
    "with",
}

LOCAL_SYNONYMS = {
    "bawang": "garlic",
    "bigas": "rice",
    "gatas": "milk",
    "hipon": "shrimp",
    "itlog": "egg",
    "kamatis": "tomato",
    "luya": "ginger",
    "manok": "chicken",
    "patis": "fish sauce",
    "repolyo": "cabbage",
    "sibuyas": "onion",
    "suka": "vinegar",
    "talong": "eggplant",
    "toyo": "soy sauce",
}

PHRASE_ALIASES = {
    "apple cider vinegar": "apple cider vinegar",
    "banana blossom": "banana blossom",
    "bangus milkfish": "bangus",
    "bell pepper": "bell pepper",
    "black pepper": "black pepper",
    "brown rice": "brown rice",
    "brown sugar": "brown sugar",
    "calamansi juice": "calamansi",
    "canola oil": "canola oil",
    "chicken breast": "chicken breast",
    "chicken thigh": "chicken thigh",
    "chicken thighs": "chicken thigh",
    "coconut cream": "coconut cream",
    "coconut milk": "coconut milk",
    "coconut oil": "coconut oil",
    "cooking oil": "cooking oil",
    "fish sauce": "fish sauce",
    "green beans": "green beans",
    "green bell pepper": "bell pepper",
    "magic sarap": "maggi magic sarap",
    "mung beans": "mung beans",
    "oyster sauce": "oyster sauce",
    "peanut butter": "peanut butter",
    "pork belly": "pork belly",
    "pork ribs": "pork ribs",
    "red bell pepper": "bell pepper",
    "sesame oil": "sesame oil",
    "shrimp paste": "shrimp paste",
    "sinigang sa sampaloc": "sinigang mix",
    "sinigang mix": "sinigang mix",
    "soy sauce": "soy sauce",
    "string beans": "string beans",
    "sweet potato": "sweet potato",
    "tomato paste": "tomato paste",
    "tomato sauce": "tomato sauce",
    "vegetable oil": "vegetable oil",
    "white rice": "white rice",
    "white sugar": "white sugar",
}

QTY_WITH_UNIT_RE = re.compile(
    r"\b\d+(?:\s+\d+/\d+|/\d+|\.\d+)?\s*"
    r"(?:kg|kilo|kilogram|kilograms|g|gram|grams|lb|lbs|pound|pounds|oz|ounce|ounces|ml|l|liter|liters|"
    r"litre|litres|cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|piece|pieces|pc|pcs|"
    r"clove|cloves|bunch|bunches|head|heads|thumb|thumbs|can|cans|pack|packs|packet|packets|slice|slices)"
    r"\.?\b",
    re.IGNORECASE,
)

NUMBER_RE = re.compile(r"\b\d+(?:\s+\d+/\d+|/\d+|\.\d+)?\b")


def _slug(value: str) -> str:
    key = re.sub(r"[^a-z0-9]+", "_", value.casefold()).strip("_")
    return key or "unknown"


def _title(value: str) -> str:
    small_words = {"and", "or", "sa", "ng"}
    parts = []
    for part in value.split():
        parts.append(part if part in small_words else part.capitalize())
    return " ".join(parts)


def _normalize_token(token: str) -> str:
    lowered = token.casefold().strip()
    return LOCAL_SYNONYMS.get(lowered, lowered)


def canonicalize_ingredient(name: str) -> tuple[str, str]:
    raw = str(name or "").casefold()
    raw = re.sub(r"\([^)]*\)", " ", raw)
    raw = raw.replace("-", " ")
    raw = QTY_WITH_UNIT_RE.sub(" ", raw)
    raw = NUMBER_RE.sub(" ", raw)
    tokens = [
        _normalize_token(token)
        for token in re.sub(r"[^a-z0-9]+", " ", raw).split()
        if token and token not in UNIT_WORDS and token not in DESCRIPTOR_WORDS
    ]
    if not tokens:
        return "unknown", "Unknown"

    joined = " ".join(tokens)
    for alias in sorted(PHRASE_ALIASES, key=len, reverse=True):
        if alias in joined:
            canonical = PHRASE_ALIASES[alias]
            return _slug(canonical), _title(canonical)

    canonical = " ".join(tokens[:4])
    return _slug(canonical), _title(canonical)


def _load_json_recipes(path: Path) -> list[dict[str, Any]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError(f"Expected a recipe list in {path}")
    normalized: list[dict[str, Any]] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        normalized.append(
            {
                "id": item.get("id"),
                "title": item.get("title") or item.get("name"),
                "mealType": item.get("mealType") or item.get("meal_type"),
                "ingredients": item.get("ingredients") or [],
            }
        )
    return normalized


def load_recipes(source: str, recipes_json: Path = DEFAULT_RECIPES_JSON) -> tuple[list[dict[str, Any]], str]:
    normalized_source = source.strip().lower()
    if normalized_source not in {"auto", "db", "json"}:
        raise ValueError("source must be one of: auto, db, json")

    if normalized_source in {"auto", "db"}:
        recipes = database.get_all_recipes()
        if recipes:
            return recipes, "db"
        if normalized_source == "db":
            raise RuntimeError("No active recipes were found in the local database.")

    return _load_json_recipes(recipes_json), "json"


def _ingredient_name_and_quantity(ingredient: Any) -> tuple[str, str]:
    if isinstance(ingredient, dict):
        return str(ingredient.get("name") or "").strip(), str(ingredient.get("quantity") or "").strip()
    return str(ingredient or "").strip(), ""


def _sample(values: Iterable[str], limit: int = 5) -> list[str]:
    result: list[str] = []
    for value in values:
        text = str(value or "").strip()
        if text and text not in result:
            result.append(text)
        if len(result) >= limit:
            break
    return result


def _join(values: Iterable[str]) -> str:
    return " | ".join(str(value) for value in values if str(value).strip())


def _suggested_keywords(canonical_key: str, display_name: str, raw_names: Iterable[str]) -> str:
    keywords = [display_name.casefold(), canonical_key.replace("_", " ")]
    for raw in raw_names:
        key, label = canonicalize_ingredient(raw)
        keywords.extend([key.replace("_", " "), label.casefold()])
    cleaned = []
    for keyword in keywords:
        token = str(keyword or "").strip()
        if token and token not in cleaned:
            cleaned.append(token)
    return ", ".join(cleaned[:8])


def build_audit_rows(
    recipes: list[dict[str, Any]],
    *,
    month_index: int | None = None,
    include_safety_buffer: bool = True,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
    pricing_context = price_catalog.create_pricing_context(month_index=month_index)
    raw_rows: list[dict[str, Any]] = []
    groups: dict[str, dict[str, Any]] = defaultdict(
        lambda: {
            "display_name": "",
            "raw_names": [],
            "recipe_ids": [],
            "recipe_titles": [],
            "meal_types": [],
            "occurrence_prices": [],
            "categories": Counter(),
            "sources": Counter(),
            "source_labels": Counter(),
            "confidences": Counter(),
            "target_units": Counter(),
            "base_prices": Counter(),
            "matched_keywords": Counter(),
            "market_multipliers": [],
        }
    )

    for recipe in recipes:
        recipe_id = str(recipe.get("id") or "").strip()
        title = str(recipe.get("title") or recipe.get("name") or "").strip()
        meal_type = str(recipe.get("mealType") or recipe.get("meal_type") or "").strip()
        for ingredient in recipe.get("ingredients") or []:
            raw_name, raw_quantity = _ingredient_name_and_quantity(ingredient)
            if not raw_name:
                continue
            canonical_key, display_name = canonicalize_ingredient(raw_name)
            estimate = price_catalog.estimate_price_explained(
                raw_name,
                raw_quantity,
                month_index=month_index,
                include_safety_buffer=include_safety_buffer,
                pricing_context=pricing_context,
            )
            row = {
                "recipe_id": recipe_id,
                "recipe_title": title,
                "meal_type": meal_type,
                "raw_ingredient_name": raw_name,
                "raw_quantity": raw_quantity,
                "canonical_key": canonical_key,
                "display_name": display_name,
                "parsed_quantity_value": estimate.quantity_value if estimate.quantity_value is not None else "",
                "parsed_quantity_unit": estimate.quantity_unit or "",
                "current_estimated_price_php": estimate.price_php,
                "current_base_rule_price_php": estimate.base_price_php,
                "current_target_unit": estimate.target_unit,
                "category": estimate.category,
                "source": estimate.source,
                "source_label": estimate.source_label,
                "confidence": estimate.confidence,
                "matched_keywords": ", ".join(estimate.matched_keywords),
                "quantity_factor": round(estimate.quantity_factor, 4),
                "market_multiplier": round(estimate.market_multiplier, 4),
                "tingi_multiplier": round(estimate.tingi_multiplier, 4),
                "safety_buffer_multiplier": round(estimate.safety_buffer_multiplier, 4),
            }
            raw_rows.append(row)

            group = groups[canonical_key]
            group["display_name"] = group["display_name"] or display_name
            group["raw_names"].append(raw_name)
            group["recipe_ids"].append(recipe_id)
            group["recipe_titles"].append(title)
            group["meal_types"].append(meal_type)
            group["occurrence_prices"].append(int(estimate.price_php))
            group["categories"][estimate.category] += 1
            group["sources"][estimate.source] += 1
            group["source_labels"][estimate.source_label] += 1
            group["confidences"][estimate.confidence] += 1
            group["target_units"][estimate.target_unit] += 1
            group["base_prices"][int(estimate.base_price_php)] += 1
            group["market_multipliers"].append(float(estimate.market_multiplier))
            for keyword in estimate.matched_keywords:
                if keyword:
                    group["matched_keywords"][keyword] += 1

    canonical_rows: list[dict[str, Any]] = []
    for canonical_key, group in groups.items():
        display_name = group["display_name"] or canonical_key.replace("_", " ").title()
        category = group["categories"].most_common(1)[0][0] if group["categories"] else "Others"
        unit = group["target_units"].most_common(1)[0][0] if group["target_units"] else ""
        one_unit_estimate = price_catalog.estimate_price_explained(
            display_name,
            f"1 {unit}" if unit else "",
            month_index=month_index,
            include_safety_buffer=include_safety_buffer,
            pricing_context=pricing_context,
        )
        occurrence_prices = group["occurrence_prices"]
        recipe_ids = _sample(group["recipe_ids"], limit=10)
        recipe_count = len({value for value in group["recipe_ids"] if value})
        raw_names = _sample(group["raw_names"], limit=8)
        market_multipliers = group["market_multipliers"]
        category_multiplier = price_catalog._CATEGORY_MULTIPLIER.get(category, 0.7)  # noqa: SLF001
        canonical_rows.append(
            {
                "canonical_key": canonical_key,
                "display_name": display_name,
                "suggested_keywords": _suggested_keywords(canonical_key, display_name, raw_names),
                "category": category,
                "suggested_unit": unit,
                "current_rule_price_php": group["base_prices"].most_common(1)[0][0] if group["base_prices"] else "",
                "current_estimated_1_unit_price_php": one_unit_estimate.price_php,
                "current_source": group["sources"].most_common(1)[0][0] if group["sources"] else "",
                "current_source_label": group["source_labels"].most_common(1)[0][0] if group["source_labels"] else "",
                "current_confidence": group["confidences"].most_common(1)[0][0] if group["confidences"] else "",
                "matched_keywords": ", ".join(keyword for keyword, _ in group["matched_keywords"].most_common(8)),
                "market_multiplier": round(sum(market_multipliers) / max(1, len(market_multipliers)), 4),
                "category_multiplier": category_multiplier,
                "occurrence_count": len(occurrence_prices),
                "recipe_count": recipe_count,
                "meal_types": _join(sorted({value for value in group["meal_types"] if value})),
                "sample_recipe_ids": _join(recipe_ids),
                "sample_recipe_titles": _join(_sample(group["recipe_titles"], limit=5)),
                "sample_raw_ingredients": _join(raw_names),
                "avg_current_occurrence_price_php": round(sum(occurrence_prices) / max(1, len(occurrence_prices)), 2),
                "max_current_occurrence_price_php": max(occurrence_prices) if occurrence_prices else 0,
                "sum_current_occurrence_price_php": sum(occurrence_prices),
                **{column: "" for column in MANUAL_PRICE_COLUMNS},
            }
        )

    canonical_rows.sort(
        key=lambda row: (
            -int(row["sum_current_occurrence_price_php"] or 0),
            str(row["display_name"]).casefold(),
        )
    )
    raw_rows.sort(
        key=lambda row: (
            str(row["canonical_key"]),
            str(row["recipe_id"]),
            str(row["raw_ingredient_name"]).casefold(),
        )
    )

    source_counts = Counter(str(row["source"]) for row in raw_rows)
    confidence_counts = Counter(str(row["confidence"]) for row in raw_rows)
    category_counts = Counter(str(row["category"]) for row in raw_rows)
    summary = {
        "recipe_count": len(recipes),
        "raw_ingredient_occurrence_count": len(raw_rows),
        "canonical_ingredient_count": len(canonical_rows),
        "category_average_fallback_occurrence_count": int(source_counts.get("category_average", 0)),
        "static_baseline_occurrence_count": int(source_counts.get("static", 0)),
        "source_counts": dict(sorted(source_counts.items())),
        "confidence_counts": dict(sorted(confidence_counts.items())),
        "category_counts": dict(sorted(category_counts.items())),
        "pricing_context": pricing_context.snapshot(),
        "include_safety_buffer": include_safety_buffer,
        "month_index": month_index,
    }
    return canonical_rows, raw_rows, summary


def write_csv(path: Path, columns: list[str], rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def write_outputs(
    output_dir: Path,
    canonical_rows: list[dict[str, Any]],
    raw_rows: list[dict[str, Any]],
    summary: dict[str, Any],
) -> dict[str, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    paths = {
        "canonical": output_dir / "ingredient_price_audit.csv",
        "raw": output_dir / "raw_ingredient_occurrences.csv",
        "summary": output_dir / "pricing_audit_summary.json",
    }
    write_csv(paths["canonical"], CANONICAL_COLUMNS, canonical_rows)
    write_csv(paths["raw"], RAW_COLUMNS, raw_rows)
    paths["summary"].write_text(json.dumps(summary, indent=2, sort_keys=True), encoding="utf-8")
    return paths


def main() -> int:
    parser = argparse.ArgumentParser(description="Export ingredient pricing audit CSVs.")
    parser.add_argument("--source", choices=["auto", "db", "json"], default="auto")
    parser.add_argument("--recipes-json", type=Path, default=DEFAULT_RECIPES_JSON)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--month-index", type=int, default=None, help="Optional month index, 1-12.")
    parser.add_argument(
        "--no-safety-buffer",
        action="store_true",
        help="Match display-only pricing instead of solver-style safety-buffered pricing.",
    )
    args = parser.parse_args()

    recipes, loaded_from = load_recipes(args.source, args.recipes_json)
    canonical_rows, raw_rows, summary = build_audit_rows(
        recipes,
        month_index=args.month_index,
        include_safety_buffer=not args.no_safety_buffer,
    )
    summary["source"] = loaded_from
    paths = write_outputs(args.output_dir, canonical_rows, raw_rows, summary)

    print(f"Loaded {len(recipes)} recipe(s) from {loaded_from}.")
    print(f"Wrote {len(canonical_rows)} canonical ingredient row(s): {paths['canonical']}")
    print(f"Wrote {len(raw_rows)} raw ingredient occurrence row(s): {paths['raw']}")
    print(f"Wrote summary: {paths['summary']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
