import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_generate_plan_returns_structured_no_safe_plan(monkeypatch):
    def fake_solve(_request, _recipes, weight_set=None, policy=None):
        return None, "Infeasible", None

    monkeypatch.setattr(main, "solve_meal_plan", fake_solve)
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))
    monkeypatch.setattr(main, "_is_circuit_open", lambda policy: False)
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "test"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
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

    with TestClient(main.app) as client:
        response = client.post("/generate-plan", json=payload)

    main.app.dependency_overrides = {}

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "no-safe-plan"
    assert body["days"] == []
    assert "MODEL_INFEASIBLE" in body["machineReasonCodes"]
    assert body["policyVersion"] == "policy-v1:test"
    assert "timestamps" in body
    assert "diagnosticsSummary" in body
    assert "profileRuleEffects" in body["diagnosticsSummary"]


def test_generate_plan_accepts_supported_legacy_schema_header(monkeypatch):
    def fake_solve(_request, _recipes, weight_set=None, policy=None):
        return None, "Infeasible", None

    monkeypatch.setattr(main, "solve_meal_plan", fake_solve)
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))
    monkeypatch.setattr(main, "_is_circuit_open", lambda policy: False)
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "test"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}

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

    headers = {"X-PCOSINA-Schema-Version": "1.2.0"}
    try:
        with TestClient(main.app) as client:
            response = client.post("/generate-plan", json=payload, headers=headers)
    finally:
        main.app.dependency_overrides = {}

    assert response.status_code == 200
    assert response.headers["X-PCOSINA-Schema-Version"] == "1.2.0"


def test_generate_plan_idempotency_reuses_cached_response(monkeypatch):
    calls = {"count": 0}

    def fake_solve(_request, _recipes, weight_set=None, policy=None):
        calls["count"] += 1
        return None, "Infeasible", None

    main._idempotency_cache.clear()
    monkeypatch.setattr(main, "solve_meal_plan", fake_solve)
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))
    monkeypatch.setattr(main, "_is_circuit_open", lambda policy: False)
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "test"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
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

    headers = {"Idempotency-Key": "idem-1"}
    try:
        with TestClient(main.app) as client:
            first = client.post("/generate-plan", json=payload, headers=headers)
            second = client.post("/generate-plan", json=payload, headers=headers)
    finally:
        main.app.dependency_overrides = {}
        main._idempotency_cache.clear()

    assert first.status_code == 200
    assert second.status_code == 200
    assert calls["count"] == 1
    assert first.json() == second.json()


def test_generate_plan_cache_hit_refreshes_request_scoped_metadata(monkeypatch):
    calls = {"count": 0}
    planner_events = []
    ml_events = []

    def fake_solve_with_telemetry(_request, _recipes, _policy_payload):
        calls["count"] += 1
        return (
            [
                {
                    "dayLabel": "Mon",
                    "meals": [{"mealLabel": "Breakfast", "recipeId": "recipe-1", "title": "Tinola Breakfast"}],
                    "totalCalories": 420,
                }
            ],
            "Success",
            {"candidatePoolSize": 1},
            {},
        )

    clock = {"now": 1_700_000_000.0}

    def fake_time():
        clock["now"] += 0.25
        return clock["now"]

    main._plan_cache.clear()
    monkeypatch.setattr(main, "_solve_with_telemetry", fake_solve_with_telemetry)
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))
    monkeypatch.setattr(main, "_is_circuit_open", lambda policy: False)
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)
    monkeypatch.setattr(main, "_emit_planner_event", lambda event, payload, **kwargs: planner_events.append((event, payload)))
    monkeypatch.setattr(main, "_emit_ml_event", lambda event_name, payload, **kwargs: ml_events.append((event_name, payload, kwargs)))
    monkeypatch.setattr(main.time, "time", fake_time)

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "cache-user"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "profile": {
            "displayName": "Cache User",
            "age": 25,
            "heightCm": 160,
            "weightKg": 60,
            "activityLevel": "Lightly Active",
            "goal": "General Health",
            "dietaryRestrictions": [],
            "allergies": [],
            "pantryItems": [],
        },
        "days": 7,
        "mealsPerDay": 3,
    }

    try:
        with TestClient(main.app) as client:
            first = client.post("/generate-plan", json=payload)
            second = client.post("/generate-plan", json=payload)
    finally:
        main.app.dependency_overrides = {}
        main._plan_cache.clear()

    assert first.status_code == 200
    assert second.status_code == 200
    assert calls["count"] == 1
    assert first.json()["days"] == second.json()["days"]
    assert first.json()["requestId"] != second.json()["requestId"]
    assert first.json()["planId"] != second.json()["planId"]
    assert first.json()["timestamps"] != second.json()["timestamps"]
    generated_events = [item for item in ml_events if item[0] == "plan_generated"]
    assert len(generated_events) == 2
    assert generated_events[1][1]["cache_hit"] is True
    assert generated_events[1][2]["request_id"] == second.json()["requestId"]
    solver_events = [item for item in planner_events if item[0] == "solver_completed"]
    assert len(solver_events) == 2
    assert solver_events[1][1]["cacheHit"] is True
    assert solver_events[1][1]["requestId"] == second.json()["requestId"]


def test_no_safe_response_refresh_helper_updates_request_scoped_diagnostics_reference():
    payload = {
        "profile": {
            "displayName": "Cache User",
            "age": 25,
            "heightCm": 160,
            "weightKg": 60,
            "activityLevel": "Lightly Active",
            "goal": "General Health",
            "dietaryRestrictions": ["Budget Vegetarian", "Gluten-Free", "Dairy-Free"],
            "allergies": [],
            "pantryItems": [],
        },
        "days": 7,
        "mealsPerDay": 3,
    }
    request = main.GeneratePlanRequest.model_validate(payload)
    cached = main._build_no_safe_plan_response(
        request=request,
        request_id="old-request",
        message="Infeasible due to conflicting restrictions",
        policy_version="policy-v1:test",
        started_ms=1000,
        completed_ms=1500,
    )

    refreshed = main._freshen_cached_plan_response(
        cached,
        request_id="new-request",
        started_ms=2000,
        completed_ms=2500,
    )

    assert refreshed.status == "no-safe-plan"
    assert refreshed.requestId == "new-request"
    assert refreshed.diagnosticsReference == "new-request"
    assert refreshed.timestamps == {"requestedAtMs": 2000, "completedAtMs": 2500}


def test_no_safe_response_captures_exclusion_and_budget_diagnostics():
    request = main.GeneratePlanRequest.model_validate(
        {
            "profile": {
                "displayName": "Diagnostic User",
                "age": 25,
                "heightCm": 160,
                "weightKg": 60,
                "activityLevel": "Lightly Active",
                "goal": "General Health",
                "weeklyBudgetPhp": 700,
                "dietaryRestrictions": ["No Pork"],
                "allergies": ["fish"],
                "pantryItems": [],
            },
            "days": 7,
            "mealsPerDay": 3,
        }
    )

    response = main._build_no_safe_plan_response(
        request=request,
        request_id="diag-request",
        message="No safe recipes found.",
        policy_version="policy-v1:test",
        started_ms=1000,
        completed_ms=1500,
        telemetry={
            "stage1_diag": {
                "exclusion_summary": {"allergy": 5, "restriction": 2, "prep_time": 1},
                "exclusion_detail_counts": {"allergy:fish": 5, "restriction:no_pork": 2},
            },
            "budget_exceeded_stage": "stage1_shortlist",
            "solver_budget": {"totalTimeLimitSeconds": 14.0},
            "phase_timings_ms": {"stage1_shortlist": 4200, "stage1_price_estimation": 3900},
            "pricing_diagnostics": {
                "marketMultiplierDbCalls": 1,
                "marketMultiplierCacheHits": 20,
                "marketMultiplierCacheMisses": 3,
                "priceCostEstimationMs": 3900,
                "recipeCostEstimateCount": 12,
                "ingredientPriceEstimateCount": 48,
            },
        },
    )

    assert response.status == "no-safe-plan"
    assert response.diagnosticsSummary["candidateExclusionSummary"]["allergy"] == 5
    assert response.diagnosticsSummary["candidateExclusionDetailCounts"]["allergy:fish"] == 5
    assert response.diagnosticsSummary["budgetExceededStage"] == "stage1_shortlist"
    assert response.diagnosticsSummary["timeoutStage"] == "stage1_shortlist"
    assert response.diagnosticsSummary["candidateCountPre"] is None
    assert response.diagnosticsSummary["candidateCountPost"] is None
    assert response.diagnosticsSummary["pricingDiagnostics"]["marketMultiplierDbCalls"] == 1
    assert response.diagnosticsSummary["phaseTimingsMs"]["stage1_price_estimation"] == 3900
    assert response.machineReasonCodes == ["PLANNER_TIMEOUT"]
    assert response.diagnosticsSummary["profileRuleEffects"]["hardFilters"]
    assert response.humanGuidance[0].startswith("Planner timed out while pricing, filtering, or optimizing recipes")
    assert any("Allergy rules removed some candidate meals" in item for item in response.humanGuidance)


def test_generate_plan_no_safe_cache_hit_emits_completion_events_for_new_request(monkeypatch):
    planner_events = []
    ml_events = []

    cached = main._build_no_safe_plan_response(
        request=main.GeneratePlanRequest.model_validate(
            {
                "profile": {
                    "displayName": "Cache User",
                    "age": 25,
                    "heightCm": 160,
                    "weightKg": 60,
                    "activityLevel": "Lightly Active",
                    "goal": "General Health",
                    "dietaryRestrictions": [],
                    "allergies": [],
                    "pantryItems": [],
                },
                "days": 7,
                "mealsPerDay": 3,
            }
        ),
        request_id="cached-request",
        message="Infeasible",
        policy_version="policy-v1:test",
        started_ms=1000,
        completed_ms=1500,
    )

    monkeypatch.setattr(main, "_emit_planner_event", lambda event, payload, **kwargs: planner_events.append((event, payload, kwargs)))
    monkeypatch.setattr(main, "_emit_ml_event", lambda event_name, payload, **kwargs: ml_events.append((event_name, payload, kwargs)))

    request = main.GeneratePlanRequest.model_validate(
        {
            "profile": {
                "displayName": "Cache User",
                "age": 25,
                "heightCm": 160,
                "weightKg": 60,
                "activityLevel": "Lightly Active",
                "goal": "General Health",
                "dietaryRestrictions": [],
                "allergies": [],
                "pantryItems": [],
            },
            "days": 7,
            "mealsPerDay": 3,
        }
    )
    fresh = main._freshen_cached_plan_response(cached, request_id="fresh-request", started_ms=2000, completed_ms=2500)
    main._emit_cache_hit_completion_events(fresh, request=request, uid="cache-user", policy_version="policy-v1:test")

    assert planner_events[0][0] == "solver_completed"
    assert planner_events[0][1]["status"] == "no-safe-plan"
    assert planner_events[0][1]["cacheHit"] is True
    assert planner_events[0][1]["requestId"] == "fresh-request"
    assert ml_events[0][0] == "no_safe_plan_encountered"
    assert ml_events[0][1]["cache_hit"] is True
    assert ml_events[0][2]["request_id"] == "fresh-request"


def test_generate_plan_exception_emits_error_completion_event(monkeypatch):
    planner_events = []

    monkeypatch.setattr(main, "_solve_with_telemetry", lambda *_args, **_kwargs: (_ for _ in ()).throw(RuntimeError("solver boom")))
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))
    monkeypatch.setattr(main, "_is_circuit_open", lambda policy: False)
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)
    monkeypatch.setattr(main, "_emit_planner_event", lambda event, payload, **kwargs: planner_events.append((event, payload, kwargs)))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "sync-owner"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "profile": {
            "displayName": "Crash User",
            "age": 25,
            "heightCm": 160,
            "weightKg": 60,
            "activityLevel": "Lightly Active",
            "goal": "General Health",
            "dietaryRestrictions": [],
            "allergies": [],
            "pantryItems": [],
        },
        "days": 7,
        "mealsPerDay": 3,
    }

    try:
        with TestClient(main.app) as client:
            response = client.post("/generate-plan", json=payload)
    finally:
        main.app.dependency_overrides = {}

    assert response.status_code == 500
    solver_events = [item for item in planner_events if item[0] == "solver_completed"]
    assert len(solver_events) == 1
    assert solver_events[0][1]["status"] == "error"
    assert solver_events[0][1]["reasonCodes"] == ["SOLVER_EXCEPTION"]
    assert solver_events[0][2]["uid"] == "sync-owner"


def test_generate_plan_idempotency_does_not_reuse_cache_for_changed_request(monkeypatch):
    calls = {"count": 0}

    def fake_solve(_request, _recipes, weight_set=None, policy=None):
        calls["count"] += 1
        return None, "Infeasible", None

    main._idempotency_cache.clear()
    monkeypatch.setattr(main, "solve_meal_plan", fake_solve)
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))
    monkeypatch.setattr(main, "_is_circuit_open", lambda policy: False)
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "test"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    base_payload = {
        "profile": {
            "displayName": "Test",
            "age": 25,
            "heightCm": 160,
            "weightKg": 60,
            "activityLevel": "Lightly Active",
            "goal": "General Health",
            "dietaryRestrictions": [],
            "allergies": [],
            "pantryItems": [],
        },
        "days": 7,
        "mealsPerDay": 3,
    }
    changed_payload = {
        **base_payload,
        "profile": {**base_payload["profile"], "pantryItems": ["egg"]},
    }

    headers = {"Idempotency-Key": "idem-same-key"}
    try:
        with TestClient(main.app) as client:
            first = client.post("/generate-plan", json=base_payload, headers=headers)
            second = client.post("/generate-plan", json=changed_payload, headers=headers)
    finally:
        main.app.dependency_overrides = {}
        main._idempotency_cache.clear()

    assert first.status_code == 200
    assert second.status_code == 200
    assert calls["count"] == 2
    assert first.json()["requestId"] != second.json()["requestId"]


def test_generate_plan_idempotency_does_not_cross_user_boundary(monkeypatch):
    calls = {"count": 0}
    current_uid = {"value": "user-a"}

    def fake_solve(_request, _recipes, weight_set=None, policy=None):
        calls["count"] += 1
        return None, "Infeasible", None

    main._idempotency_cache.clear()
    monkeypatch.setattr(main, "solve_meal_plan", fake_solve)
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))
    monkeypatch.setattr(main, "_is_circuit_open", lambda policy: False)
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": current_uid["value"]}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
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
            "pantryItems": [],
        },
        "days": 7,
        "mealsPerDay": 3,
    }

    headers = {"Idempotency-Key": "idem-cross-user"}
    try:
        with TestClient(main.app) as client:
            first = client.post("/generate-plan", json=payload, headers=headers)
            current_uid["value"] = "user-b"
            second = client.post("/generate-plan", json=payload, headers=headers)
    finally:
        main.app.dependency_overrides = {}
        main._idempotency_cache.clear()

    assert first.status_code == 200
    assert second.status_code == 200
    assert calls["count"] == 2
    assert first.json()["requestId"] != second.json()["requestId"]
