"""Stress audit supported restriction profiles crossed with common allergy pairs.

The regular 50-case audit is representative. This script is broader: it checks
the supported dietary-restriction profiles against no allergy, each known
allergy, and each pair of known allergies including the custom "chicken" input.
"""

from __future__ import annotations

import argparse
import csv
import json
import time
from itertools import combinations
from pathlib import Path
from typing import Any, Dict, List, Sequence, Tuple

from audit_runtime_catalog_diversity import (
    DEFAULT_CATALOG,
    DEFAULT_OUTPUT_DIR,
    apply_audit_requirements,
    base_profile,
    load_catalog,
    production_policy,
    run_scenario,
)


DEFAULT_JSON = DEFAULT_OUTPUT_DIR / "pcosina_combination_stress_audit_supported_restrictions_allergy_pairs.json"
DEFAULT_CSV = DEFAULT_OUTPUT_DIR / "pcosina_combination_stress_audit_supported_restrictions_allergy_pairs.csv"

SUPPORTED_RESTRICTION_PROFILES: Dict[str, List[str]] = {
    "none": [],
    "no_pork": ["No Pork"],
    "no_beef": ["No Beef"],
    "lactose_intolerant": ["Lactose Intolerant"],
    "no_pork_no_beef": ["No Pork", "No Beef"],
    "no_pork_lactose": ["No Pork", "Lactose Intolerant"],
    "no_beef_lactose": ["No Beef", "Lactose Intolerant"],
    "no_pork_no_beef_lactose": ["No Pork", "No Beef", "Lactose Intolerant"],
    "vegetarian": ["Vegetarian"],
    "vegetarian_lactose": ["Vegetarian", "Lactose Intolerant"],
    "pescatarian": ["Pescatarian"],
    "pescatarian_lactose": ["Pescatarian", "Lactose Intolerant"],
}

ALLERGY_INPUTS: List[str] = [
    "dairy",
    "egg",
    "peanuts",
    "tree nuts",
    "soy",
    "gluten/wheat",
    "fish",
    "shellfish",
    "chicken",
]


def allergy_sets() -> List[Tuple[str, List[str]]]:
    sets: List[Tuple[str, List[str]]] = [("none", [])]
    sets.extend((slug_allergies([item]), [item]) for item in ALLERGY_INPUTS)
    sets.extend((slug_allergies(list(items)), list(items)) for items in combinations(ALLERGY_INPUTS, 2))
    return sets


def slug_allergies(items: Sequence[str]) -> str:
    if not items:
        return "none"
    return "_".join(
        item.replace("/", "_").replace(" ", "_").replace("-", "_").lower()
        for item in items
    )


def is_expected_conflict(restrictions: Sequence[str], allergies: Sequence[str]) -> bool:
    restriction_set = {item.strip().lower() for item in restrictions}
    allergy_set = {item.strip().lower() for item in allergies}
    return "pescatarian" in restriction_set and {"fish", "shellfish"}.issubset(allergy_set)


def csv_row(row: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "scenario": row["scenario"],
        "accepted": row["accepted"],
        "expectedConflict": row["expectedConflict"],
        "auditPass": row.get("auditPass"),
        "auditViolations": json.dumps(row.get("auditViolations") or []),
        "status": row["status"],
        "message": row["message"],
        "dietaryRestrictions": json.dumps(row["dietaryRestrictions"]),
        "allergies": json.dumps(row["allergies"]),
        "safeRecipeCountPrePricing": row.get("safeRecipeCountPrePricing"),
        "candidateCountPost": row.get("candidateCountPost"),
        "selectedUniqueRecipeCount": row.get("selectedUniqueRecipeCount"),
        "selectedMaxRecipeRepeatCount": row.get("selectedMaxRecipeRepeatCount"),
        "dominantIngredientFamily": row.get("dominantIngredientFamily"),
        "dominantIngredientFamilyCount": row.get("dominantIngredientFamilyCount"),
        "estimatedWeeklyCostPhp": row.get("estimatedWeeklyCostPhp"),
        "maxPerWeekUsed": row.get("maxPerWeekUsed"),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--json-out", type=Path, default=DEFAULT_JSON)
    parser.add_argument("--csv-out", type=Path, default=DEFAULT_CSV)
    parser.add_argument("--max-repeat-limit", type=int, default=3)
    parser.add_argument("--max-semantic-family-limit", type=int, default=8)
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--meals-per-day", type=int, default=3)
    args = parser.parse_args()

    started = time.monotonic()
    recipes = load_catalog(args.catalog)
    policy = production_policy()
    allergy_cases = allergy_sets()
    rows: List[Dict[str, Any]] = []

    for restriction_slug, restrictions in SUPPORTED_RESTRICTION_PROFILES.items():
        for allergy_slug, allergies in allergy_cases:
            scenario = f"{restriction_slug}__allergy_{allergy_slug}"
            expected_conflict = is_expected_conflict(restrictions, allergies)
            summary = run_scenario(
                scenario,
                base_profile(dietaryRestrictions=list(restrictions), allergies=list(allergies)),
                recipes,
                policy,
                days=int(args.days),
                meals_per_day=int(args.meals_per_day),
            )
            apply_audit_requirements(
                summary,
                max_repeat_limit=int(args.max_repeat_limit),
                max_semantic_family_limit=int(args.max_semantic_family_limit),
            )
            accepted = bool(summary.get("auditPass")) or (
                expected_conflict and summary.get("status") == "failed"
            )
            summary["expectedConflict"] = expected_conflict
            summary["accepted"] = accepted
            rows.append(summary)

    successes = [row for row in rows if row.get("status") == "success"]
    failures = [row for row in rows if row.get("status") != "success"]
    expected_conflicts = [row for row in failures if row.get("expectedConflict")]
    unexpected_failures = [row for row in failures if not row.get("expectedConflict")]
    variety_violations = [
        row
        for row in successes
        if (int(row.get("selectedMaxRecipeRepeatCount") or 0) > int(args.max_repeat_limit))
        or (int(row.get("dominantIngredientFamilyCount") or 0) > int(args.max_semantic_family_limit))
    ]

    payload = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "catalogRecipeCount": len(recipes),
        "catalogMealTypeCounts": rows[0].get("catalogMealTypeCounts") if rows else {},
        "restrictionProfileCount": len(SUPPORTED_RESTRICTION_PROFILES),
        "allergySetCount": len(allergy_cases),
        "scenarioCount": len(rows),
        "acceptedCount": sum(1 for row in rows if row.get("accepted")),
        "unacceptedCount": sum(1 for row in rows if not row.get("accepted")),
        "successCount": len(successes),
        "expectedConflictCount": len(expected_conflicts),
        "unexpectedFailureCount": len(unexpected_failures),
        "varietyViolationCount": len(variety_violations),
        "elapsedSeconds": round(time.monotonic() - started, 3),
        "requirements": {
            "maxRecipeRepeatCount": int(args.max_repeat_limit),
            "maxSemanticFamilyCount": int(args.max_semantic_family_limit),
            "expectedHardConflictsAllowed": True,
        },
        "worst": {
            "maxRepeat": max((int(row.get("selectedMaxRecipeRepeatCount") or 0) for row in successes), default=0),
            "maxSemanticFamilyCount": max((int(row.get("dominantIngredientFamilyCount") or 0) for row in successes), default=0),
            "minUniqueSuccess": min((int(row.get("selectedUniqueRecipeCount") or 0) for row in successes), default=0),
            "maxUniqueSuccess": max((int(row.get("selectedUniqueRecipeCount") or 0) for row in successes), default=0),
        },
        "unexpectedFailures": unexpected_failures,
        "expectedConflicts": expected_conflicts,
        "varietyViolations": variety_violations,
        "rows": rows,
    }

    args.json_out.parent.mkdir(parents=True, exist_ok=True)
    args.csv_out.parent.mkdir(parents=True, exist_ok=True)
    args.json_out.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")

    with args.csv_out.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(csv_row(rows[0]).keys()) if rows else [])
        if rows:
            writer.writeheader()
            writer.writerows(csv_row(row) for row in rows)

    print(json.dumps({
        "catalogRecipeCount": payload["catalogRecipeCount"],
        "scenarioCount": payload["scenarioCount"],
        "acceptedCount": payload["acceptedCount"],
        "successCount": payload["successCount"],
        "expectedConflictCount": payload["expectedConflictCount"],
        "unexpectedFailureCount": payload["unexpectedFailureCount"],
        "varietyViolationCount": payload["varietyViolationCount"],
        "worst": payload["worst"],
        "jsonOut": str(args.json_out),
        "csvOut": str(args.csv_out),
        "elapsedSeconds": payload["elapsedSeconds"],
    }, indent=2, sort_keys=True))
    return 0 if payload["unacceptedCount"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
