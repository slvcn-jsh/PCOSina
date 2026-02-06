import argparse
import json
import re
from collections import Counter
from pathlib import Path


BREAKFAST_NAME_KEYWORDS = [
    "omelet",
    "omelette",
    "pancake",
    "waffle",
    "hotcake",
    "silog",
    "tapsilog",
    "longsilog",
    "tocilog",
    "bangsilog",
    "tosilog",
    "champorado",
    "lugaw",
    "arroz",
    "breakfast",
    "brunch",
]

BREAKFAST_FULL_KEYWORDS = [
    "omelet",
    "omelette",
    "pancake",
    "waffle",
    "hotcake",
    "cereal",
    "oat",
    "oatmeal",
    "granola",
    "yogurt",
    "toast",
    "champorado",
    "lugaw",
    "arroz",
    "breakfast",
    "brunch",
    "silog",
    "tapsilog",
    "longsilog",
    "tocilog",
    "bangsilog",
    "tosilog",
]

LUNCH_KEYWORDS = [
    "salad",
    "sandwich",
    "wrap",
    "burger",
    "pasta",
    "noodle",
    "noodles",
    "pancit",
    "spaghetti",
    "mami",
    "lomi",
    "chopsuey",
    "chop suey",
    "siopao",
    "tortilla",
    "quesadilla",
    "sushi",
    "bento",
    "dumpling",
    "siomai",
]

DINNER_KEYWORDS = [
    "adobo",
    "sinigang",
    "kare",
    "kaldereta",
    "menudo",
    "afritada",
    "tinola",
    "nilaga",
    "bulalo",
    "mechado",
    "paksiw",
    "pinakbet",
    "soup",
    "stew",
    "curry",
    "guisado",
    "roast",
    "grill",
    "brais",
    "bake",
    "lechon",
    "sisig",
    "pochero",
    "ginataan",
    "laing",
    "inun-unan",
    "escabeche",
    "caldereta",
    "bicol",
]


def normalize(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (text or "").lower()).strip()


def recipe_text(recipe: dict) -> tuple[str, str, list[str]]:
    name = recipe.get("name") or recipe.get("title") or ""
    ingredients = recipe.get("ingredients") or []
    parts = [name]
    for ing in ingredients:
        if isinstance(ing, dict):
            parts.append(str(ing.get("name", "")))
        else:
            parts.append(str(ing))
    full_text = normalize(" ".join(parts))
    name_text = normalize(name)
    tokens = full_text.split()
    return name_text, full_text, tokens


def contains_any(text: str, tokens: set[str], keywords: list[str]) -> bool:
    for k in keywords:
        if " " in k:
            if k in text:
                return True
        elif k in tokens:
            return True
    return False


def infer_meal_type(recipe: dict) -> str:
    name_text, full_text, tokens = recipe_text(recipe)
    name_tokens = set(name_text.split())
    tokens_set = set(tokens)
    has_breakfast = contains_any(
        name_text, name_tokens, BREAKFAST_NAME_KEYWORDS
    ) or contains_any(full_text, tokens_set, BREAKFAST_FULL_KEYWORDS)
    has_dinner = contains_any(full_text, tokens_set, DINNER_KEYWORDS)
    has_egg = "egg" in tokens

    if has_breakfast or (has_egg and not has_dinner):
        return "Breakfast"
    if contains_any(full_text, tokens_set, LUNCH_KEYWORDS):
        return "Lunch"
    if has_dinner:
        return "Dinner"
    return "Lunch"


def main() -> int:
    parser = argparse.ArgumentParser(description="Heuristically tag recipes with meal types.")
    parser.add_argument("--write", action="store_true", help="Write changes to recipes.json")
    parser.add_argument(
        "--path",
        default="recipes.json",
        help="Path to recipes.json (default: recipes.json)",
    )
    parser.add_argument(
        "--sample",
        type=int,
        default=5,
        help="Number of samples per category to print",
    )
    args = parser.parse_args()

    path = Path(args.path)
    if not path.exists():
        print(f"recipes file not found: {path}")
        return 1

    recipes = json.loads(path.read_text(encoding="utf-8"))
    before = Counter((r.get("mealType") or "Unknown") for r in recipes)

    samples = {"Breakfast": [], "Lunch": [], "Dinner": []}
    changed = 0
    for r in recipes:
        new_type = infer_meal_type(r)
        old_type = r.get("mealType")
        if old_type != new_type:
            changed += 1
        r["mealType"] = new_type
        if len(samples[new_type]) < args.sample:
            samples[new_type].append(r.get("name") or r.get("title") or r.get("id"))

    after = Counter((r.get("mealType") or "Unknown") for r in recipes)

    print("MealType counts (before):", dict(before))
    print("MealType counts (after):", dict(after))
    print("Changed:", changed)
    for k, v in samples.items():
        print(f"Sample {k}: {v}")

    if args.write:
        path.write_text(json.dumps(recipes, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Wrote updates to {path}")
    else:
        print("Dry run only. Re-run with --write to persist.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
