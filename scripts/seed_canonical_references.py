"""Seed priority canonical nutrition and versioned NCR price references."""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import time
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402
from canonical_reference_data import (  # noqa: E402
    PRIORITY_NUTRITION_PROFILE_KEYS,
    PRIORITY_PRICE_KEYWORDS,
)
from draft_nutrition_from_fdc import LOCAL_REFERENCE_NUTRIENTS, select_local_profile_key  # noqa: E402


DEFAULT_PRICE_CSV = BACKEND_ROOT / "seed_data" / "reviewed_market_price_rules.csv"


def _slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", str(value or "").casefold()).strip("_")


def _bool(value: Any) -> bool:
    return str(value or "").strip().lower() in {"1", "true", "yes", "on"}


def _parse_source_date(value: str) -> str:
    match = re.search(r"\d{4}-\d{2}-\d{2}", str(value or ""))
    return match.group(0) if match else ""


def _select_price_row(rows: list[dict[str, str]], keywords: tuple[str, ...]) -> dict[str, str] | None:
    active = [row for row in rows if _bool(row.get("active"))]
    for keyword in keywords:
        exact = [
            row for row in active
            if str(row.get("keywords") or "").strip().casefold() == keyword.casefold()
        ]
        if exact:
            return max(exact, key=lambda row: {"high": 3, "medium": 2, "low": 1}.get(str(row.get("confidence") or "").lower(), 0))
    for keyword in keywords:
        matches = [
            row for row in active
            if keyword.casefold() in {
                part.strip().casefold()
                for part in str(row.get("keywords") or "").split(",")
                if part.strip()
            }
        ]
        if matches:
            return max(matches, key=lambda row: {"high": 3, "medium": 2, "low": 1}.get(str(row.get("confidence") or "").lower(), 0))
    return None


def seed_references(price_csv: Path = DEFAULT_PRICE_CSV) -> dict[str, Any]:
    database.init_db()
    price_rows = list(csv.DictReader(price_csv.open(encoding="utf-8-sig", newline="")))
    conn = database._connect()  # noqa: SLF001
    now = int(time.time() * 1000)
    placeholder = "%s" if database._use_postgres() else "?"  # noqa: SLF001
    summary = {
        "nutrition_refs_seeded": 0,
        "wet_market_refs_seeded": 0,
        "grocery_refs_seeded": 0,
        "missing_nutrition_profiles": [],
        "missing_price_rows": [],
    }

    def replace(table: str, columns: list[str], values: tuple, conflict_column: str) -> None:
        names = ", ".join(columns)
        params = ", ".join([placeholder] * len(columns))
        if database._use_postgres():  # noqa: SLF001
            updates = ", ".join(f"{column}=EXCLUDED.{column}" for column in columns if column != conflict_column)
            sql = f"INSERT INTO {table} ({names}) VALUES ({params}) ON CONFLICT ({conflict_column}) DO UPDATE SET {updates}"
        else:
            sql = f"INSERT OR REPLACE INTO {table} ({names}) VALUES ({params})"
        conn.cursor().execute(sql, values)

    try:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT ingredient_id, canonical_name
            FROM canonical_ingredients
            WHERE active = 1 AND quality_status NOT LIKE 'provisional_%'
            """
        )
        auto_profiles = {
            str(row[0] if not hasattr(row, "keys") else row["ingredient_id"]):
            select_local_profile_key(str(row[1] if not hasattr(row, "keys") else row["canonical_name"]))
            for row in cur.fetchall()
        }
        nutrition_profiles = {
            ingredient_id: profile_key
            for ingredient_id, profile_key in auto_profiles.items()
            if profile_key
        }
        nutrition_profiles.update(PRIORITY_NUTRITION_PROFILE_KEYS)
        for ingredient_id, profile_key in nutrition_profiles.items():
            nutrients = LOCAL_REFERENCE_NUTRIENTS.get(profile_key)
            if not nutrients:
                summary["missing_nutrition_profiles"].append(ingredient_id)
                continue
            replace(
                "ingredient_nutrition_refs",
                [
                    "nutrition_ref_id", "ingredient_id", "source_name", "source_food_id",
                    "food_form", "calories_per_100g", "protein_per_100g",
                    "carbs_per_100g", "fat_per_100g", "fiber_per_100g",
                    "sodium_mg_per_100g", "sugar_per_100g", "confidence",
                    "review_status", "source_url", "active", "created_at", "updated_at",
                ],
                (
                    f"nutrition_{ingredient_id}_local_reference_v1",
                    ingredient_id,
                    "PCOSina local reference profile derived from USDA-style per-100g values",
                    f"local:{profile_key}",
                    "reference_unspecified",
                    nutrients["calories"],
                    nutrients["protein_grams"],
                    nutrients["carbs_grams"],
                    nutrients["fats_grams"],
                    nutrients["fiber_grams"],
                    nutrients["sodium_mg"],
                    nutrients["sugar_grams"],
                    "medium",
                    "pending_review",
                    "",
                    1,
                    now,
                    now,
                ),
                "nutrition_ref_id",
            )
            summary["nutrition_refs_seeded"] += 1

        for ingredient_id, keywords in PRIORITY_PRICE_KEYWORDS.items():
            row = _select_price_row(price_rows, keywords)
            if not row:
                summary["missing_price_rows"].append(ingredient_id)
                continue
            price = float(row["price_php"])
            source_date = _parse_source_date(row.get("effective") or "")
            base_values = {
                "ingredient_id": ingredient_id,
                "location": "NCR",
                "unit": str(row.get("unit") or "").strip().lower(),
                "source": str(row.get("market_source") or row.get("source") or "reviewed_market"),
                "source_date": source_date,
                "confidence": str(row.get("confidence") or "medium").lower(),
                "valid_until": "",
                "active": 1,
                "created_at": now,
                "updated_at": now,
            }
            for scope, market_type, multiplier, priority in (
                ("wet_market", "wet_market", 1.0, 300),
                ("grocery", "grocery_estimate", 1.15, 250),
            ):
                ref_id = f"price_{ingredient_id}_ncr_{scope}_{source_date or 'undated'}"
                notes = (
                    "Imported from reviewed NCR audit."
                    if scope == "wet_market"
                    else "Derived grocery estimate = reviewed NCR wet-market reference x 1.15; not an independent store survey."
                )
                replace(
                    "ingredient_price_refs",
                    [
                        "price_ref_id", "ingredient_id", "location", "market_type", "unit",
                        "price_php", "price_min_php", "price_max_php", "source",
                        "source_date", "confidence", "valid_until", "active",
                        "created_at", "updated_at", "price_scope", "owner_uid",
                        "priority", "notes",
                    ],
                    (
                        ref_id,
                        base_values["ingredient_id"],
                        base_values["location"],
                        market_type,
                        base_values["unit"],
                        round(price * multiplier, 2),
                        float(row["price_min_php"]) * multiplier if row.get("price_min_php") else None,
                        float(row["price_max_php"]) * multiplier if row.get("price_max_php") else None,
                        base_values["source"],
                        base_values["source_date"],
                        base_values["confidence"] if scope == "wet_market" else "low",
                        base_values["valid_until"],
                        base_values["active"],
                        base_values["created_at"],
                        base_values["updated_at"],
                        scope,
                        None,
                        priority,
                        notes,
                    ),
                    "price_ref_id",
                )
                summary[f"{scope}_refs_seeded"] += 1
        conn.commit()
        return summary
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--price-csv", type=Path, default=DEFAULT_PRICE_CSV)
    args = parser.parse_args()
    print(json.dumps(seed_references(args.price_csv), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
