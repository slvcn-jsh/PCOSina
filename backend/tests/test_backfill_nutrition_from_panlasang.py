import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import backfill_nutrition_from_panlasang as pp


def test_slug_candidates_remove_recipe_noise():
    slugs = pp.slug_candidates("Filipino Chicken Adobo Recipe")

    assert "filipino-chicken-adobo-recipe" in slugs
    assert "chicken-adobo" in slugs


def test_extract_nutrition_from_json_ld():
    page = """
    <html><script type="application/ld+json">
    {
      "@type": "Recipe",
      "nutrition": {
        "@type": "NutritionInformation",
        "calories": "435 kcal",
        "carbohydrateContent": "26 g",
        "proteinContent": "53 g",
        "fatContent": "16 g",
        "fiberContent": "9 g",
        "sodiumContent": "1657 mg",
        "sugarContent": "12 g"
      }
    }
    </script></html>
    """

    nutrition = pp.extract_nutrition(page)

    assert nutrition == {
        "calories": 435,
        "carbs_grams": 26.0,
        "protein_grams": 53.0,
        "fats_grams": 16.0,
        "fiber_grams": 9.0,
        "sodium_mg": 1657,
        "sugar_grams": 12.0,
        "source_recipe_yield": None,
    }


def test_extract_nutrition_from_text_fallback():
    page = "Calories: 512 kcal Carbohydrates: 41 g Protein: 13 g Fat: 38 g Fiber: 11 g"

    nutrition = pp.extract_nutrition(page)

    assert nutrition["calories"] == 512
    assert nutrition["protein_grams"] == 13.0
    assert nutrition["fiber_grams"] == 11.0


def test_extract_nutrition_keeps_recipe_yield_for_normalization():
    page = """
    <html><script type="application/ld+json">
    {
      "@type": "Recipe",
      "recipeYield": ["6", "6 people"],
      "nutrition": {
        "@type": "NutritionInformation",
        "calories": "6078 kcal",
        "carbohydrateContent": "283 g",
        "proteinContent": "149 g",
        "fatContent": "513 g",
        "fiberContent": "72 g"
      }
    }
    </script></html>
    """

    nutrition = pp.extract_nutrition(page)
    normalized, basis, servings = pp.normalize_per_serving(nutrition, {"source_servings": "6"})

    assert servings == 6
    assert basis == "normalized_by_source_recipe_yield"
    assert normalized["calories"] == 1013
    assert normalized["protein_grams"] == 24.8
    assert normalized["fiber_grams"] == 12.0


def test_normalization_keeps_plausible_per_serving_values():
    nutrition = {
        "calories": 435,
        "carbs_grams": 26.0,
        "protein_grams": 53.0,
        "fats_grams": 16.0,
        "fiber_grams": 9.0,
        "sodium_mg": 1657,
        "sugar_grams": 12.0,
        "source_recipe_yield": 4,
    }

    normalized, basis, servings = pp.normalize_per_serving(nutrition, {"source_servings": "4"})

    assert servings == 4
    assert basis == "source_published_per_serving"
    assert normalized["calories"] == 435


def test_merge_corrections_replaces_existing_recipe_rows():
    rows = pp.merge_corrections(
        [{"recipe_id": "ph_1", "calories": "350"}, {"recipe_id": "ph_2", "calories": "420"}],
        [{"recipe_id": "ph_1", "calories": "1013"}],
    )

    assert rows == [{"recipe_id": "ph_1", "calories": "1013"}, {"recipe_id": "ph_2", "calories": "420"}]
