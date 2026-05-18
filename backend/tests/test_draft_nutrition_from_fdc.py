import sys
from pathlib import Path
from urllib.error import HTTPError
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import draft_nutrition_from_fdc as fdc


class FakeResolver:
    def __init__(self):
        self.foods = {
            "chicken": {
                "fdcId": 1,
                "nutrientsPer100g": {
                    "calories": 165,
                    "protein_grams": 31,
                    "carbs_grams": 0,
                    "fats_grams": 3.6,
                    "fiber_grams": 0,
                    "sodium_mg": 74,
                    "sugar_grams": 0,
                },
            },
            "rice": {
                "fdcId": 2,
                "nutrientsPer100g": {
                    "calories": 130,
                    "protein_grams": 2.7,
                    "carbs_grams": 28,
                    "fats_grams": 0.3,
                    "fiber_grams": 0.4,
                    "sodium_mg": 1,
                    "sugar_grams": 0.1,
                },
            },
            "cooking oil": {
                "fdcId": 3,
                "nutrientsPer100g": {
                    "calories": 884,
                    "protein_grams": 0,
                    "carbs_grams": 0,
                    "fats_grams": 100,
                    "fiber_grams": 0,
                    "sodium_mg": 0,
                    "sugar_grams": 0,
                },
            },
        }

    def search_nutrients(self, query: str):
        return self.foods.get(query)


class EmptyResolver:
    def search_nutrients(self, query: str):
        return None


class HugeResolver:
    def search_nutrients(self, query: str):
        return {
            "fdcId": 99,
            "nutrientsPer100g": {
                "calories": 4000,
                "protein_grams": 500,
                "carbs_grams": 0,
                "fats_grams": 0,
                "fiber_grams": 0,
                "sodium_mg": 0,
                "sugar_grams": 0,
            },
        }


def test_parse_ingredient_handles_mass_volume_and_pieces():
    chicken = fdc.parse_ingredient("1 1/2 lbs chicken cut into serving pieces")
    oil = fdc.parse_ingredient("3 tablespoons cooking oil")
    garlic = fdc.parse_ingredient("5 cloves garlic")
    coconut = fdc.parse_ingredient("4 cups coconut milk")
    pork = fdc.parse_ingredient("11/2 pounds pork shoulder")
    pack = fdc.parse_ingredient("2 (200-ml) packs coconut cream")
    frying_oil = fdc.parse_ingredient("4 cups vegetable oil")
    noodles = fdc.parse_ingredient("120-140 Grams dried pancit canton")
    water = fdc.parse_ingredient("8 quarts water for boiling pork")

    assert chicken.query == "chicken"
    assert round(chicken.grams or 0) == 680
    assert oil.query == "cooking oil"
    assert round(oil.grams or 0, 1) == 40.8
    assert garlic.query == "garlic"
    assert garlic.grams == 15
    assert coconut.query == "coconut milk"
    assert coconut.grams == 960
    assert pork.query == "pork shoulder"
    assert round(pork.grams or 0) == 680
    assert pack.query == "coconut cream"
    assert pack.grams == 400
    assert frying_oil.reason == "ignored frying oil reserve"
    assert noodles.query == "dried pancit canton"
    assert noodles.grams == 130
    assert water.reason == "ignored seasoning/water"


def test_parse_servings_handles_ranges_and_piece_counts():
    assert fdc.parse_servings("4 to 6") == 5
    assert fdc.parse_servings("6-8 persons") == 7
    assert fdc.parse_servings("24 pieces") == 24
    assert fdc.parse_servings("around 1/2 cup") == 1


def test_draft_row_calculates_per_serving_but_keeps_pending_review():
    row = {
        "priority": "P0",
        "recipe_id": "draft-1",
        "title": "Draft Chicken Rice",
        "meal_type": "Lunch",
        "source_servings": "2",
        "ingredients": "100 grams chicken | 1 cup rice | 1 tablespoon cooking oil",
    }

    draft = fdc.draft_row(row, FakeResolver())

    assert draft["draft_status"] == "draft_ready_for_review"
    assert draft["calories"] == 299
    assert draft["protein_grams"] == 18.7
    assert draft["carbs_grams"] == 33.6
    assert draft["fats_grams"] == 9.0
    assert draft["source"] == "usda_fdc_ingredient_sum_draft"
    assert draft["confidence"] == "api_estimate"
    assert draft["review_status"] == "pending_review"
    assert "Reviewer must verify" in draft["notes"]


def test_local_reference_resolver_produces_pending_estimate():
    row = {
        "priority": "P1",
        "recipe_id": "local-1",
        "title": "Local Chicken Tinola",
        "meal_type": "Dinner",
        "source_servings": "4",
        "ingredients": "1 lb chicken | 1 piece sayote | 1 thumb ginger | 1 tablespoon cooking oil",
    }

    draft = fdc.draft_row(row, fdc.LocalReferenceResolver())

    assert draft["draft_status"] == "draft_ready_for_review"
    assert draft["calories"] > 0
    assert draft["protein_grams"] > 0
    assert draft["source"] == "local_reference_ingredient_sum_draft"
    assert draft["confidence"] == "api_estimate"
    assert draft["review_status"] == "pending_review"


def test_draft_row_accepts_serving_range_but_keeps_pending_review():
    row = {
        "priority": "P1",
        "recipe_id": "range-1",
        "title": "Range Chicken Tinola",
        "meal_type": "Dinner",
        "source_servings": "4 to 6",
        "ingredients": "1 lb chicken | 4 cups coconut milk | 1 tablespoon canola oil",
    }

    draft = fdc.draft_row(row, fdc.LocalReferenceResolver())

    assert draft["draft_status"] == "draft_ready_for_review"
    assert draft["calories"] > 0
    assert "source_servings=5" in draft["notes"]
    assert draft["confidence"] == "api_estimate"
    assert draft["review_status"] == "pending_review"


def test_draft_row_uses_labeled_defaults_when_source_has_names_without_quantities():
    row = {
        "priority": "P2",
        "recipe_id": "missing-qty-1",
        "title": "Missing Quantity Dinuguan",
        "meal_type": "Dinner",
        "source_servings": "1",
        "ingredients": "coconut milk (gata) | Garlic shredded | onion | tomatoes | Pig Parts chopped | Vinegar",
    }

    draft = fdc.draft_row(row, fdc.LocalReferenceResolver())

    assert draft["draft_status"] == "draft_missing_quantity_estimate"
    assert draft["calories"] > 0
    assert draft["source"] == "local_reference_missing_quantity_draft"
    assert draft["confidence"] == "api_estimate"
    assert draft["review_status"] == "pending_review"
    assert "default household quantities" in draft["notes"]


def test_draft_row_leaves_values_blank_when_no_matches():
    row = {
        "priority": "P0",
        "recipe_id": "draft-blank",
        "source_servings": "2",
        "ingredients": "100 grams unknown ingredient",
    }

    draft = fdc.draft_row(row, EmptyResolver())

    assert draft["draft_status"] == "needs_manual_nutrition_review"
    assert draft["calories"] == ""
    assert draft["protein_grams"] == ""
    assert draft["source"] == ""
    assert draft["review_status"] == ""


def test_draft_row_leaves_values_blank_when_outside_import_bounds():
    row = {
        "priority": "P0",
        "recipe_id": "draft-high",
        "source_servings": "1",
        "ingredients": "100 grams pork",
    }

    draft = fdc.draft_row(row, HugeResolver())

    assert draft["draft_status"] == "needs_manual_nutrition_review"
    assert draft["calories"] == ""
    assert "outside database import bounds" in draft["unmatched_ingredients"]


def test_draft_rows_filters_priority_and_limit():
    rows = [
        {"priority": "P0", "recipe_id": "a", "source_servings": "2", "ingredients": "100 grams chicken"},
        {"priority": "P1", "recipe_id": "b", "source_servings": "2", "ingredients": "100 grams chicken"},
        {"priority": "P0", "recipe_id": "c", "source_servings": "2", "ingredients": "100 grams chicken"},
    ]

    drafted, summary = fdc.draft_rows(rows, FakeResolver(), priority="P0", limit=1)

    assert [row["recipe_id"] for row in drafted] == ["a"]
    assert summary["consideredRows"] == 1
    assert summary["draftCount"] == 1


def test_fdc_client_treats_bad_query_as_unmatched(tmp_path):
    client = fdc.FdcClient("DEMO_KEY", tmp_path / "cache.json")

    def raise_404(_request, timeout=30):
        raise HTTPError("https://example.test", 404, "Not Found", None, None)

    with patch("urllib.request.urlopen", raise_404):
        assert client.search_nutrients("bad query") is None

    assert client.cache["bad query"] == {}


def test_select_best_food_penalizes_wrong_food_forms():
    foods = [
        {
            "fdcId": 10,
            "description": "Garlic sauce",
            "dataType": "Survey (FNDDS)",
            "score": 500,
            "foodNutrients": [],
        },
        {
            "fdcId": 11,
            "description": "Garlic, raw",
            "dataType": "SR Legacy",
            "score": 200,
            "foodNutrients": [],
        },
    ]

    best = fdc.select_best_food(foods, "garlic")

    assert best["fdcId"] == 11


def test_fdc_client_marks_rate_limit_without_crashing(tmp_path):
    client = fdc.FdcClient("DEMO_KEY", tmp_path / "cache.json")

    def raise_429(_request, timeout=30):
        raise HTTPError("https://example.test", 429, "Too Many Requests", None, None)

    with patch("urllib.request.urlopen", raise_429):
        assert client.search_nutrients("pepper") is None

    assert client.rate_limited is True
    assert client.search_nutrients("garlic") is None
