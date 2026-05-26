import json
import re
from datasets import load_dataset

DATASET_NAME = "joackimagno/FILIPINO_RECIPES_1K"
OUTPUT_FILE = "recipes_hf_ph.json"  # ✅ NEW FILE (won’t overwrite your current recipes.json)

def split_pipe(text: str):
    if not text:
        return []
    return [x.strip() for x in str(text).split("|") if x.strip()]


def duration_to_minutes(value: str):
    text = str(value or "").strip().lower()
    if not text:
        return None
    hours = 0
    minutes = 0
    for amount, unit in re.findall(r"(\d+(?:\.\d+)?)\s*(hours?|hrs?|minutes?|mins?)", text):
        parsed = float(amount)
        if unit.startswith(("hour", "hr")):
            hours += int(round(parsed))
        else:
            minutes += int(round(parsed))
    total = hours * 60 + minutes
    return total if total > 0 else None


def main():
    ds = load_dataset(DATASET_NAME, split="train")

    recipes = []
    for i, row in enumerate(ds):
        name = (row.get("recipe_name") or f"Recipe {i+1}").strip()
        ingredients = split_pipe(row.get("ingredients"))
        instructions = split_pipe(row.get("instructions"))
        total_time = (row.get("total_time") or "").strip()
        total_minutes = duration_to_minutes(total_time)

        if not ingredients or not instructions:
            continue

        recipe = {
            "id": f"ph_{i+1}",
            "name": name,
            "mealType": "Lunch",  # placeholder for now
            "tags": [],
            "nutrition": {
                "calories": None,
                "protein_g": None,
                "carbs_g": None,
                "fat_g": None,
                "fiber_g": None
            },
            "ingredients": [{"name": ing, "quantity": ""} for ing in ingredients],
            "instructions": instructions,
            "sourceDataset": DATASET_NAME,
            "sourceRowIndex": i,
            "sourceServings": str(row.get("servings") or "").strip(),
            "sourcePrepTime": str(row.get("prep_time") or "").strip(),
            "sourceCookTime": str(row.get("cook_time") or "").strip(),
            "sourceTotalTime": total_time,
            "sourceIngredientNames": str(row.get("ingredient_names") or "").strip(),
        }
        if total_minutes:
            recipe["minutes"] = total_minutes
        recipes.append(recipe)

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(recipes, f, ensure_ascii=False, indent=2)

    print(f"✅ Exported {len(recipes)} recipes to {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
