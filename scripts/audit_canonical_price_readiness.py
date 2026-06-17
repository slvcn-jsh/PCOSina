"""Report priority canonical NCR price coverage and version history."""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402
from canonical_reference_data import PRIORITY_PRICE_KEYWORDS  # noqa: E402


DEFAULT_OUTPUT_DIR = BACKEND_ROOT / "output" / "canonical_price_readiness"


def build_report() -> tuple[list[dict], dict]:
    database.init_db()
    conn = database._connect()  # noqa: SLF001
    try:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT ingredient_id, price_scope, location, market_type, unit,
                   price_php, source_date, confidence, source, notes
            FROM ingredient_price_refs
            WHERE active = 1
            ORDER BY ingredient_id, source_date DESC, priority DESC
            """
        )
        refs: dict[str, list[dict]] = {}
        for row in cur.fetchall():
            raw = dict(row) if hasattr(row, "keys") else {
                "ingredient_id": row[0], "price_scope": row[1], "location": row[2],
                "market_type": row[3], "unit": row[4], "price_php": row[5],
                "source_date": row[6], "confidence": row[7], "source": row[8],
                "notes": row[9],
            }
            refs.setdefault(str(raw["ingredient_id"]), []).append(raw)
    finally:
        conn.close()

    rows = []
    for ingredient_id in PRIORITY_PRICE_KEYWORDS:
        items = refs.get(ingredient_id, [])
        scopes = {str(item.get("price_scope") or "") for item in items}
        dated = [str(item.get("source_date") or "") for item in items if item.get("source_date")]
        rows.append(
            {
                "ingredient_id": ingredient_id,
                "wet_market_ready": "wet_market" in scopes,
                "grocery_ready": "grocery" in scopes,
                "baseline_ready": "baseline" in scopes,
                "version_count": len(items),
                "latest_source_date": max(dated) if dated else "",
                "scopes": " | ".join(sorted(scopes)),
            }
        )
    complete = sum(1 for row in rows if row["wet_market_ready"] and row["grocery_ready"])
    summary = {
        "priority_ingredient_count": len(rows),
        "wet_and_grocery_ready_count": complete,
        "coverage_pct": round(100 * complete / max(1, len(rows)), 2),
        "monthly_update_ready": all(row["latest_source_date"] for row in rows),
        "admin_override_supported": True,
        "user_override_supported": True,
        "price_gate_passed": complete == len(rows),
    }
    return rows, summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--require-ready", action="store_true")
    args = parser.parse_args()
    rows, summary = build_report()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    with (args.output_dir / "priority_price_coverage.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    (args.output_dir / "summary.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0 if not args.require_ready or summary["price_gate_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
