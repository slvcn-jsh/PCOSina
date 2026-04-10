import sys
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import database
import main
from ml_events import uid_hash


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_async_job_owner"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"plan_jobs_owner_{uuid4().hex}.db"


def test_generate_plan_async_records_owner_uid(monkeypatch):
    captured = {}

    def fake_init_job(job_id: str, request_json: str | None = None, idempotency_key: str | None = None, owner_uid: str | None = None):
        captured["job_id"] = job_id
        captured["owner_uid"] = owner_uid
        captured["idempotency_key"] = idempotency_key

    monkeypatch.setattr(main, "ASYNC_MODE", "queued")
    monkeypatch.setattr(main, "QUEUE_BROKER", type("NoBroker", (), {"is_enabled": lambda self: False, "health": lambda self: {"backend": "db"}, "publish": lambda self, job_id: False})())
    monkeypatch.setattr(main, "_init_job", fake_init_job)
    monkeypatch.setattr(main.database, "find_plan_job_by_idempotency", lambda *args, **kwargs: None)
    monkeypatch.setattr(main.database, "count_plan_jobs_by_status", lambda status: 0)
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({"planning": {"planning_horizon_days": 7, "meals_per_day": 3}, "sync_offline": {"sync_batch_size": 100}, "solver": {"queue_priority_rules": {"default": "fifo"}}}, "policy-v-test"))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "owner-123"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "profile": {
            "displayName": "OwnerUser",
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
            response = client.post("/generate-plan-async", json=payload)
        assert response.status_code == 200
        assert captured["owner_uid"] == "owner-123"
    finally:
        main.app.dependency_overrides = {}


def test_generate_plan_async_reuses_matching_idempotent_job(monkeypatch):
    captured = {"init_called": False}

    def fake_init_job(job_id: str, request_json: str | None = None, idempotency_key: str | None = None, owner_uid: str | None = None):
        captured["init_called"] = True

    monkeypatch.setattr(main, "ASYNC_MODE", "queued")
    monkeypatch.setattr(main, "QUEUE_BROKER", type("NoBroker", (), {"is_enabled": lambda self: False, "health": lambda self: {"backend": "db"}, "publish": lambda self, job_id: False})())
    monkeypatch.setattr(main, "_init_job", fake_init_job)
    monkeypatch.setattr(
        main.database,
        "find_plan_job_by_idempotency",
        lambda idempotency_key, request_json=None, owner_uid=None: {
            "id": "existing-job-1",
            "status": "queued",
            "ownerUid": owner_uid,
            "idempotencyKey": idempotency_key,
        },
    )
    monkeypatch.setattr(main.database, "count_plan_jobs_by_status", lambda status: 999)
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({"planning": {"planning_horizon_days": 7, "meals_per_day": 3}, "sync_offline": {"sync_batch_size": 1}, "solver": {"queue_priority_rules": {"default": "fifo"}}}, "policy-v-test"))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "owner-123"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "profile": {
            "displayName": "OwnerUser",
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
            response = client.post(
                "/generate-plan-async",
                json=payload,
                headers={"Idempotency-Key": "async-idem-owner"},
            )
        assert response.status_code == 200
        assert response.json()["jobId"] == "existing-job-1"
        assert response.json()["status"] == "queued"
        assert response.json()["reused"] is True
        assert captured["init_called"] is False
    finally:
        main.app.dependency_overrides = {}


def test_get_plan_job_requires_matching_owner(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    database.create_plan_job("owned-job-1", request_json="{}", owner_uid="owner-abc")

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "other-user"}
    try:
        with TestClient(main.app) as client:
            response = client.get("/plan-jobs/owned-job-1")
        assert response.status_code == 404
    finally:
        main.app.dependency_overrides = {}


def test_run_job_uses_owner_uid_for_async_telemetry(monkeypatch):
    recorded_events = []
    recorded_stage1 = {}

    request = main.GeneratePlanRequest.model_validate(
        {
            "profile": {
                "displayName": "OwnerUser",
                "age": 25,
                "heightCm": 160,
                "weightKg": 60,
                "activityLevel": "Lightly Active",
                "goal": "General Health",
                "dietaryRestrictions": [],
                "allergies": [],
                "pantryItems": [],
            },
            "days": 1,
            "mealsPerDay": 3,
        }
    )

    def _fake_solve(req, recipes, policy_payload):
        return (
            [
                {
                    "dayLabel": "Mon",
                    "meals": [{"mealLabel": "Breakfast", "recipeId": "recipe-1", "title": "Tinola Breakfast"}],
                    "totalCalories": 420,
                }
            ],
            "Success",
            {"candidatePoolSize": 2},
            {
                "stage1_candidates": [{"recipe_id": "recipe-1", "model_score": 0.73}],
                "selected_recipe_ids": ["recipe-1"],
                "ranking_strategy": "stage1_ml_canary_plus_heuristic",
                "ml_model_version": "shadow_v1",
                "candidate_count_pre": 9,
                "candidate_count_post": 2,
                "ml_score_enabled": True,
            },
        )

    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v-owner"))
    monkeypatch.setattr(main, "_solve_with_telemetry", _fake_solve)
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main.database, "update_plan_job", lambda *args, **kwargs: None)
    monkeypatch.setattr(main.database, "record_ml_event", lambda event: recorded_events.append(event))
    monkeypatch.setattr(main.database, "record_stage1_candidate_features", lambda **kwargs: recorded_stage1.update(kwargs))
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)
    monkeypatch.setattr(main, "_inc_plan_job_diag", lambda key, delta=1: None)

    main._run_job("async-owner-job", request, owner_uid="owner-123")

    assert recorded_stage1["request_id"] == "async-owner-job"
    assert recorded_stage1["uid_hash"] == uid_hash("owner-123")
    assert {event["uid_hash"] for event in recorded_events} == {uid_hash("owner-123")}


def test_run_job_emits_error_completion_event_for_inprocess_exception(monkeypatch):
    recorded_events = []

    request = main.GeneratePlanRequest.model_validate(
        {
            "profile": {
                "displayName": "OwnerUser",
                "age": 25,
                "heightCm": 160,
                "weightKg": 60,
                "activityLevel": "Lightly Active",
                "goal": "General Health",
                "dietaryRestrictions": [],
                "allergies": [],
                "pantryItems": [],
            },
            "days": 1,
            "mealsPerDay": 3,
        }
    )

    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v-owner"))
    monkeypatch.setattr(main.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(main, "_solve_with_telemetry", lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("solver boom")))
    monkeypatch.setattr(main.database, "update_plan_job", lambda *args, **kwargs: None)
    monkeypatch.setattr(main.database, "record_ml_event", lambda event: recorded_events.append(event))
    monkeypatch.setattr(main, "_record_solver_outcome", lambda success: None)
    monkeypatch.setattr(main, "_inc_plan_job_diag", lambda key, delta=1: None)

    main._run_job("async-owner-error", request, owner_uid="owner-err")

    failure_events = [event for event in recorded_events if event["event_name"] == "async_solver_completed"]
    assert len(failure_events) == 1
    assert failure_events[0]["status"] == "error"
    assert failure_events[0]["reason_codes"] == ["SOLVER_EXCEPTION"]
    assert failure_events[0]["uid_hash"] == uid_hash("owner-err")

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "owner-abc"}
    try:
        with TestClient(main.app) as client:
            response = client.get("/plan-jobs/owned-job-1")
        assert response.status_code == 200
        assert response.json()["ownerUid"] == "owner-abc"
    finally:
        main.app.dependency_overrides = {}
