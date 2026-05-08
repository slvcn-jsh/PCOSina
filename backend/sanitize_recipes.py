"""Conservative recipe dataset sanitizer.

Default mode is dry-run. Use --apply to write changes back to the recipe file.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

BACKEND_ROOT = Path(__file__).resolve().parent
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from diagnose_recipes import DEFAULT_RECIPE_FILE, classify_complexity, diagnose_recipes, summarize_issues  # noqa: E402


NOTE_ARTIFACT_RE = re.compile(r"\s*(?:\((?:see\s+)?note\s*\d+\)|,?\s*(?:see\s+)?note\s*\d+)\s*", re.IGNORECASE)
WHITESPACE_RE = re.compile(r"\s+")
COMPLEXITY_TAGS = {"quick", "standard", "weekend"}

KNOWN_INGREDIENT_FIXES = {
    ("ph_494", "45 cloves garlic; crushed"): "4-5 cloves garlic; crushed",
    ("ph_98", "1 1/2 lb. lechon kawaii sliced"): "1 1/2 lb. lechon kawali sliced",
}


def load_recipes(path: Path = DEFAULT_RECIPE_FILE) -> list[dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError("recipes.json must contain a list of recipes")
    return [item for item in payload if isinstance(item, dict)]


def sanitize_recipes(recipes: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, str]]]:
    cleaned = json.loads(json.dumps(recipes, ensure_ascii=True))
    changes: list[dict[str, str]] = []
    for recipe in cleaned:
        recipe_id = str(recipe.get("id") or "").strip()
        _clean_text_field(recipe, recipe_id, "name", changes)
        _clean_text_field(recipe, recipe_id, "title", changes)

        for ingredient in recipe.get("ingredients") or []:
            if not isinstance(ingredient, dict):
                continue
            original_name = str(ingredient.get("name") or "")
            fixed_name = KNOWN_INGREDIENT_FIXES.get((recipe_id, original_name), original_name)
            fixed_name = _clean_note_artifacts(fixed_name)
            if fixed_name != original_name:
                ingredient["name"] = fixed_name
                changes.append(
                    {
                        "id": recipe_id,
                        "field": "ingredient.name",
                        "before": original_name,
                        "after": fixed_name,
                    }
                )

        tags = [str(tag).strip().lower() for tag in (recipe.get("tags") or []) if str(tag).strip()]
        next_complexity = classify_complexity(recipe)
        next_tags = [tag for tag in tags if tag not in COMPLEXITY_TAGS]
        next_tags.append(next_complexity)
        if sorted(next_tags) != sorted(tags):
            recipe["tags"] = sorted(dict.fromkeys(next_tags))
            changes.append(
                {
                    "id": recipe_id,
                    "field": "tags",
                    "before": ", ".join(tags),
                    "after": ", ".join(recipe["tags"]),
                }
            )

        issue_types = {issue["issue"] for issue in diagnose_recipes([recipe])}
        needs_review = bool(issue_types - {"missing_complexity_tag", "missing_minutes"})
        tags_after_review = [str(tag).strip().lower() for tag in (recipe.get("tags") or []) if str(tag).strip()]
        if needs_review and "needs_review" not in tags_after_review:
            recipe["tags"] = sorted(dict.fromkeys(tags_after_review + ["needs_review"]))
            changes.append(
                {
                    "id": recipe_id,
                    "field": "tags",
                    "before": ", ".join(tags_after_review),
                    "after": ", ".join(recipe["tags"]),
                }
            )
        elif not needs_review and "needs_review" in tags_after_review:
            recipe["tags"] = sorted(tag for tag in tags_after_review if tag != "needs_review")
            changes.append(
                {
                    "id": recipe_id,
                    "field": "tags",
                    "before": ", ".join(tags_after_review),
                    "after": ", ".join(recipe["tags"]),
                }
            )
    return cleaned, changes


def write_recipes(path: Path, recipes: list[dict[str, Any]]) -> None:
    path.write_text(json.dumps(recipes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _clean_text_field(recipe: dict[str, Any], recipe_id: str, field: str, changes: list[dict[str, str]]) -> None:
    if field not in recipe:
        return
    original = str(recipe.get(field) or "")
    cleaned = _clean_note_artifacts(original)
    if cleaned != original:
        recipe[field] = cleaned
        changes.append({"id": recipe_id, "field": field, "before": original, "after": cleaned})


def _clean_note_artifacts(value: str) -> str:
    cleaned = NOTE_ARTIFACT_RE.sub(" ", value)
    cleaned = WHITESPACE_RE.sub(" ", cleaned)
    return cleaned.strip(" ,;")


def main() -> int:
    parser = argparse.ArgumentParser(description="Sanitize PCOSina recipes conservatively.")
    parser.add_argument("--recipes", type=Path, default=DEFAULT_RECIPE_FILE)
    parser.add_argument("--apply", action="store_true", help="Write sanitized recipes back to --recipes.")
    parser.add_argument("--report", type=Path, help="Write JSON change report.")
    args = parser.parse_args()

    recipes = load_recipes(args.recipes)
    before_summary = summarize_issues(diagnose_recipes(recipes))
    cleaned, changes = sanitize_recipes(recipes)
    after_summary = summarize_issues(diagnose_recipes(cleaned))
    print(f"Prepared {len(changes)} conservative change(s) for {len(cleaned)} recipe(s).")
    print(f"Issues before: {before_summary['issueCount']} across {before_summary['affectedRecipeCount']} recipe(s).")
    print(f"Issues after: {after_summary['issueCount']} across {after_summary['affectedRecipeCount']} recipe(s).")
    for change in changes[:20]:
        print(f"[{change['id']}] {change['field']}: {change['before']} -> {change['after']}")
    if len(changes) > 20:
        print(f"... and {len(changes) - 20} more change(s).")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(
                {
                    "applied": bool(args.apply),
                    "changeCount": len(changes),
                    "issuesBefore": before_summary,
                    "issuesAfter": after_summary,
                    "changes": changes,
                },
                ensure_ascii=True,
                indent=2,
            ),
            encoding="utf-8",
        )
        print(f"Wrote change report to {args.report}")
    if args.apply:
        write_recipes(args.recipes, cleaned)
        print(f"Applied sanitized dataset to {args.recipes}")
    else:
        print("Dry run only. Re-run with --apply to write changes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
