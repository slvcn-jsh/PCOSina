import json
from pathlib import Path


NUTRIENTS = ("calories", "protein_g", "carbs_g", "fat_g", "fiber_g")


def ingredient_totals(ingredient):
    multiplier = ingredient["portion_g"] / 100
    return {
        nutrient: multiplier * float(ingredient["per_100g"][nutrient])
        for nutrient in NUTRIENTS
    }


def main():
    path = Path("backend/seed_data/pcosina_rnd_complete_meal_seed_v1.json")
    catalog = json.loads(path.read_text(encoding="utf-8"))

    daily = {nutrient: 0.0 for nutrient in NUTRIENTS}
    print("Meal nutrition totals from PhilFCT per-100g values")
    print("-" * 72)

    for meal in catalog["meals"]:
        meal_total = {nutrient: 0.0 for nutrient in NUTRIENTS}
        for ingredient in meal["ingredients"]:
            totals = ingredient_totals(ingredient)
            for nutrient in NUTRIENTS:
                meal_total[nutrient] += totals[nutrient]
                daily[nutrient] += totals[nutrient]

        print(
            f"{meal['mealType']:<10} {meal['name']:<42} "
            f"{meal_total['calories']:>7.2f} kcal | "
            f"P {meal_total['protein_g']:>5.2f}g | "
            f"C {meal_total['carbs_g']:>6.2f}g | "
            f"F {meal_total['fat_g']:>5.2f}g | "
            f"Fiber {meal_total['fiber_g']:>5.2f}g"
        )

    print("-" * 72)
    print(
        f"{'Daily':<10} {'Total':<42} "
        f"{daily['calories']:>7.2f} kcal | "
        f"P {daily['protein_g']:>5.2f}g | "
        f"C {daily['carbs_g']:>6.2f}g | "
        f"F {daily['fat_g']:>5.2f}g | "
        f"Fiber {daily['fiber_g']:>5.2f}g"
    )

    print("\nItems requiring review before final runtime import:")
    for meal in catalog["meals"]:
        for ingredient in meal["ingredients"]:
            status = ingredient.get("match_status", "")
            if status.startswith("review") or status.startswith("needs"):
                print(
                    f"- {meal['name']}: {ingredient['name']} -> "
                    f"{ingredient['philfct_name']} ({status})"
                )


if __name__ == "__main__":
    main()
