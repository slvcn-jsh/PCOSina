import json
from datasets import load_dataset

DATASET_NAME = "joackimagno/FILIPINO_RECIPES_1K"
OUTPUT_FILE = "recipes_hf_ph.json"  # ✅ NEW FILE (won’t overwrite your current recipes.json)

def split_pipe(text: str):
    if not text:
        return []
    return [x.strip() for x in str(text).split("|") if x.strip()]

def main():
    ds = load_dataset(DATASET_NAME, split="train")

    recipes = []
    for i, row in enumerate(ds):
        name = (row.get("recipe_name") or f"Recipe {i+1}").strip()
        ingredients = split_pipe(row.get("ingredients"))
        instructions = split_pipe(row.get("instructions"))

        if not ingredients or not instructions:
            continue

        recipes.append({
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
            "instructions": instructions
        })

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(recipes, f, ensure_ascii=False, indent=2)

    print(f"✅ Exported {len(recipes)} recipes to {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
