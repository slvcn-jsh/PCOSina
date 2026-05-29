import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from services.grocery_aggregator import aggregate_grocery_list, price_grocery_buckets


def test_aggregate_grocery_list_canonicalizes_and_sums_garlic_units():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "bawang", "quantity": "3 cloves"},
                            {"name": "minced garlic", "quantity": "2 tbsp"},
                        ]
                    }
                ]
            }
        ]
    )

    assert list(result.keys()) == ["garlic"]
    assert result["garlic"]["name"] == "Garlic"
    assert result["garlic"]["displayQuantity"] == "45 g"
    assert result["garlic"]["originalNames"] == ["bawang", "minced garlic"]


def test_aggregate_grocery_list_extracts_quantity_from_ingredient_text():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "3 cloves garlic, minced", "quantity": ""},
                            {"name": "bawang", "quantity": "2 cloves"},
                        ]
                    }
                ]
            }
        ]
    )

    assert result["garlic"]["displayQuantity"] == "25 g"


def test_aggregate_grocery_list_applies_serving_scale():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "egg", "quantity": "6 pcs", "scale": 0.5},
                        ]
                    }
                ]
            }
        ]
    )

    assert result["egg"]["displayQuantity"] == "165 g"


def test_price_grocery_buckets_returns_budget_authority_payload():
    buckets = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "egg", "quantity": "3 pcs"},
                        ]
                    }
                ]
            }
        ]
    )

    output = price_grocery_buckets(buckets, weekly_budget_php=100)

    assert output["authority"] == "backend_aggregated_grocery"
    assert output["estimatedTotalPhp"] > 0
    assert output["withinBudget"] is True
    assert output["items"][0]["estimatedCostPhp"] > 0
