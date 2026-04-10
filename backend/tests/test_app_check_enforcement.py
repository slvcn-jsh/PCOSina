import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_require_app_check_rejects_missing_token_when_enforced(monkeypatch):
    monkeypatch.setenv("PCOSINA_ENFORCE_APP_CHECK", "true")

    try:
        main.require_app_check(None)
        assert False, "Expected HTTPException"
    except Exception as exc:
        assert getattr(exc, "status_code", None) == 401


def test_require_app_check_verifies_token(monkeypatch):
    monkeypatch.setenv("PCOSINA_ENFORCE_APP_CHECK", "true")
    monkeypatch.setattr(main.firebase_admin, "_apps", [object()])

    captured = {}

    def fake_verify(token: str):
        captured["token"] = token
        return {"app_id": "pcosina-test"}

    monkeypatch.setattr(main.app_check, "verify_token", fake_verify)

    claims = main.require_app_check("app-check-token")
    assert claims["app_id"] == "pcosina-test"
    assert captured["token"] == "app-check-token"


def test_generate_plan_requires_app_check_header(monkeypatch):
    monkeypatch.setenv("PCOSINA_ENFORCE_APP_CHECK", "true")
    monkeypatch.setattr(main.firebase_admin, "_apps", [object()])
    monkeypatch.setattr(main.app_check, "verify_token", lambda token: {"app_id": "pcosina-test"})
    monkeypatch.setattr(main, "solve_meal_plan", lambda _request, _recipes, weight_set=None, policy=None: (None, "Infeasible", None))
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "test"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "profile": {
            "displayName": "Test",
            "age": 25,
            "heightCm": 160,
            "weightKg": 60,
            "activityLevel": "Lightly Active",
            "goal": "General Health",
            "dietaryRestrictions": [],
            "allergies": [],
            "pantryItems": []
        },
        "days": 7,
        "mealsPerDay": 3
    }

    try:
        with TestClient(main.app) as client:
            missing = client.post("/generate-plan", json=payload)
            allowed = client.post("/generate-plan", json=payload, headers={"X-Firebase-AppCheck": "app-check-token"})
        assert missing.status_code == 401
        assert allowed.status_code == 200
    finally:
        main.app.dependency_overrides = {}
