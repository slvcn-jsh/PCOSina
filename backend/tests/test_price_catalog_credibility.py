import sys
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

import database
import price_catalog
from scripts.import_dti_srp_price_rules import import_rows


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_price_catalog_credibility"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"price_catalog_credibility_{uuid4().hex}.db"


def test_dti_srp_import_drives_explainable_price_estimates():
    database.DATABASE_URL = ""
    database.DB_NAME = str(_temp_db_path())
    database.init_db()
    price_catalog.invalidate_override_cache()

    csv_dir = Path(__file__).resolve().parent / ".tmp_price_catalog_credibility"
    csv_dir.mkdir(parents=True, exist_ok=True)
    csv_path = csv_dir / f"srp_{uuid4().hex}.csv"
    csv_path.write_text(
        "keywords,price_php,category,unit,effective,source,confidence,notes\n"
        '"garlic,bawang",150,Produce,kg,2026-05,DTI SRP,high,unit test baseline\n',
        encoding="utf-8",
    )

    imported = import_rows(csv_path)
    price_catalog.invalidate_override_cache()
    estimate = price_catalog.estimate_price_explained("bawang", "1 kg", month_index=5)

    assert imported[0]["pricePhp"] == 150
    assert estimate.source == "dti_srp"
    assert estimate.source_label == "DTI SRP baseline (2026-05)"
    assert estimate.confidence == "medium"
    assert estimate.price_php > 0
    assert estimate.matched_keywords == ["garlic", "bawang"]


def test_reviewed_market_unit_rule_uses_real_price_without_category_discount():
    database.DATABASE_URL = ""
    database.DB_NAME = str(_temp_db_path())
    database.init_db()
    database.upsert_price_rule(
        {
            "id": "reviewed_garlic",
            "keywords": ["garlic", "bawang"],
            "pricePhp": 146,
            "category": "Produce",
            "unit": "kg",
            "active": True,
            "notes": "source=reviewed_market; confidence=high; pricing_basis=market_unit; category_multiplier=none; source_url=https://www.dti.gov.ph/konsyumer/e-presyo/",
        }
    )
    price_catalog.invalidate_override_cache()

    estimate = price_catalog.estimate_price_explained("garlic", "1 kg", month_index=5)

    assert estimate.source == "reviewed_market"
    assert estimate.category_multiplier == 1.0
    assert estimate.price_php == 146


def test_reviewed_rule_priority_uses_matched_keyword_not_unmatched_synonyms():
    database.DATABASE_URL = ""
    database.DB_NAME = str(_temp_db_path())
    database.init_db()
    database.upsert_price_rule(
        {
            "id": "legacy_egg",
            "keywords": ["egg", "itlog"],
            "pricePhp": 9,
            "category": "Eggs & Dairy",
            "unit": "piece",
            "active": True,
            "notes": "source=DTI SRP; confidence=high",
        }
    )
    database.upsert_price_rule(
        {
            "id": "reviewed_egg",
            "keywords": ["egg"],
            "pricePhp": 8,
            "category": "Eggs & Dairy",
            "unit": "piece",
            "active": True,
            "notes": "source=reviewed_market; confidence=high; pricing_basis=market_unit; category_multiplier=none",
        }
    )
    price_catalog.invalidate_override_cache()

    estimate = price_catalog.estimate_price_explained("egg", "1 piece", month_index=5)

    assert estimate.source == "reviewed_market"
    assert estimate.base_price_php == 8
    assert estimate.category_multiplier == 1.0


def test_reviewed_zero_price_rule_can_represent_tap_water():
    database.DATABASE_URL = ""
    database.DB_NAME = str(_temp_db_path())
    database.init_db()
    database.upsert_price_rule(
        {
            "id": "reviewed_water",
            "keywords": ["water", "tap water"],
            "pricePhp": 1,
            "category": "Beverages",
            "unit": "l",
            "active": True,
            "notes": "source=reviewed_market; confidence=medium; pricing_basis=market_unit; category_multiplier=none; zero_price=true",
        }
    )
    price_catalog.invalidate_override_cache()

    estimate = price_catalog.estimate_price_explained("tap water", "1 L", month_index=5)

    assert estimate.source == "reviewed_market"
    assert estimate.base_price_php == 0
    assert estimate.price_php == 0


def test_static_tap_water_price_is_zero_without_database_override(monkeypatch):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(database, "get_market_multiplier", lambda category, month_index: 1.0)

    estimate = price_catalog.estimate_price_explained("tap water", "1 L", month_index=5)

    assert estimate.price_php == 0
    assert estimate.base_price_php == 0
    assert estimate.target_unit == "l"


def test_bangus_fillet_uses_product_specific_retail_rate(tmp_path, monkeypatch):
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(tmp_path / "bangus_fillet_prices.db"))
    database.init_db()
    price_catalog.invalidate_override_cache()
    context = price_catalog.create_pricing_context(month_index=9)

    fillet = price_catalog.estimate_price_explained(
        "Bangus Fillet",
        "110 g",
        pricing_context=context,
        clamp_quantity=False,
    )
    whole = price_catalog.estimate_price_explained(
        "Bangus",
        "110 g",
        pricing_context=context,
        clamp_quantity=False,
    )

    assert fillet.price_php == 43
    assert fillet.base_price_php == 388
    assert fillet.target_unit == "kg"
    assert fillet.category == "Meat/Seafood"
    assert fillet.source == "canonical_retail_observation"
    assert fillet.confidence == "medium"
    assert whole.price_php == 27
    assert whole.base_price_php == 243.62


def test_zero_water_rule_never_prices_compound_ingredients_as_free(monkeypatch):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(database, "get_market_multiplier", lambda category, month_index: 1.0)

    assert price_catalog.estimate_price_explained("water spinach", "1 kg").price_php > 0
    assert price_catalog.estimate_price_explained("canned tuna in water", "1 can").price_php > 0
    assert price_catalog.estimate_price_explained("coconut water", "1 L").price_php > 0
    assert price_catalog.estimate_price_explained("water", "1 glass").price_php == 0


def test_price_catalog_loads_more_than_legacy_500_rule_cap(monkeypatch):
    price_catalog.invalidate_override_cache()
    requested_limits: list[int] = []

    def fake_rules(limit: int = 500):
        requested_limits.append(limit)
        return []

    monkeypatch.setattr(database, "list_active_price_rules", fake_rules)

    price_catalog.estimate_price_explained("tomato", "1 kg", month_index=5)

    assert requested_limits
    assert requested_limits[0] > 500


def test_price_estimate_applies_default_seasonality_and_tingi_factor():
    database.DATABASE_URL = ""
    database.DB_NAME = str(_temp_db_path())
    database.init_db()
    price_catalog.invalidate_override_cache()

    rainy = price_catalog.estimate_price_explained("tomato", "1 kg", month_index=8)
    summer = price_catalog.estimate_price_explained("tomato", "1 kg", month_index=3)
    tingi = price_catalog.estimate_price_explained("tomato", "2 pieces", month_index=3)

    assert rainy.market_multiplier > summer.market_multiplier
    assert rainy.price_php > summer.price_php
    assert tingi.tingi_multiplier > 1.0


def test_price_estimate_uses_ingredient_specific_garlic_count_weights(monkeypatch):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(database, "get_market_multiplier", lambda category, month_index: 1.0)

    clove = price_catalog.estimate_price_explained("garlic", "1 clove", month_index=3)
    cloves = price_catalog.estimate_price_explained("garlic", "26 cloves", month_index=3)
    grams = price_catalog.estimate_price_explained("garlic", "130 g", month_index=3)
    head = price_catalog.estimate_price_explained("bawang", "1 head", month_index=3)

    assert clove.quantity_unit == "clove"
    assert clove.quantity_factor == 0.02
    assert clove.price_php <= 8
    assert abs(cloves.quantity_factor - grams.quantity_factor) < 0.001
    assert cloves.price_php < 25
    assert grams.price_php < 25
    assert head.quantity_factor < 0.06
    assert head.price_php <= 8


def test_market_multiplier_cache_bounds_db_calls(monkeypatch):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(database, "list_effective_canonical_price_refs", lambda **kwargs: {})
    monkeypatch.setattr(
        database,
        "list_market_multipliers_for_month",
        lambda month_index: (_ for _ in ()).throw(RuntimeError("preload unavailable")),
    )
    calls: list[tuple[str, int]] = []

    def fake_market_multiplier(category: str, month_index: int) -> float:
        calls.append((category, month_index))
        return 1.0

    monkeypatch.setattr(database, "get_market_multiplier", fake_market_multiplier)
    context = price_catalog.create_pricing_context(month_index=8)
    ingredients = [
        {"name": "tomato", "quantity": "1 kg"},
        {"name": "kamatis", "quantity": "1 kg"},
        {"name": "onion", "quantity": "1 kg"},
        {"name": "garlic", "quantity": "1 kg"},
        {"name": "chicken", "quantity": "1 kg"},
        {"name": "tilapia", "quantity": "1 kg"},
    ] * 20

    cost = price_catalog.estimate_recipe_cost(ingredients, pricing_context=context)
    diagnostics = context.snapshot()

    assert cost > 0
    assert set(calls) == {("Produce", 8), ("Meat/Seafood", 8)}
    assert diagnostics["marketMultiplierDbCalls"] <= 3
    assert diagnostics["marketMultiplierCacheMisses"] == 2
    assert diagnostics["marketMultiplierCacheHits"] >= 100
    assert diagnostics["ingredientPriceEstimateCount"] == len(ingredients)
    assert diagnostics["ingredientCostEstimates"] == len(ingredients)


def test_pricing_context_preloads_market_multipliers_once(monkeypatch):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(database, "list_effective_canonical_price_refs", lambda **kwargs: {})
    preload_calls: list[int] = []

    def fake_preload(month_index: int) -> dict[str, float]:
        preload_calls.append(month_index)
        return {"Produce": 1.25, "Meat/Seafood": 1.10}

    monkeypatch.setattr(database, "list_market_multipliers_for_month", fake_preload)
    monkeypatch.setattr(
        database,
        "get_market_multiplier",
        lambda category, month_index: (_ for _ in ()).throw(AssertionError("per-category fallback should not run")),
    )
    context = price_catalog.create_pricing_context(month_index=8)

    tomato = price_catalog.estimate_price_explained("tomato", "1 kg", pricing_context=context)
    chicken = price_catalog.estimate_price_explained("chicken", "1 kg", pricing_context=context)
    onion = price_catalog.estimate_price_explained("onion", "1 kg", pricing_context=context)
    diagnostics = context.snapshot()

    assert tomato.market_multiplier == 1.25
    assert chicken.market_multiplier == 1.10
    assert onion.market_multiplier == 1.25
    assert preload_calls == [8]
    assert diagnostics["marketMultiplierDbCalls"] == 1
    assert diagnostics["marketMultiplierCacheHits"] == 2
    assert diagnostics["marketMultiplierCacheMisses"] == 1


def test_missing_market_multiplier_falls_back_without_repeated_db_calls(monkeypatch):
    price_catalog.invalidate_override_cache()
    monkeypatch.setattr(database, "list_active_price_rules", lambda limit=500: [])
    monkeypatch.setattr(
        database,
        "list_market_multipliers_for_month",
        lambda month_index: (_ for _ in ()).throw(RuntimeError("preload unavailable")),
    )
    calls: list[tuple[str, int]] = []

    def fake_market_multiplier(category: str, month_index: int) -> float:
        calls.append((category, month_index))
        return 1.0

    monkeypatch.setattr(database, "get_market_multiplier", fake_market_multiplier)
    context = price_catalog.create_pricing_context(month_index=5)

    first = price_catalog.estimate_price_explained(
        "unknown pantry item",
        "1 piece",
        month_index=5,
        pricing_context=context,
    )
    second = price_catalog.estimate_price_explained(
        "unknown pantry item",
        "1 piece",
        month_index=5,
        pricing_context=context,
    )

    assert first.market_multiplier == 1.0
    assert second.market_multiplier == 1.0
    assert calls == [("Others", 5)]
    assert context.snapshot()["marketMultiplierCacheHits"] == 1


def test_list_market_multipliers_for_month_returns_category_map():
    database.DATABASE_URL = ""
    database.DB_NAME = str(_temp_db_path())
    database.init_db()
    conn = database._connect()
    try:
        cur = conn.cursor()
        now = 1_700_000_000_000
        cur.execute(
            """
            INSERT OR REPLACE INTO market_seasonality_rules
                (id, category, month_index, multiplier, notes, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ("produce_8", "Produce", 8, 1.3, "unit test", now),
        )
        cur.execute(
            """
            INSERT OR REPLACE INTO market_seasonality_rules
                (id, category, month_index, multiplier, notes, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ("meat_8", "Meat/Seafood", 8, 1.1, "unit test", now),
        )
        conn.commit()
    finally:
        conn.close()

    multipliers = database.list_market_multipliers_for_month(8)

    assert multipliers == {"Produce": 1.3, "Meat/Seafood": 1.1}
    assert database.list_market_multipliers_for_month(99) == {}
