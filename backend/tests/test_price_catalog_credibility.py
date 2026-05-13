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


def test_market_multiplier_cache_bounds_db_calls(monkeypatch):
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
