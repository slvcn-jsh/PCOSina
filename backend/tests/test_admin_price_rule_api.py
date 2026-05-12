import sys
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import database
import main
import price_catalog


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_admin_price_rule_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"admin_price_rule_api_{uuid4().hex}.db"


def test_content_admin_price_rule_crud_updates_catalog_and_audit_log():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    price_catalog.invalidate_override_cache()

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
        "keywords": ["dragonfruit", "pitaya"],
        "pricePhp": 999,
        "priceMinPhp": 850,
        "priceMaxPhp": 1150,
        "category": "Produce",
        "unit": "kg",
        "active": True,
        "notes": "market override",
    }

    try:
        baseline = price_catalog.estimate_price("dragonfruit")

        with TestClient(main.app) as client:
            create_resp = client.post("/admin/price-rules", json=payload)
            assert create_resp.status_code == 200
            created = create_resp.json()
            rule_id = created["id"]
            assert created["keywords"] == ["dragonfruit", "pitaya"]
            assert created["priceMinPhp"] == 850
            assert created["priceMaxPhp"] == 1150

            raised = price_catalog.estimate_price("dragonfruit")
            assert raised > baseline
            assert raised >= 500

            list_resp = client.get("/admin/price-rules", params={"q": "dragonfruit"})
            assert list_resp.status_code == 200
            assert any(item["id"] == rule_id for item in list_resp.json()["items"])

            get_resp = client.get(f"/admin/price-rules/{rule_id}")
            assert get_resp.status_code == 200
            assert get_resp.json()["category"] == "Produce"

            update_payload = dict(payload)
            update_payload["pricePhp"] = 1200
            update_payload["priceMinPhp"] = 1100
            update_payload["priceMaxPhp"] = 1400
            update_resp = client.put(f"/admin/price-rules/{rule_id}", json=update_payload)
            assert update_resp.status_code == 200
            assert update_resp.json()["pricePhp"] == 1200
            assert update_resp.json()["priceMinPhp"] == 1100
            assert update_resp.json()["priceMaxPhp"] == 1400

            raised_again = price_catalog.estimate_price("dragonfruit")
            assert raised_again > raised

            audit_resp = client.get("/admin/audit/logs", params={"resource_type": "ingredient_price_rule"})
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "price_rule.create" in actions
            assert "price_rule.update" in actions

            delete_resp = client.delete(f"/admin/price-rules/{rule_id}")
            assert delete_resp.status_code == 200

            reverted = price_catalog.estimate_price("dragonfruit")
            assert reverted < raised_again

            missing_resp = client.get(f"/admin/price-rules/{rule_id}")
            assert missing_resp.status_code == 404
    finally:
        price_catalog.invalidate_override_cache()
        main.app.dependency_overrides = {}


def test_ops_admin_principal_can_use_mobile_content_maintenance_endpoints():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    price_catalog.invalidate_override_cache()

    ops_admin = {
        "uid": "ops-content-admin-1",
        "actor": "ops-content-admin@example.com",
        "roles": ["ops_admin"],
    }
    main.app.dependency_overrides[main.require_admin_config_token] = lambda: ops_admin

    recipe_payload = {
        "id": "ops-mobile-recipe-1",
        "title": "Ops Mobile Tinola",
        "mealType": "Dinner",
        "calories": 420,
        "proteinGrams": 30,
        "carbsGrams": 18,
        "fatsGrams": 14,
        "fiberGrams": 5,
        "tags": ["filipino", "pcos-friendly"],
        "minutes": 35,
        "ingredients": [{"name": "Chicken", "quantity": "250g"}],
        "steps": ["Simmer", "Serve"],
    }
    price_payload = {
        "id": "ops-mobile-price-1",
        "keywords": ["tinola chicken"],
        "pricePhp": 180,
        "priceMinPhp": 160,
        "priceMaxPhp": 220,
        "category": "Protein",
        "unit": "kg",
        "active": True,
        "notes": "ops mobile maintenance test",
    }

    try:
        with TestClient(main.app) as client:
            recipe_resp = client.post("/admin/recipes", json=recipe_payload)
            assert recipe_resp.status_code == 200
            assert recipe_resp.json()["id"] == "ops-mobile-recipe-1"

            price_resp = client.post("/admin/price-rules", json=price_payload)
            assert price_resp.status_code == 200
            assert price_resp.json()["priceMinPhp"] == 160

            list_resp = client.get("/admin/recipes", params={"q": "Tinola"})
            assert list_resp.status_code == 200
            assert any(item["id"] == "ops-mobile-recipe-1" for item in list_resp.json()["items"])
    finally:
        price_catalog.invalidate_override_cache()
        main.app.dependency_overrides = {}
