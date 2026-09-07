import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database
import price_catalog
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


def test_aggregate_grocery_list_uses_filipino_produce_names_and_realistic_cup_weights():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "malunggay/leaves", "quantity": "4 cups"},
                            {"name": "kalabasa cubed small", "quantity": "2 cups"},
                        ]
                    }
                ]
            }
        ]
    )

    assert "leaves" not in result
    assert "cubed" not in result
    assert result["malunggay leaves"]["name"] == "Malunggay Leaves"
    assert result["malunggay leaves"]["displayQuantity"] == "120 g"
    assert result["kalabasa"]["name"] == "Kalabasa"
    assert result["kalabasa"]["displayQuantity"] == "280 g"


def test_aggregate_grocery_list_converts_cooked_monggo_to_dry_grocery_equivalent():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "cooked monggo", "quantity": "3 cups"},
                            {"name": "monggo", "quantity": "180 g"},
                        ]
                    }
                ]
            }
        ]
    )

    assert "cooked" not in result
    assert result["monggo"]["name"] == "Monggo"
    assert result["monggo"]["displayQuantity"] == "420 g"
    assert result["monggo"]["originalNames"] == ["cooked monggo", "monggo"]


def test_aggregate_grocery_list_does_not_parse_large_or_long_as_liters():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "1 large bangus milkfish, cleaned and sliced", "quantity": ""},
                            {"name": "12 long green beans cut into 2 inch pieces", "quantity": ""},
                        ]
                    }
                ]
            }
        ]
    )

    assert "and" not in result
    assert "2" not in result
    assert result["bangus"]["displayQuantity"] == "450 g"
    assert result["string beans"]["displayQuantity"] == "120 g"


def test_aggregate_grocery_list_keeps_liquid_spoons_volume_based():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "2 tablespoons soy sauce", "quantity": ""},
                            {"name": "1 tablespoon canola oil", "quantity": ""},
                        ]
                    }
                ]
            }
        ]
    )

    assert result["soy sauce"]["displayQuantity"] == "30 ml"
    assert result["canola oil"]["displayQuantity"] == "15 ml"


def test_aggregate_grocery_list_uses_small_piece_weight_for_bay_leaves():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "4 pieces dried bay leaves", "quantity": ""},
                            {"name": "2 bay leaves dahon ng laurel", "quantity": ""},
                        ]
                    }
                ]
            }
        ]
    )

    assert "bay" not in result
    assert "laurel" not in result
    assert result["bay leaves"]["name"] == "Bay Leaves"
    assert result["bay leaves"]["displayQuantity"] == "1.5 g"


def test_aggregate_grocery_list_uses_local_aliases_and_leading_counts():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "2 medium potatoes peeled and quartered (patatas)", "quantity": ""},
                            {"name": "3 pieces Finger Chilies (Siling Pangsigang)", "quantity": ""},
                        ]
                    }
                ]
            }
        ]
    )

    assert "quartered" not in result
    assert "patatas" not in result
    assert result["potato"]["displayQuantity"] == "300 g"
    assert result["siling pangsigang"]["displayQuantity"] == "24 g"


def test_aggregate_grocery_list_keeps_water_spinach_separate_from_water():
    result = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "water spinach", "quantity": "1 bunch"},
                            {"name": "water", "quantity": "1 L"},
                        ]
                    }
                ]
            }
        ]
    )

    assert set(result.keys()) == {"kangkong", "water"}
    assert result["kangkong"]["name"] == "Kangkong"
    assert result["kangkong"]["displayQuantity"] == "180 g"
    assert result["water"]["name"] == "Water"
    assert result["water"]["displayQuantity"] == "1 L"


def test_aggregate_grocery_list_treats_recipe_glasses_as_water_volume():
    plan = [
        {"meals": [{"ingredients": [{"name": "water", "quantity": "1 glass"}]}]}
        for _ in range(21)
    ]

    result = aggregate_grocery_list(plan)

    assert result["water"]["unit"] == "ml"
    assert result["water"]["totalValue"] == 5040.0
    assert result["water"]["displayQuantity"] == "5.04 L"


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
    assert output["pricingCatalogVersion"] == "pcosina-ncr-retail-2026-09-08-v2"
    assert output["pricingReferenceDate"] == "2026-09-08"
    assert output["pricingReferenceLocation"] == "NCR"
    assert output["pricingBasis"] == "required_quantity_at_item_specific_retail_reference"
    assert sum(output["pricingSourceCounts"].values()) == output["itemCount"]
    assert output["estimatedTotalPhp"] > 0
    assert output["withinBudget"] is True
    assert output["items"][0]["estimatedCostPhp"] > 0


def test_price_grocery_buckets_does_not_cap_weekly_piece_quantities(tmp_path, monkeypatch):
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(tmp_path / "weekly_piece_prices.db"))
    database.init_db()
    price_catalog.invalidate_override_cache()
    buckets = aggregate_grocery_list(
        [{"meals": [{"ingredients": [{"name": "egg", "quantity": "12 pcs"}]}]}]
    )

    output = price_grocery_buckets(buckets)
    eggs = next(item for item in output["items"] if item["key"] == "egg")

    assert eggs["estimatedCostPhp"] == 97


def test_price_grocery_buckets_keeps_water_free_and_total_equal_to_items(tmp_path, monkeypatch):
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(tmp_path / "grocery_prices.db"))
    database.init_db()
    price_catalog.invalidate_override_cache()
    buckets = aggregate_grocery_list(
        [
            {
                "meals": [
                    {
                        "ingredients": [
                            {"name": "water", "quantity": "1 L"},
                            {"name": "egg", "quantity": "3 pcs"},
                        ]
                    }
                ]
            }
        ]
    )

    output = price_grocery_buckets(buckets, weekly_budget_php=100)
    water = next(item for item in output["items"] if item["key"] == "water")

    assert water["estimatedCostPhp"] == 0
    assert output["estimatedTotalPhp"] == sum(item["estimatedCostPhp"] for item in output["items"])
    assert output["finalGroceryEstimatePhp"] == output["estimatedTotalPhp"]


def test_price_grocery_buckets_explains_bangus_fillet_retail_math(tmp_path, monkeypatch):
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(tmp_path / "bangus_fillet_grocery.db"))
    database.init_db()
    price_catalog.invalidate_override_cache()
    buckets = aggregate_grocery_list(
        [{"meals": [{"ingredients": [{"name": "Bangus Fillet", "quantity": "110 g"}]}]}]
    )

    output = price_grocery_buckets(buckets, weekly_budget_php=100)
    item = output["items"][0]

    assert item["key"] == "bangus fillet"
    assert item["quantity"] == "110 g"
    assert item["requiredQuantity"] == "110 g"
    assert item["purchaseQuantity"] == "110 g"
    assert item["purchaseMode"] == "weighed_to_order"
    assert item["unitPricePhp"] == 388
    assert item["priceUnit"] == "kg"
    assert item["estimatedCostPhp"] == 43
    assert "boneless bangus" in item["sourceLabel"].lower()
    assert output["estimatedTotalPhp"] == 43
    assert output["withinBudget"] is True
