"""Persist complete shadow canonical links for active database recipes."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--require-complete",
        action="store_true",
        help="Fail unless every ingredient occurrence receives a canonical or provisional ID.",
    )
    args = parser.parse_args()

    database.init_db()
    recipes = database.get_all_recipes()
    if not recipes:
        raise RuntimeError("No active database recipes were found.")
    summary = database.sync_recipe_ingredient_links(recipes)
    print(json.dumps(summary, indent=2, sort_keys=True))
    if args.require_complete and float(summary.get("classification_coverage_pct") or 0) != 100.0:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
