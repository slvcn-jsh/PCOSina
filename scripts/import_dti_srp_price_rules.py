"""Import DTI/SRP-style ingredient prices into backend ingredient_price_rules.

Expected CSV columns:
keywords,price_php,category,unit,effective,source,confidence,notes

Example:
"garlic,bawang",120,Produce,kg,2026-05,DTI SRP,high,"Imported baseline"
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402
import price_catalog  # noqa: E402


REQUIRED_COLUMNS = {"keywords", "price_php", "category", "unit"}


def _csv_keywords(raw: str) -> list[str]:
    return [part.strip().lower() for part in str(raw or "").split(",") if part.strip()]


def _notes(row: dict[str, str]) -> str:
    source = str(row.get("source") or "DTI SRP").strip()
    confidence = str(row.get("confidence") or "high").strip().lower()
    effective = str(row.get("effective") or "").strip()
    freeform = str(row.get("notes") or "").strip()
    chunks = [f"source={source}", f"confidence={confidence}"]
    if effective:
        chunks.append(f"effective={effective}")
    if freeform:
        chunks.append(f"notes={freeform}")
    return "; ".join(chunks)


def import_rows(csv_path: Path, *, dry_run: bool = False) -> list[dict[str, Any]]:
    database.init_db()
    saved: list[dict[str, Any]] = []
    with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = {name.strip() for name in (reader.fieldnames or [])}
        missing = REQUIRED_COLUMNS - fieldnames
        if missing:
            raise ValueError(f"Missing required CSV columns: {', '.join(sorted(missing))}")
        for index, row in enumerate(reader, start=2):
            keywords = _csv_keywords(row.get("keywords", ""))
            if not keywords:
                raise ValueError(f"Row {index}: keywords cannot be blank")
            payload = {
                "id": str(row.get("id") or keywords[0]).strip().lower().replace(" ", "_"),
                "keywords": keywords,
                "pricePhp": max(1, int(float(str(row.get("price_php") or "0").strip()))),
                "category": str(row.get("category") or "Others").strip() or "Others",
                "unit": str(row.get("unit") or "").strip() or None,
                "active": str(row.get("active") or "true").strip().lower() not in {"0", "false", "no"},
                "notes": _notes(row),
            }
            saved.append(payload if dry_run else database.upsert_price_rule(payload))
    if not dry_run:
        price_catalog.invalidate_override_cache()
    return saved


def main() -> int:
    parser = argparse.ArgumentParser(description="Import DTI/SRP ingredient price rules into PCOSina.")
    parser.add_argument("csv_path", type=Path, help="Path to CSV file with SRP price rows.")
    parser.add_argument("--dry-run", action="store_true", help="Validate and print rows without writing.")
    args = parser.parse_args()

    rows = import_rows(args.csv_path, dry_run=args.dry_run)
    action = "Validated" if args.dry_run else "Imported"
    print(f"{action} {len(rows)} price rule(s).")
    for row in rows[:10]:
        print(f"- {row.get('id')}: {', '.join(row.get('keywords') or [])} = PHP {row.get('pricePhp')} / {row.get('unit') or 'item'}")
    if len(rows) > 10:
        print(f"... {len(rows) - 10} more")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
