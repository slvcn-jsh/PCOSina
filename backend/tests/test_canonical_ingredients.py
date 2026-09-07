import csv
import json
import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = ROOT / "backend"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database
import price_catalog
from canonical_ingredients import IngredientAliasSeed, resolve_ingredient
from scripts.audit_ingredients import build_audit, write_outputs


def test_resolver_preserves_specific_ingredient_identity_and_quantity():
    resolution = resolve_ingredient("1 1/2 lbs. pork belly diced")

    assert resolution.status == "mapped"
    assert resolution.ingredient_id == "ing_pork_belly_raw"
    assert resolution.canonical_name == "pork belly"
    assert resolution.matched_alias == "pork belly"
    assert resolution.quantity_value == 1.5
    assert resolution.quantity_unit == "lb"


def test_resolver_maps_generic_terms_without_guessing_specific_food_form():
    resolution = resolve_ingredient("2 kg manok chopped")

    assert resolution.status == "mapped"
    assert resolution.ingredient_id == "ing_chicken_generic"
    assert resolution.ingredient_id != "ing_chicken_breast_raw"


def test_resolver_keeps_bangus_fillet_distinct_from_whole_bangus():
    fillet = resolve_ingredient("110 g fresh bangus fillet")
    boneless = resolve_ingredient("boneless milkfish")
    whole = resolve_ingredient("1 kg bangus")

    assert fillet.ingredient_id == "ing_bangus_fillet"
    assert boneless.ingredient_id == "ing_bangus_fillet"
    assert whole.ingredient_id == "ing_bangus"


def test_resolver_reports_ambiguous_aliases_instead_of_selecting_one():
    aliases = (
        IngredientAliasSeed("ing_onion_red", "special onion"),
        IngredientAliasSeed("ing_onion_white", "special onion"),
    )

    resolution = resolve_ingredient("1 special onion", aliases=aliases)

    assert resolution.status == "ambiguous"
    assert resolution.ingredient_id is None
    assert resolution.candidate_ids == ("ing_onion_red", "ing_onion_white")


def test_canonical_schema_migration_creates_and_seeds_reference_tables(tmp_path, monkeypatch):
    db_path = tmp_path / "canonical.db"
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))

    database.init_db()

    conn = sqlite3.connect(db_path)
    try:
        table_names = {
            row[0]
            for row in conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'").fetchall()
        }
        assert {
            "canonical_ingredients",
            "ingredient_aliases",
            "ingredient_unit_conversions",
            "ingredient_nutrition_refs",
            "ingredient_price_refs",
            "recipe_ingredient_links",
            "ingredient_allergen_links",
        } <= table_names
        assert conn.execute("SELECT COUNT(*) FROM canonical_ingredients").fetchone()[0] >= 40
        assert conn.execute("SELECT COUNT(*) FROM ingredient_aliases").fetchone()[0] >= 60
        assert conn.execute("SELECT COUNT(*) FROM ingredient_price_refs").fetchone()[0] >= 20
        assert conn.execute("SELECT COUNT(*) FROM ingredient_nutrition_refs").fetchone()[0] == 0
        fish_sauce = conn.execute(
            """
            SELECT a.ingredient_id
            FROM ingredient_aliases a
            WHERE a.normalized_alias = 'patis'
            """
        ).fetchone()
        assert fish_sauce == ("ing_fish_sauce",)
    finally:
        conn.close()


def test_read_only_audit_reports_mapped_unmapped_and_recipe_coverage(tmp_path):
    recipes = [
        {
            "id": "r1",
            "name": "Audit Recipe",
            "mealType": "Dinner",
            "ingredients": [
                {"name": "8 cloves bawang minced", "quantity": ""},
                {"name": "1 mystery leaf", "quantity": ""},
            ],
        }
    ]

    outputs, summary = build_audit(recipes)
    paths = write_outputs(tmp_path, outputs, summary)

    assert summary["ingredient_occurrence_count"] == 2
    assert summary["mapped_occurrence_count"] == 1
    assert summary["unmapped_occurrence_count"] == 1
    assert summary["ambiguous_occurrence_count"] == 0
    assert outputs["raw"][0]["mapping_status"] in {"mapped", "unmapped"}
    assert len(outputs["unmapped"]) == 1
    assert outputs["recipes"][0]["quality_level"] == "D"

    with paths["unmapped"].open("r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    assert rows[0]["raw_ingredient_text"] == "1 mystery leaf"

    saved_summary = json.loads(paths["summary"].read_text(encoding="utf-8"))
    assert saved_summary["mapping_coverage_pct"] == 50.0

    complete_outputs, complete_summary = build_audit(
        recipes,
        complete_classification=True,
    )
    assert complete_summary["classification_coverage_pct"] == 100.0
    assert complete_summary["provisional_occurrence_count"] == 1
    assert all(row["classified_ingredient_id"] for row in complete_outputs["raw"])


def test_recipe_link_sync_classifies_every_occurrence_idempotently(tmp_path, monkeypatch):
    db_path = tmp_path / "canonical_links.db"
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))
    database.init_db()

    conn = sqlite3.connect(db_path)
    try:
        conn.execute(
            """
            INSERT INTO recipes (
                id, title, meal_type, calories, protein, carbs, fats, fiber,
                tags, minutes, ingredients_json, steps_json, active, source,
                created_at, updated_at, deleted_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                "r1", "Canonical Link Test", "Dinner", 400, 20, 40, 10, 5,
                "[]", 20, "[]", "[]", 1, "test", 1, 1, 0,
            ),
        )
        conn.commit()
    finally:
        conn.close()

    recipes = [
        {
            "id": "r1",
            "ingredients": [
                {"name": "2 cloves bawang", "quantity": ""},
                {"name": "1 mystery leaf", "quantity": ""},
            ],
        }
    ]
    first = database.sync_recipe_ingredient_links(recipes)
    second = database.sync_recipe_ingredient_links(recipes)

    assert first["classification_coverage_pct"] == 100.0
    assert first["mapped"] == 1
    assert first["provisional"] == 1
    assert second["links"] == 2

    loaded = database.get_all_recipes()
    assert len(loaded) == 1
    canonical = loaded[0]["_canonical_stage1"]
    assert canonical["curatedIngredientIds"] == ["ing_garlic"]
    assert canonical["provisionalCount"] == 1

    conn = sqlite3.connect(db_path)
    try:
        links = conn.execute(
            """
            SELECT mapping_status, mapping_method, mapping_confidence, ingredient_id,
                   normalized_grams
            FROM recipe_ingredient_links
            ORDER BY ingredient_index
            """
        ).fetchall()
        assert len(links) == 2
        assert links[0][:3] == ("mapped", "curated_alias", "high")
        assert links[0][4] == 6.0
        assert links[1][0] == "provisional"
        assert links[1][3].startswith("ing_provisional_mystery_leaf_")
    finally:
        conn.close()


def test_canonical_price_override_precedence(tmp_path, monkeypatch):
    db_path = tmp_path / "canonical_prices.db"
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))
    database.init_db()

    market = database.resolve_canonical_price_ref("ing_garlic")
    assert market is not None
    assert market["scope"] == "wet_market"

    database.upsert_canonical_price_override(
        ingredient_id="ing_garlic",
        price_php=175,
        unit="kg",
        scope="admin_override",
        notes="Admin NCR override",
    )
    admin = database.resolve_canonical_price_ref("ing_garlic")
    assert admin is not None
    assert admin["scope"] == "admin_override"
    assert admin["pricePhp"] == 175

    database.upsert_canonical_price_override(
        ingredient_id="ing_garlic",
        price_php=160,
        unit="kg",
        scope="user_override",
        owner_uid="user-1",
        notes="User local store price",
    )
    user = database.resolve_canonical_price_ref("ing_garlic", owner_uid="user-1")
    other = database.resolve_canonical_price_ref("ing_garlic", owner_uid="user-2")

    assert user is not None and user["scope"] == "user_override"
    assert user["pricePhp"] == 160
    assert other is not None and other["scope"] == "admin_override"

    context = price_catalog.create_pricing_context(owner_uid="user-1", market_type="wet_market")
    estimate = price_catalog.estimate_price_explained(
        "2 cloves bawang",
        pricing_context=context,
    )
    second = price_catalog.estimate_price_explained(
        "1 kg garlic",
        pricing_context=context,
    )

    assert estimate.source == "canonical_user_override"
    assert second.source == "canonical_user_override"
    assert context.canonical_price_ref_db_calls == 1


def test_canonical_water_baseline_is_zero_cost_tap_water(tmp_path, monkeypatch):
    db_path = tmp_path / "canonical_water.db"
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))
    database.init_db()

    baseline = database.resolve_canonical_price_ref("ing_water")

    assert baseline is not None
    assert baseline["scope"] == "baseline"
    assert baseline["unit"] == "l"
    assert baseline["pricePhp"] == 0
    assert baseline["confidence"] == "high"


def test_latest_da_ncr_market_prices_override_static_baselines(tmp_path, monkeypatch):
    db_path = tmp_path / "canonical_da_market.db"
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))
    database.init_db()

    tomato = database.resolve_canonical_price_ref("ing_tomato")
    papaya = database.resolve_canonical_price_ref("ing_papaya")
    context = price_catalog.create_pricing_context(month_index=9)
    estimate = price_catalog.estimate_price_explained("tomato", "1 kg", pricing_context=context)

    assert tomato is not None
    assert tomato["scope"] == "wet_market"
    assert tomato["pricePhp"] == 108.50
    assert tomato["sourceDate"] == "2026-09-06"
    assert papaya is not None and papaya["pricePhp"] == 78.49
    assert estimate.source == "canonical_wet_market"
    assert estimate.price_php == 109
    assert estimate.market_multiplier == 1.0
    assert estimate.tingi_multiplier == 1.0


def test_bangus_fillet_retail_migration_seeds_product_specific_reference(tmp_path, monkeypatch):
    db_path = tmp_path / "canonical_bangus_fillet.db"
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))
    database.init_db()

    fillet = database.resolve_canonical_price_ref("ing_bangus_fillet")
    whole = database.resolve_canonical_price_ref("ing_bangus")

    assert fillet is not None
    assert fillet["pricePhp"] == 388
    assert fillet["unit"] == "kg"
    assert fillet["marketType"] == "supermarket"
    assert fillet["source"] == "metro_retail_fresh_boneless_listing"
    assert fillet["sourceDate"] == "2026-09-08"
    assert fillet["confidence"] == "medium"
    assert whole is not None and whole["pricePhp"] == 243.62


def test_water_price_migration_repairs_existing_paid_baseline(tmp_path, monkeypatch):
    db_path = tmp_path / "canonical_water_upgrade.db"
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))
    database.init_db()

    conn = sqlite3.connect(db_path)
    try:
        conn.execute(
            """
            UPDATE ingredient_price_refs
            SET unit = 'piece',
                price_php = 20,
                source = 'static_catalog',
                confidence = 'medium',
                market_type = 'baseline',
                notes = NULL
            WHERE ingredient_id = 'ing_water'
              AND price_ref_id = 'price_ing_water_static_v1'
            """
        )
        conn.execute(
            """
            DELETE FROM schema_migrations
            WHERE scope = 'app'
              AND migration_id = '20260614_app_020_canonical_water_baseline_zero_price'
            """
        )
        conn.commit()
    finally:
        conn.close()

    database.init_db()
    baseline = database.resolve_canonical_price_ref("ing_water")

    assert baseline is not None
    assert baseline["unit"] == "l"
    assert baseline["pricePhp"] == 0
    assert baseline["source"] == "household_tap_water_baseline"
    assert baseline["confidence"] == "high"
