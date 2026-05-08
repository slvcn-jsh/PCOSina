"""Recipe dataset quality diagnostics.

The goal is to flag cookability and data-quality risks without mutating the
source dataset. Sanitizing/fixing belongs in sanitize_recipes.py.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any


BACKEND_ROOT = Path(__file__).resolve().parent
DEFAULT_RECIPE_FILE = BACKEND_ROOT / "recipes.json"

NOTE_ARTIFACT_RE = re.compile(r"\b(?:see\s+)?note\s*\d+\b", re.IGNORECASE)
EXTREME_GARLIC_RE = re.compile(r"\b([2-9]\d|[1-9]\d{2,})\s*(?:cloves?|clove)\b.*\bgarlic\b", re.IGNORECASE)
LONG_PREP_RE = re.compile(r"\b(?:overnight|24\s*hours?|1\s*/\s*2\s*day|half\s+a\s+day)\b", re.IGNORECASE)
SUN_DRY_RE = re.compile(r"\b(?:sun|sun-dry|sun dry|under the sun)\b", re.IGNORECASE)
DEEP_FRY_RE = re.compile(r"\b(?:deep[- ]?fry|deep fried|fry until crispy|lechon kawali)\b", re.IGNORECASE)
MISSING_NOTE_RE = re.compile(r"\b(?:get more details here|available in the recipe section|note:)\b", re.IGNORECASE)


def load_recipes(path: Path = DEFAULT_RECIPE_FILE) -> list[dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError("recipes.json must contain a list of recipes")
    return [item for item in payload if isinstance(item, dict)]


def diagnose_recipes(recipes: list[dict[str, Any]]) -> list[dict[str, Any]]:
    issues: list[dict[str, Any]] = []
    for recipe in recipes:
        recipe_id = str(recipe.get("id") or "").strip()
        title = str(recipe.get("name") or recipe.get("title") or "Untitled").strip()
        ingredients = recipe.get("ingredients") or []
        instructions = recipe.get("instructions") or recipe.get("steps") or []
        tags = [str(tag).strip().lower() for tag in (recipe.get("tags") or []) if str(tag).strip()]
        minutes = _int_or_none(recipe.get("minutes"))

        ingredient_text = " | ".join(str((item or {}).get("name") or "") for item in ingredients if isinstance(item, dict))
        instruction_text = " ".join(str(step or "") for step in instructions)
        combined_text = f"{title} {ingredient_text} {instruction_text}"

        _append_if(issues, recipe_id, title, "metadata_artifact", "Ingredient/title contains unresolved note text", NOTE_ARTIFACT_RE.search(f"{title} {ingredient_text}"))
        _append_if(issues, recipe_id, title, "extreme_ingredient_quantity", "Possible scraped quantity typo, such as too many garlic cloves", EXTREME_GARLIC_RE.search(ingredient_text))
        _append_if(issues, recipe_id, title, "missing_external_note", "Instruction references an external note/details that are not in JSON", MISSING_NOTE_RE.search(instruction_text))
        _append_if(issues, recipe_id, title, "weekend_complexity", "Requires sun drying or unusually long passive prep", SUN_DRY_RE.search(instruction_text) or LONG_PREP_RE.search(instruction_text))
        _append_if(issues, recipe_id, title, "deep_fry_heavy", "Deep-fried or lechon-style recipe should not be a default weekday PCOS pick", DEEP_FRY_RE.search(combined_text))

        if minutes is None or minutes <= 0:
            _append_issue(issues, recipe_id, title, "missing_minutes", "Cooking time is missing or zero")
        elif minutes > 75:
            _append_issue(issues, recipe_id, title, "long_cook_time", f"Cooking time is {minutes} minutes")

        if not ingredients:
            _append_issue(issues, recipe_id, title, "missing_ingredients", "Recipe has no ingredients")
        if not instructions:
            _append_issue(issues, recipe_id, title, "missing_instructions", "Recipe has no instructions")
        elif len(instructions) > 12:
            _append_issue(issues, recipe_id, title, "complex_instruction_count", f"Recipe has {len(instructions)} instruction steps")

        expected_complexity = classify_complexity(recipe)
        if expected_complexity not in tags:
            _append_issue(issues, recipe_id, title, "missing_complexity_tag", f"Expected complexity tag: {expected_complexity}")
    return issues


def classify_complexity(recipe: dict[str, Any]) -> str:
    minutes = _int_or_none(recipe.get("minutes"))
    instructions = " ".join(str(step or "") for step in (recipe.get("instructions") or recipe.get("steps") or []))
    title = str(recipe.get("name") or recipe.get("title") or "")
    ingredients = " ".join(
        str((item or {}).get("name") or "")
        for item in recipe.get("ingredients") or []
        if isinstance(item, dict)
    )
    combined = f"{title} {ingredients} {instructions}"
    if SUN_DRY_RE.search(instructions) or LONG_PREP_RE.search(instructions) or (minutes is not None and minutes > 60):
        return "weekend"
    if DEEP_FRY_RE.search(combined) or (minutes is not None and minutes > 40):
        return "standard"
    if minutes is not None and minutes <= 30 and len(recipe.get("instructions") or recipe.get("steps") or []) <= 6:
        return "quick"
    return "standard"


def summarize_issues(issues: list[dict[str, Any]]) -> dict[str, Any]:
    counts = Counter(issue["issue"] for issue in issues)
    affected_recipes = {issue["id"] for issue in issues}
    return {
        "issueCount": len(issues),
        "affectedRecipeCount": len(affected_recipes),
        "issueTypes": dict(sorted(counts.items())),
    }


def write_issue_csv(path: Path, issues: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["id", "title", "issue", "detail"])
        writer.writeheader()
        writer.writerows(issues)


def write_issue_json(path: Path, issues: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"summary": summarize_issues(issues), "issues": issues}
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding="utf-8")


def _append_if(
    issues: list[dict[str, Any]],
    recipe_id: str,
    title: str,
    issue: str,
    detail: str,
    condition: Any,
) -> None:
    if condition:
        _append_issue(issues, recipe_id, title, issue, detail)


def _append_issue(issues: list[dict[str, Any]], recipe_id: str, title: str, issue: str, detail: str) -> None:
    if recipe_id:
        issues.append({"id": recipe_id, "title": title, "issue": issue, "detail": detail})


def _int_or_none(value: Any) -> int | None:
    try:
        parsed = int(float(str(value).strip()))
        return parsed if parsed > 0 else None
    except Exception:
        return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Diagnose PCOSina recipe dataset quality.")
    parser.add_argument("--recipes", type=Path, default=DEFAULT_RECIPE_FILE)
    parser.add_argument("--json", type=Path, help="Write full JSON diagnostic report.")
    parser.add_argument("--csv", type=Path, help="Write flat CSV diagnostic report.")
    parser.add_argument("--limit", type=int, default=20, help="Number of console examples to print.")
    args = parser.parse_args()

    recipes = load_recipes(args.recipes)
    issues = diagnose_recipes(recipes)
    summary = summarize_issues(issues)
    print(f"Analyzed {len(recipes)} recipe(s).")
    print(f"Found {summary['issueCount']} issue(s) across {summary['affectedRecipeCount']} recipe(s).")
    for issue, count in summary["issueTypes"].items():
        print(f"- {issue}: {count}")
    for issue in issues[: max(0, args.limit)]:
        print(f"[{issue['id']}] {issue['title']} - {issue['issue']}: {issue['detail']}")
    if args.json:
        write_issue_json(args.json, issues)
        print(f"Wrote JSON report to {args.json}")
    if args.csv:
        write_issue_csv(args.csv, issues)
        print(f"Wrote CSV report to {args.csv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
