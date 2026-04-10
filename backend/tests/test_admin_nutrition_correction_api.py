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
    base = Path(__file__).resolve().parent / ".tmp_admin_nutrition_correction_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"admin_nutrition_correction_api_{uuid4().hex}.db"


def test_content_admin_nutrition_correction_overrides_recipe_reads_and_audit_log():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    recipe = database.upsert_recipe(
        {
            "id": "recipe-correctable-1",
            "title": "Correctable Tinola",
            "mealType": "Dinner",
            "calories": 420,
            "proteinGrams": 30,
            "carbsGrams": 18,
            "fatsGrams": 14,
            "fiberGrams": 5,
            "tags": ["high_protein"],
            "minutes": 35,
            "ingredients": [{"name": "Chicken", "quantity": "250g"}],
            "steps": ["Simmer chicken", "Serve warm"],
        }
    )
    assert recipe["calories"] == 420

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
        "calories": 390,
        "proteinGrams": 34,
        "carbsGrams": 12,
        "fatsGrams": 11,
        "fiberGrams": 6,
        "sodiumMg": 420,
        "sugarGrams": 4,
        "active": True,
        "notes": "Dietitian-reviewed correction",
    }

    try:
        with TestClient(main.app) as client:
            upsert_resp = client.put(f"/admin/nutrition-corrections/{recipe['id']}", json=payload)
            assert upsert_resp.status_code == 200
            correction = upsert_resp.json()
            assert correction["recipeId"] == recipe["id"]
            assert correction["calories"] == 390
            assert correction["sodiumMg"] == 420

            get_resp = client.get(f"/admin/nutrition-corrections/{recipe['id']}")
            assert get_resp.status_code == 200
            assert get_resp.json()["notes"] == "Dietitian-reviewed correction"

            list_resp = client.get("/admin/nutrition-corrections", params={"q": "Tinola"})
            assert list_resp.status_code == 200
            assert any(item["recipeId"] == recipe["id"] for item in list_resp.json()["items"])

            corrected_recipe = database.get_recipe_by_id(recipe["id"])
            assert corrected_recipe is not None
            assert corrected_recipe["calories"] == 390
            assert corrected_recipe["proteinGrams"] == 34
            assert corrected_recipe["sodiumMg"] == 420
            assert corrected_recipe["nutritionCorrectionId"] == correction["id"]

            corrected_pool = {item["id"]: item for item in database.get_all_recipes()}
            assert corrected_pool[recipe["id"]]["sugarGrams"] == 4

            public_recipe = client.get(f"/admin/recipes/{recipe['id']}")
            assert public_recipe.status_code == 200
            assert public_recipe.json()["calories"] == 390
            assert public_recipe.json()["nutritionCorrectionId"] == correction["id"]

            audit_resp = client.get("/admin/audit/logs", params={"resource_type": "recipe_nutrition_correction"})
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "nutrition_correction.upsert" in actions

            delete_resp = client.delete(f"/admin/nutrition-corrections/{recipe['id']}")
            assert delete_resp.status_code == 200

            reverted_recipe = database.get_recipe_by_id(recipe["id"])
            assert reverted_recipe is not None
            assert reverted_recipe["calories"] == 420
            assert reverted_recipe.get("nutritionCorrectionId") is None

            missing_resp = client.get(f"/admin/nutrition-corrections/{recipe['id']}")
            assert missing_resp.status_code == 404
    finally:
        main.app.dependency_overrides = {}
