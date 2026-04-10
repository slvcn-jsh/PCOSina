#!/usr/bin/env python
from __future__ import annotations

import argparse
import re
from pathlib import Path


REQUIRED_EVENTS = {
    "plan_generated",
    "plan_viewed",
    "meal_accepted",
    "meal_replaced",
    "meal_skipped",
    "recipe_opened",
    "grocery_completed",
    "pantry_item_added",
    "pantry_item_removed",
    "pantry_item_expired",
    "cook_completed",
    "no_safe_plan_encountered",
    "manual_override_attempted",
    "why_replaced_submitted",
    "why_skipped_submitted",
}


def main() -> int:
    parser = argparse.ArgumentParser(description="Check Android ML behavior event coverage.")
    parser.add_argument(
        "--root",
        default="app/src/main/java/com/pcosina/app/ui",
        help="Root directory containing Kotlin UI files.",
    )
    args = parser.parse_args()

    root = Path(args.root)
    if not root.exists():
        print(f"missing root directory: {root}")
        return 2

    regex = re.compile(r'eventName\s*=\s*"([^"]+)"')
    found = set()
    for path in root.rglob("*.kt"):
        try:
            text = path.read_text(encoding="utf-8")
        except Exception:
            continue
        found.update(regex.findall(text))

    missing = sorted(REQUIRED_EVENTS - found)
    if missing:
        print("MOBILE ML EVENT COVERAGE FAILED")
        for item in missing:
            print(f"- missing event: {item}")
        return 1

    print("MOBILE ML EVENT COVERAGE PASSED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
