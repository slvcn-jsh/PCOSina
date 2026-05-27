import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from services.grocery_aggregator import aggregate_grocery_list


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


def test_aggregate_grocery_list_handles_expanded_count_units():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "1 head garlic, crushed", "quantity": ""},
                            {"name": "luya", "quantity": "1 piece"},
                            {"name": "malunggay", "quantity": "1 bunch"},
                        ]
                    }
                ]
            }
        ]
    )

    assert result["garlic"]["displayQuantity"] == "45 g"
    assert result["ginger"]["displayQuantity"] == "20 g"
    assert result["malunggay"]["displayQuantity"] == "80 g"
