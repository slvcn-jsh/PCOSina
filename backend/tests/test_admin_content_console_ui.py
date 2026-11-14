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
    base = Path(__file__).resolve().parent / ".tmp_admin_content_console_ui"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"admin_content_console_ui_{uuid4().hex}.db"


def _content_admin_principal() -> dict[str, object]:
    return {
        "uid": "content-admin-ui-1",
        "actor": "content-admin-ui@example.com",
        "roles": ["content_admin"],
        "nonce": "content-console-nonce",
        "authType": "session",
    }


def test_admin_login_page_links_to_content_console(monkeypatch):
    principal = _content_admin_principal()
    monkeypatch.setattr(main, "_principal_from_session_token", lambda token: principal)

    with TestClient(main.app) as client:
        response = client.get("/admin/login")

    assert response.status_code == 200
    assert "/admin/content" in response.text
    assert "Manage Meals" in response.text


def test_content_console_shows_switch_console_links_for_multi_role_admin():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    principal = {
        **_content_admin_principal(),
        "roles": ["content_admin", "ops_admin", "feedback_admin"],
    }
    main.app.dependency_overrides[main.require_content_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            response = client.get("/admin/content")

        assert response.status_code == 200
        assert "Tools" in response.text
        assert "/admin/content" in response.text
        assert "/admin/ops" in response.text
        assert "/admin/feedback" in response.text
    finally:
        main.app.dependency_overrides = {}


def test_content_admin_recipe_console_html_crud():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    principal = _content_admin_principal()
    main.app.dependency_overrides[main.require_content_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            page = client.get("/admin/content/recipes")
            assert page.status_code == 200
            assert "Recipe Operations" in page.text
            assert "Catalog Status" in page.text
            assert "Import missing seed recipes" in page.text
            assert "csrf_token" in page.text

            save_token = main._build_admin_csrf_token(principal, "content-recipe-save")
            create = client.post(
                "/admin/content/recipes/save",
                data={
                    "csrf_token": save_token,
                    "title": "Backoffice Tinola",
                    "meal_type": "Dinner",
                    "calories": "430",
                    "protein_grams": "31",
                    "carbs_grams": "14",
                    "fats_grams": "16",
                    "fiber_grams": "4",
                    "minutes": "35",
                    "tags": "filipino, high_protein",
                    "ingredients_text": "Chicken | 250g\nPapaya | 1 cup",
                    "steps_text": "Simmer broth\nServe warm",
                },
                follow_redirects=False,
            )
            assert create.status_code == 303
            assert "status=recipe_saved" in create.headers["location"]

            items = database.list_admin_recipes(q="Backoffice Tinola")
            assert len(items) == 1
            recipe_id = items[0]["id"]

            update = client.post(
                "/admin/content/recipes/save",
                data={
                    "csrf_token": save_token,
                    "recipe_id": recipe_id,
                    "title": "Backoffice Tinola Updated",
                    "meal_type": "Dinner",
                    "calories": "415",
                    "protein_grams": "33",
                    "carbs_grams": "12",
                    "fats_grams": "11",
                    "fiber_grams": "5",
                    "minutes": "28",
                    "tags": "filipino, soup",
                    "ingredients_text": "Chicken | 220g\nMalunggay | 1 bunch",
                    "steps_text": "Simmer\nFinish",
                },
                follow_redirects=False,
            )
            assert update.status_code == 303

            saved = database.get_recipe_by_id(recipe_id)
            assert saved is not None
            assert saved["title"] == "Backoffice Tinola Updated"
            assert saved["minutes"] == 28
            assert saved["ingredients"][1]["name"] == "Malunggay"

            delete_token = main._build_admin_csrf_token(principal, "content-recipe-delete")
            delete = client.post(
                f"/admin/content/recipes/{recipe_id}/delete",
                data={"csrf_token": delete_token},
                follow_redirects=False,
            )
            assert delete.status_code == 303
            assert database.get_recipe_by_id(recipe_id) is None
    finally:
        main.app.dependency_overrides = {}


def test_content_admin_recipe_console_seed_import(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    principal = _content_admin_principal()
    main.app.dependency_overrides[main.require_content_admin] = lambda: principal
    calls = []

    def fake_seed_recipes(*, force_reseed=False):
        calls.append(force_reseed)
        return {
            "sourcePath": "backend/recipes.json",
            "sourceCount": 1114,
            "beforeCount": 0,
            "afterCount": 1114,
            "insertedCount": 1114,
            "updatedCount": 0,
            "skippedExistingCount": 0,
            "forceReseed": force_reseed,
        }

    monkeypatch.setattr(main.database, "seed_recipes", fake_seed_recipes)

    try:
        with TestClient(main.app) as client:
            calls.clear()
            token = main._build_admin_csrf_token(principal, "content-recipe-seed")
            response = client.post(
                "/admin/content/recipes/seed",
                data={"csrf_token": token, "q": "tinola", "meal_type": "Dinner", "limit": "50"},
                follow_redirects=False,
            )

        assert response.status_code == 303
        assert "status=recipe_seeded" in response.headers["location"]
        assert calls == [False]
    finally:
        main.app.dependency_overrides = {}


def test_content_admin_price_rule_console_html_crud():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    price_catalog.invalidate_override_cache()

    principal = _content_admin_principal()
    main.app.dependency_overrides[main.require_content_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            page = client.get("/admin/content/price-rules")
            assert page.status_code == 200
            assert "Ingredient Price Rules" in page.text
            assert "Prices are estimates and may vary by store, location, and date." in page.text
            assert "csrf_token" in page.text

            save_token = main._build_admin_csrf_token(principal, "content-price-rule-save")
            create = client.post(
                "/admin/content/price-rules/save",
                data={
                    "csrf_token": save_token,
                    "keywords": "dragonfruit, pitaya",
                    "price_php": "980",
                    "price_min_php": "900",
                    "price_max_php": "1100",
                    "category": "Produce",
                    "unit": "kg",
                    "active": "true",
                    "notes": "Back-office override",
                },
                follow_redirects=False,
            )
            assert create.status_code == 303
            assert "status=price_rule_saved" in create.headers["location"]

            rules = database.list_admin_price_rules(q="dragonfruit")
            assert len(rules) == 1
            rule_id = rules[0]["id"]
            assert rules[0]["priceMinPhp"] == 900
            assert rules[0]["priceMaxPhp"] == 1100

            update = client.post(
                "/admin/content/price-rules/save",
                data={
                    "csrf_token": save_token,
                    "rule_id": rule_id,
                    "keywords": "dragonfruit, pitaya",
                    "price_php": "1200",
                    "price_min_php": "1000",
                    "price_max_php": "1300",
                    "category": "Produce",
                    "unit": "kg",
                    "active": "true",
                    "notes": "Updated override",
                },
                follow_redirects=False,
            )
            assert update.status_code == 303
            saved = database.get_price_rule_by_id(rule_id)
            assert saved is not None
            assert saved["pricePhp"] == 1200
            assert saved["priceMinPhp"] == 1000
            assert saved["priceMaxPhp"] == 1300

            delete_token = main._build_admin_csrf_token(principal, "content-price-rule-delete")
            delete = client.post(
                f"/admin/content/price-rules/{rule_id}/delete",
                data={"csrf_token": delete_token},
                follow_redirects=False,
            )
            assert delete.status_code == 303
            assert database.get_price_rule_by_id(rule_id) is None
    finally:
        price_catalog.invalidate_override_cache()
        main.app.dependency_overrides = {}


def test_content_admin_nutrition_console_html_crud():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    recipe = database.upsert_recipe(
        {
            "id": "recipe-admin-ui-1",
            "title": "Console Tinola",
            "mealType": "Dinner",
            "calories": 420,
            "proteinGrams": 30,
            "carbsGrams": 18,
            "fatsGrams": 14,
            "fiberGrams": 5,
            "tags": ["filipino"],
            "minutes": 35,
            "ingredients": [{"name": "Chicken", "quantity": "250g"}],
            "steps": ["Simmer", "Serve"],
        }
    )

    principal = _content_admin_principal()
    main.app.dependency_overrides[main.require_content_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            page = client.get("/admin/content/nutrition-corrections")
            assert page.status_code == 200
            assert "Nutrition Corrections" in page.text
            assert "csrf_token" in page.text

            save_token = main._build_admin_csrf_token(principal, "content-nutrition-save")
            create = client.post(
                "/admin/content/nutrition-corrections/save",
                data={
                    "csrf_token": save_token,
                    "recipe_id": recipe["id"],
                    "calories": "390",
                    "protein_grams": "34",
                    "carbs_grams": "11",
                    "fats_grams": "10",
                    "fiber_grams": "6",
                    "sodium_mg": "420",
                    "sugar_grams": "4",
                    "active": "true",
                    "notes": "Dietitian-reviewed",
                },
                follow_redirects=False,
            )
            assert create.status_code == 303
            assert "status=nutrition_correction_saved" in create.headers["location"]

            correction = database.get_nutrition_correction_by_recipe_id(recipe["id"])
            assert correction is not None
            assert correction["calories"] == 390

            delete_token = main._build_admin_csrf_token(principal, "content-nutrition-delete")
            delete = client.post(
                f"/admin/content/nutrition-corrections/{recipe['id']}/delete",
                data={"csrf_token": delete_token},
                follow_redirects=False,
            )
            assert delete.status_code == 303
            assert database.get_nutrition_correction_by_recipe_id(recipe["id"]) is None
    finally:
        main.app.dependency_overrides = {}


def test_content_console_escapes_stored_html():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    database.upsert_recipe(
        {
            "id": "recipe-html-1",
            "title": "<script>alert('x')</script>",
            "mealType": "Dinner",
            "calories": 400,
            "proteinGrams": 25,
            "carbsGrams": 20,
            "fatsGrams": 12,
            "fiberGrams": 5,
            "tags": ["<b>tag</b>"],
            "minutes": 25,
            "ingredients": [{"name": "Chicken", "quantity": "200g"}],
            "steps": ["Cook"],
        }
    )
    database.upsert_price_rule(
        {
            "id": "rule-html-1",
            "keywords": ["okra"],
            "pricePhp": 120,
            "category": "Produce",
            "unit": "kg",
            "active": True,
            "notes": "<img src=x onerror=alert(1)>",
        }
    )

    principal = _content_admin_principal()
    main.app.dependency_overrides[main.require_content_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            recipes_page = client.get("/admin/content/recipes")
            assert recipes_page.status_code == 200
            assert "<script>alert('x')</script>" not in recipes_page.text
            assert "&lt;script&gt;alert" in recipes_page.text

            rules_page = client.get("/admin/content/price-rules")
            assert rules_page.status_code == 200
            assert "<img src=x onerror=alert(1)>" not in rules_page.text
            assert "&lt;img src=x onerror=alert(1)&gt;" in rules_page.text
    finally:
        main.app.dependency_overrides = {}
