import sys
from pathlib import Path

import pytest
from fastapi import HTTPException

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database
import main
import price_catalog
from domain.models import PersonalPriceOverrideRequest


def _use_database(tmp_path, monkeypatch):
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(tmp_path / "personal_prices.db"))
    database.init_db()
    price_catalog.invalidate_override_cache()


def test_user_price_override_is_scoped_applied_and_can_be_reset(tmp_path, monkeypatch):
    _use_database(tmp_path, monkeypatch)
    database.upsert_canonical_price_override(
        ingredient_id="ing_bangus_fillet",
        price_php=510,
        unit="kg",
        scope="user_override",
        owner_uid="alice",
        market_type="wet_market",
        source="user_reported_price",
        source_date="2026-09-09",
        valid_until="2027-03-08",
    )

    alice = price_catalog.estimate_price_explained(
        "bangus fillet",
        "1 kg",
        pricing_context=price_catalog.create_pricing_context(owner_uid="alice"),
        clamp_quantity=False,
    )
    bob = price_catalog.estimate_price_explained(
        "bangus fillet",
        "1 kg",
        pricing_context=price_catalog.create_pricing_context(owner_uid="bob"),
        clamp_quantity=False,
    )

    assert alice.price_php == 510
    assert alice.source == "canonical_user_override"
    assert bob.price_php != 510
    assert database.deactivate_canonical_price_override(
        ingredient_id="ing_bangus_fillet",
        owner_uid="alice",
    ) is True
    assert database.list_canonical_price_overrides_for_user("alice") == []


def test_expired_user_price_does_not_override_current_reference(tmp_path, monkeypatch):
    _use_database(tmp_path, monkeypatch)
    database.upsert_canonical_price_override(
        ingredient_id="ing_bangus_fillet",
        price_php=999,
        unit="kg",
        scope="user_override",
        owner_uid="alice",
        source="user_reported_price",
        source_date="2025-01-01",
        valid_until="2025-06-30",
    )

    estimate = price_catalog.estimate_price_explained(
        "bangus fillet",
        "1 kg",
        pricing_context=price_catalog.create_pricing_context(owner_uid="alice"),
        clamp_quantity=False,
    )

    assert estimate.price_php != 999
    assert estimate.source != "canonical_user_override"
    assert database.list_canonical_price_overrides_for_user("alice") == []


def test_personal_price_applies_across_saved_locality_and_refreshes_metadata(tmp_path, monkeypatch):
    _use_database(tmp_path, monkeypatch)
    database.upsert_canonical_price_override(
        ingredient_id="ing_bangus_fillet",
        price_php=440,
        unit="kg",
        scope="user_override",
        owner_uid="alice",
        location="Quezon City",
        market_type="wet_market",
        source_date="2026-09-01",
        valid_until="2026-10-01",
    )
    database.upsert_canonical_price_override(
        ingredient_id="ing_bangus_fillet",
        price_php=525,
        unit="kg",
        scope="user_override",
        owner_uid="alice",
        location="Pasig City",
        market_type="supermarket",
        source_date="2026-09-09",
        valid_until="2027-03-08",
    )

    saved = database.list_canonical_price_overrides_for_user("alice")[0]
    estimate = price_catalog.estimate_price_explained(
        "bangus fillet",
        "1 kg",
        pricing_context=price_catalog.create_pricing_context(owner_uid="alice", location="NCR"),
        clamp_quantity=False,
    )

    assert saved["pricePhp"] == 525
    assert saved["location"] == "Pasig City"
    assert saved["marketType"] == "supermarket"
    assert saved["observedOn"] == "2026-09-09"
    assert saved["validUntil"] == "2027-03-08"
    assert estimate.price_php == 525
    assert estimate.source == "canonical_user_override"


def test_personal_price_endpoint_warns_on_outlier_without_rejecting(tmp_path, monkeypatch):
    _use_database(tmp_path, monkeypatch)
    payload = PersonalPriceOverrideRequest(
        ingredient="bangus fillet",
        pricePhp=1500,
        unit="kg",
        marketType="wet_market",
        location="NCR",
        observedOn="2026-09-09",
    )

    saved = main.save_personal_price(payload, {"uid": "alice"}, None, None)

    assert saved.ingredientId == "ing_bangus_fillet"
    assert saved.pricePhp == 1500
    assert saved.warning is not None
    assert "was saved" in saved.warning
    assert main.list_personal_prices({"uid": "alice"}, None, None)[0].pricePhp == 1500
    assert main.list_personal_prices({"uid": "bob"}, None, None) == []


def test_personal_price_endpoint_keeps_household_water_zero(tmp_path, monkeypatch):
    _use_database(tmp_path, monkeypatch)
    payload = PersonalPriceOverrideRequest(
        ingredient="water",
        pricePhp=50,
        unit="l",
    )

    with pytest.raises(HTTPException) as exc:
        main.save_personal_price(payload, {"uid": "alice"}, None, None)

    assert exc.value.status_code == 400
    assert "zero-cost" in str(exc.value.detail)


def test_planner_wrapper_passes_owner_to_pricing_context(monkeypatch):
    captured = {}

    def fake_solver(request, recipes, **kwargs):
        captured.update(kwargs)
        return [], "ok", {}

    monkeypatch.setattr(main, "solve_meal_plan", fake_solver)
    request = main.GeneratePlanRequest(profile={})

    main._solve_with_user_ml_context(request, [], {}, uid="alice")

    assert captured["owner_uid"] == "alice"
