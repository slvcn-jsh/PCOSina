import json
import sys
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import database
import main


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_admin_recipe_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"admin_recipe_api_{uuid4().hex}.db"


def test_seed_recipes_imports_missing_rows_without_overwriting_existing(tmp_path):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    seed_path = tmp_path / "recipes.json"
    seed_path.write_text(
        json.dumps(
            [
                {
                    "id": "seed-recipe-1",
                    "name": "Seed Tinola",
                    "mealType": "Dinner",
                    "nutrition": {"calories": 410, "protein_g": 31, "carbs_g": 14, "fat_g": 11, "fiber_g": 5},
                    "tags": ["filipino"],
                    "minutes": 35,
                    "ingredients": [{"name": "Chicken", "quantity": "250g"}],
                    "instructions": ["Simmer", "Serve"],
                },
                {
                    "id": "seed-recipe-2",
                    "name": "Seed Mongo",
                    "mealType": "Lunch",
                    "nutrition": {"calories": 380, "protein_g": 22, "carbs_g": 42, "fat_g": 8, "fiber_g": 10},
                    "tags": ["high_fiber"],
                    "minutes": 30,
                    "ingredients": [{"name": "Mongo", "quantity": "1 cup"}],
                    "instructions": ["Boil", "Season"],
                },
            ]
        ),
        encoding="utf-8",
    )

    database.upsert_recipe(
        {
            "id": "seed-recipe-1",
            "title": "Admin Edited Tinola",
            "mealType": "Dinner",
            "calories": 420,
            "proteinGrams": 30,
            "carbsGrams": 18,
            "fatsGrams": 14,
            "fiberGrams": 5,
            "tags": ["admin"],
            "minutes": 28,
            "ingredients": [{"name": "Chicken", "quantity": "220g"}],
            "steps": ["Admin edit"],
        }
    )

    summary = database.seed_recipes(source_path=str(seed_path), force_reseed=False)

    assert summary["insertedCount"] == 1
    assert summary["updatedCount"] == 0
    assert summary["skippedExistingCount"] == 1
    assert database.get_recipe_by_id("seed-recipe-1")["title"] == "Admin Edited Tinola"
    assert database.get_recipe_by_id("seed-recipe-2")["title"] == "Seed Mongo"

    forced = database.seed_recipes(source_path=str(seed_path), force_reseed=True)

    assert forced["updatedCount"] == 2
    assert database.get_recipe_by_id("seed-recipe-1")["title"] == "Seed Tinola"


def test_recipe_delete_is_soft_delete_and_seed_does_not_reactivate_without_force(tmp_path):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    seed_path = tmp_path / "recipes.json"
    seed_path.write_text(
        json.dumps(
            [
                {
                    "id": "seed-delete-1",
                    "name": "Seed Delete Tinola",
                    "mealType": "Dinner",
                    "nutrition": {"calories": 410, "protein_g": 31, "carbs_g": 14, "fat_g": 11, "fiber_g": 5},
                    "tags": ["filipino"],
                    "minutes": 35,
                    "ingredients": [{"name": "Chicken", "quantity": "250g"}],
                    "instructions": ["Simmer", "Serve"],
                }
            ]
        ),
        encoding="utf-8",
    )

    database.seed_recipes(source_path=str(seed_path), force_reseed=False)
    assert database.get_recipe_by_id("seed-delete-1") is not None

    assert database.delete_recipe("seed-delete-1") == 1
    assert database.get_recipe_by_id("seed-delete-1") is None
    assert database.get_all_recipes() == []

    conn = database._connect()
    try:
        row = conn.execute("SELECT active, deleted_at FROM recipes WHERE id = ?", ("seed-delete-1",)).fetchone()
    finally:
        conn.close()
    assert row is not None
    assert row[0] == 0
    assert row[1] > 0

    skipped = database.seed_recipes(source_path=str(seed_path), force_reseed=False)
    assert skipped["skippedExistingCount"] == 1
    assert database.get_recipe_by_id("seed-delete-1") is None

    database.seed_recipes(source_path=str(seed_path), force_reseed=True)
    assert database.get_recipe_by_id("seed-delete-1") is not None


def test_recipe_catalog_endpoint_returns_active_recipe_details_only():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    database.upsert_recipe(
        {
            "id": "catalog-active-1",
            "title": "Catalog Active Tinola",
            "mealType": "Dinner",
            "calories": 420,
            "proteinGrams": 30,
            "carbsGrams": 18,
            "fatsGrams": 14,
            "fiberGrams": 5,
            "tags": ["filipino"],
            "minutes": 35,
            "ingredients": [{"name": "Chicken", "quantity": "250g"}],
            "steps": ["Simmer"],
        }
    )
    database.upsert_recipe(
        {
            "id": "catalog-inactive-1",
            "title": "Catalog Hidden Tinola",
            "mealType": "Dinner",
            "calories": 420,
            "proteinGrams": 30,
            "carbsGrams": 18,
            "fatsGrams": 14,
            "fiberGrams": 5,
            "tags": ["filipino"],
            "minutes": 35,
            "ingredients": [{"name": "Chicken", "quantity": "250g"}],
            "steps": ["Simmer"],
        }
    )
    database.delete_recipe("catalog-inactive-1")

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "catalog-user"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}

    try:
        with TestClient(main.app) as client:
            response = client.get("/recipes/catalog", params={"limit": 2000})

        assert response.status_code == 200
        ids = {item["id"] for item in response.json()}
        assert "catalog-active-1" in ids
        assert "catalog-inactive-1" not in ids
    finally:
        main.app.dependency_overrides = {}


def test_admin_recipe_rejects_invalid_bounds_and_oversized_content():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    content_admin = {
        "uid": "content-admin-1",
        "actor": "content-admin@example.com",
        "roles": ["content_admin"],
    }
    main.app.dependency_overrides[main.require_content_admin] = lambda: content_admin

    payload = {
        "id": "invalid/route-id",
        "title": "x" * 200,
        "mealType": "Dinner",
        "calories": -1,
        "proteinGrams": 30,
        "carbsGrams": 18,
        "fatsGrams": 14,
        "fiberGrams": 5,
        "tags": ["filipino"],
        "minutes": 0,
        "ingredients": [],
        "steps": ["Simmer chicken"],
    }

    try:
        with TestClient(main.app) as client:
            response = client.post("/admin/recipes", json=payload)

        assert response.status_code == 422
    finally:
        main.app.dependency_overrides = {}


def test_content_admin_recipe_crud_and_audit_log():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    content_admin = {
        "uid": "content-admin-1",
        "actor": "content-admin@example.com",
        "roles": ["content_admin"],
    }
    ops_admin = {
        "uid": "ops-admin-1",
        "actor": "ops-admin@example.com",
        "roles": ["ops_admin"],
    }
    main.app.dependency_overrides[main.require_content_admin] = lambda: content_admin
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin

    payload = {
        "title": "Admin Tinola",
        "mealType": "Dinner",
        "calories": 420,
        "proteinGrams": 30,
        "carbsGrams": 18,
        "fatsGrams": 14,
        "fiberGrams": 5,
        "tags": ["high_protein", "filipino"],
        "minutes": 35,
        "ingredients": [{"name": "Chicken", "quantity": "250g"}],
        "steps": ["Simmer chicken", "Serve warm"],
    }

    try:
        with TestClient(main.app) as client:
            create_resp = client.post("/admin/recipes", json=payload)
            assert create_resp.status_code == 200
            created = create_resp.json()
            recipe_id = created["id"]
            assert created["title"] == "Admin Tinola"

            list_resp = client.get("/admin/recipes", params={"q": "Tinola"})
            assert list_resp.status_code == 200
            assert any(item["id"] == recipe_id for item in list_resp.json()["items"])

            get_resp = client.get(f"/admin/recipes/{recipe_id}")
            assert get_resp.status_code == 200
            assert get_resp.json()["mealType"] == "Dinner"

            update_payload = dict(payload)
            update_payload["title"] = "Admin Tinola Updated"
            update_payload["minutes"] = 28
            update_resp = client.put(f"/admin/recipes/{recipe_id}", json=update_payload)
            assert update_resp.status_code == 200
            assert update_resp.json()["title"] == "Admin Tinola Updated"
            assert update_resp.json()["minutes"] == 28

            audit_resp = client.get("/admin/audit/logs", params={"resource_type": "recipe"})
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "recipe.create" in actions
            assert "recipe.update" in actions

            delete_resp = client.delete(f"/admin/recipes/{recipe_id}")
            assert delete_resp.status_code == 200

            missing_resp = client.get(f"/admin/recipes/{recipe_id}")
            assert missing_resp.status_code == 404
    finally:
        main.app.dependency_overrides = {}


def test_build_admin_principal_supports_uid_allowlists(monkeypatch):
    monkeypatch.setenv("PCOSINA_CONTENT_ADMIN_UIDS", "uid-content-1")
    principal = main._build_admin_principal(
        {"uid": "uid-content-1", "email": "operator@example.com"},
        auth_type="bearer",
    )
    assert "content_admin" in principal["roles"]


def test_build_admin_principal_rejects_unverified_email_allowlist(monkeypatch):
    monkeypatch.setenv("PCOSINA_CONTENT_ADMIN_EMAILS", "operator@example.com")
    monkeypatch.setenv("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL", "true")
    try:
        main._build_admin_principal(
            {
                "uid": "uid-content-2",
                "email": "operator@example.com",
                "email_verified": False,
            },
            auth_type="bearer",
        )
        assert False, "expected verified-email enforcement to reject unverified operator email"
    except Exception as exc:
        assert getattr(exc, "status_code", None) == 403


def test_build_admin_principal_accepts_verified_email_allowlist(monkeypatch):
    monkeypatch.setenv("PCOSINA_CONTENT_ADMIN_EMAILS", "operator@example.com")
    monkeypatch.setenv("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL", "true")
    principal = main._build_admin_principal(
        {
            "uid": "uid-content-3",
            "email": "operator@example.com",
            "email_verified": True,
        },
        auth_type="bearer",
    )
    assert "content_admin" in principal["roles"]
    assert principal["emailVerified"] is True
