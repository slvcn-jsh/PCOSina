import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main
from domain.models import DayPlan, PlannedMeal


def _payload() -> dict:
    return {
        "profile": {
            "displayName": "ContractTest",
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


def test_success_response_contains_production_contract_fields(monkeypatch):
    def fake_solve(_request, _recipes, weight_set=None, policy=None):
        day = DayPlan(
            dayLabel="Mon",
            meals=[
                PlannedMeal(mealLabel="Breakfast", recipeId="r1", title="Meal A"),
                PlannedMeal(mealLabel="Lunch", recipeId="r2", title="Meal B"),
                PlannedMeal(mealLabel="Dinner", recipeId="r3", title="Meal C"),
            ],
            totalCalories=1400,
        )
        return [day], "Success", {"fallbackUsed": False, "authority": "cp-sat"}

    monkeypatch.setattr(main, "solve_meal_plan", fake_solve)
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "test"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    with TestClient(main.app) as client:
        response = client.post("/generate-plan", json=_payload())

    main.app.dependency_overrides = {}

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "success"
    assert isinstance(body.get("requestId"), str)
    assert isinstance(body.get("planId"), str)
    assert body.get("policyVersion") == "policy-v1:test"
    assert body.get("solverMetadata", {}).get("authoritative") is True
    assert "runtimeMs" in body.get("solverMetadata", {})
    assert body.get("timestamps", {}).get("completedAtMs") >= body.get("timestamps", {}).get("requestedAtMs")
