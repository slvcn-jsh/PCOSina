import csv
import sys
from pathlib import Path
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = ROOT / "backend"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from scripts.import_reviewed_price_audit import reviewed_price_rule_rows
import database
import price_catalog


def test_reviewed_price_audit_rows_normalize_market_units_and_notes():
    rows = reviewed_price_rule_rows(
        [
            {
                "canonical_key": "fish_sauce",
                "display_name": "Fish Sauce",
                "suggested_keywords": "fish sauce, patis",
                "category": "Spices & Condiments",
                "suggested_unit": "piece",
                "real_price_php": "90",
                "real_unit": "L",
                "price_min_php": "75",
                "price_max_php": "100",
                "market_source": "Audit source; with semicolon",
                "source_url": "https://example.test/source",
                "survey_date": "2026-05-29",
                "pricing_confidence_final": "Medium-Low - non-DA estimate",
                "needs_manual_validation": "Yes",
                "implementation_ready_status": "Ready with unit-conversion note; validate special cases",
                "unit_conversion_note": "Use mL conversion.",
            },
            {
                "canonical_key": "egg",
                "display_name": "Egg",
                "suggested_keywords": "egg, itlog",
                "category": "Eggs & Dairy",
                "real_price_php": "7.99",
                "real_unit": "pc",
                "pricing_confidence_final": "High - DA exact",
                "needs_manual_validation": "No",
                "survey_date": "2026-05-18 to 2026-05-23",
            },
            {
                "canonical_key": "water",
                "display_name": "Water",
                "suggested_keywords": "water, tap water",
                "category": "Beverages",
                "real_price_php": "0",
                "real_unit": "L",
                "pricing_confidence_final": "Medium-Low - non-DA estimate",
                "needs_manual_validation": "Yes",
                "survey_date": "2026-05-29",
            },
        ]
    )

    fish_sauce = rows[0]
    egg = rows[1]
    water = rows[2]

    assert fish_sauce["unit"] == "l"
    assert fish_sauce["confidence"] == "medium"
    assert fish_sauce["price_php"] == "90"
    assert fish_sauce["active"] == "false"
    assert "pricing_basis=market_unit" in fish_sauce["notes"]
    assert "category_multiplier=none" in fish_sauce["notes"]
    assert "Audit source, with semicolon" in fish_sauce["notes"]
    assert egg["unit"] == "piece"
    assert egg["price_php"] == "8"
    assert egg["confidence"] == "high"
    assert egg["active"] == "true"
    assert water["price_php"] == "1"
    assert water["zero_price"] == "true"
    assert water["active"] == "false"
    assert "zero_price=true" in water["notes"]


def test_reviewed_price_seed_csv_updates_database_and_price_catalog(tmp_path):
    database.DATABASE_URL = ""
    database.DB_NAME = str(tmp_path / f"reviewed_prices_{uuid4().hex}.db")
    database.init_db()
    seed_path = tmp_path / "reviewed_market_price_rules.csv"
    seed_path.write_text(
        "id,keywords,price_php,price_min_php,price_max_php,category,unit,active,source,confidence,effective,pricing_basis,zero_price,market_source,source_url,review_status,needs_manual_validation,notes\n"
        "reviewed_tomato,\"tomato, kamatis\",64,50,80,Produce,kg,true,reviewed_market,high,2026-05-29,market_unit,false,DA,test,Ready,false,\"source=reviewed_market; confidence=high; pricing_basis=market_unit; category_multiplier=none\"\n",
        encoding="utf-8",
    )

    summary = database.seed_reviewed_price_rules(str(seed_path))
    unchanged = database.seed_reviewed_price_rules(str(seed_path))
    price_catalog.invalidate_override_cache()
    estimate = price_catalog.estimate_price_explained("kamatis", "1 kg", month_index=5)

    assert summary["sourceCount"] == 1
    assert summary["insertedCount"] == 1
    assert unchanged["updatedCount"] == 0
    assert unchanged["skippedExistingCount"] == 1
    assert estimate.source == "reviewed_market"
    assert estimate.price_php == 64
    assert estimate.category_multiplier == 1.0
    assert estimate.market_multiplier == 1.0
    assert estimate.tingi_multiplier == 1.0


def test_bundled_bangus_fillet_rule_uses_product_specific_retail_source():
    seed_path = BACKEND_ROOT / "seed_data" / "reviewed_market_price_rules.csv"
    with seed_path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = {row["id"]: row for row in csv.DictReader(handle)}

    fillet = rows["reviewed_bangus_fillet"]

    assert fillet["category"] == "Meat/Seafood"
    assert fillet["unit"] == "kg"
    assert fillet["price_php"] == "388"
    assert fillet["confidence"] == "medium"
    assert fillet["effective"] == "2026-09-08"
    assert fillet["source_url"] == "https://shopmetro.ph/product/boneless-bangus-1kg/"
    assert "whole bangus" in fillet["notes"]


def test_reviewed_price_seed_keeps_unvalidated_and_zero_rows_inactive():
    assert database.reviewed_price_rule_is_production_eligible(
        {
            "active": "true",
            "confidence": "high",
            "needs_manual_validation": "false",
            "review_status": "Ready for base-price import",
            "zero_price": "false",
        }
    )
    assert not database.reviewed_price_rule_is_production_eligible(
        {
            "active": "true",
            "confidence": "low",
            "needs_manual_validation": "true",
            "review_status": "Needs local price validation before import",
            "market_source": "PCOSina catalog fallback pending local validation",
            "zero_price": "false",
        }
    )
    assert not database.reviewed_price_rule_is_production_eligible(
        {
            "active": "true",
            "confidence": "high",
            "needs_manual_validation": "false",
            "review_status": "Ready",
            "zero_price": "true",
        }
    )
