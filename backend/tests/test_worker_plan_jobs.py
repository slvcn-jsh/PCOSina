import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import worker_plan_jobs
from ml_events import uid_hash


class _NoBroker:
    backend = "db"

    def is_enabled(self):
        return False

    def publish(self, job_id: str):
        return False

    def pop(self, timeout_seconds: float = 1.0):
        return None

    def health(self):
        return {"backend": "db", "enabled": False}


def test_worker_main_skips_database_bootstrap_when_predeploy_owns_it(monkeypatch):
    calls = []
    monkeypatch.setenv("PCOSINA_BOOTSTRAP_ON_STARTUP", "false")
    monkeypatch.setenv("PCOSINA_WORKER_RUN_FOREVER", "false")
    monkeypatch.setattr(worker_plan_jobs.database, "init_db", lambda: calls.append("init_db"))
    monkeypatch.setattr(worker_plan_jobs.database, "seed_recipes", lambda: calls.append("seed_recipes"))
    monkeypatch.setattr(worker_plan_jobs.policy_store, "init_policy_store", lambda: calls.append("policy_store"))
    monkeypatch.setattr(worker_plan_jobs, "run_once", lambda: calls.append("run_once"))

    assert worker_plan_jobs.main() == 0
    assert calls == ["run_once"]


def test_worker_requeues_with_next_attempt_on_solver_exception(monkeypatch):
    captured = {}
    diag = []
    recorded_events = []
    monkeypatch.setattr(worker_plan_jobs, "QUEUE_BROKER", _NoBroker())

    monkeypatch.setattr(
        worker_plan_jobs.database,
        "claim_next_plan_job",
        lambda worker_id: {
            "id": "job-1",
            "request": {"profile": {}, "days": 7, "mealsPerDay": 3},
            "ownerUid": "owner-retry",
            "attemptCount": 1,
        },
    )
    monkeypatch.setattr(
        worker_plan_jobs,
        "_runtime_policy",
        lambda: ({"sync_offline": {"dead_letter_threshold": 5, "sync_retry_backoff": [1234]}}, "policy-v1"),
    )
    monkeypatch.setattr(worker_plan_jobs.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(worker_plan_jobs, "solve_meal_plan", lambda req, recipes, policy=None: (_ for _ in ()).throw(RuntimeError("boom")))
    monkeypatch.setattr(worker_plan_jobs.database, "increment_plan_job_diagnostic", lambda key, delta=1: diag.append((key, delta)))
    monkeypatch.setattr(worker_plan_jobs.database, "record_ml_event", lambda event: recorded_events.append(event))
    monkeypatch.setattr(worker_plan_jobs.time, "sleep", lambda seconds: None)

    def _capture_update(job_id, status, result_json=None, error=None, worker_id=None, increment_attempt=False, next_attempt_at=None):
        captured["job_id"] = job_id
        captured["status"] = status
        captured["next_attempt_at"] = next_attempt_at
        captured["error"] = error

    monkeypatch.setattr(worker_plan_jobs.database, "update_plan_job", _capture_update)

    processed = worker_plan_jobs.run_once()
    assert processed is True
    assert captured["status"] == "queued"
    assert isinstance(captured["next_attempt_at"], int)
    assert captured["next_attempt_at"] > 0
    metric_keys = [k for k, _ in diag]
    assert "queue_worker_failures_total" in metric_keys
    assert "queue_worker_retry_queued_total" in metric_keys
    failure_events = [event for event in recorded_events if event["event_name"] == "async_solver_completed"]
    assert len(failure_events) == 1
    assert failure_events[0]["status"] == "error"
    assert failure_events[0]["reason_codes"] == ["SOLVER_EXCEPTION"]
    assert failure_events[0]["failureStatus"] == "queued"
    assert failure_events[0]["uid_hash"] == uid_hash("owner-retry")


def test_worker_moves_to_dead_letter_when_threshold_reached(monkeypatch):
    captured = {}
    diag = []
    recorded_events = []
    monkeypatch.setattr(worker_plan_jobs, "QUEUE_BROKER", _NoBroker())

    monkeypatch.setattr(
        worker_plan_jobs.database,
        "claim_next_plan_job",
        lambda worker_id: {"id": "job-2", "request": None, "ownerUid": "owner-dead", "attemptCount": 5},
    )
    monkeypatch.setattr(
        worker_plan_jobs,
        "_runtime_policy",
        lambda: ({"sync_offline": {"dead_letter_threshold": 5, "sync_retry_backoff": [1000]}}, "policy-v1"),
    )
    monkeypatch.setattr(worker_plan_jobs.database, "increment_plan_job_diagnostic", lambda key, delta=1: diag.append((key, delta)))
    monkeypatch.setattr(worker_plan_jobs.database, "record_ml_event", lambda event: recorded_events.append(event))
    monkeypatch.setattr(worker_plan_jobs.time, "sleep", lambda seconds: None)

    def _capture_update(job_id, status, result_json=None, error=None, worker_id=None, increment_attempt=False, next_attempt_at=None):
        captured["status"] = status
        captured["next_attempt_at"] = next_attempt_at

    monkeypatch.setattr(worker_plan_jobs.database, "update_plan_job", _capture_update)

    processed = worker_plan_jobs.run_once()
    assert processed is True
    assert captured["status"] == "dead-letter"
    assert captured["next_attempt_at"] is None
    metric_keys = [k for k, _ in diag]
    assert "queue_worker_dead_letter_total" in metric_keys
    failure_events = [event for event in recorded_events if event["event_name"] == "async_solver_completed"]
    assert len(failure_events) == 1
    assert failure_events[0]["reason_codes"] == ["MISSING_REQUEST_PAYLOAD"]
    assert failure_events[0]["failureStatus"] == "dead-letter"
    assert failure_events[0]["uid_hash"] == uid_hash("owner-dead")


def test_worker_success_records_stage1_features_and_ml_events(monkeypatch):
    captured = {}
    diag = []
    recorded_events = []
    recorded_stage1 = {}
    monkeypatch.setattr(worker_plan_jobs, "QUEUE_BROKER", _NoBroker())

    monkeypatch.setattr(
        worker_plan_jobs.database,
        "claim_next_plan_job",
        lambda worker_id: {
            "id": "job-success",
            "request": {"profile": {}, "days": 1, "mealsPerDay": 3},
            "ownerUid": "owner-async",
            "attemptCount": 1,
        },
    )
    monkeypatch.setattr(
        worker_plan_jobs,
        "_runtime_policy",
        lambda: ({"sync_offline": {"dead_letter_threshold": 5, "sync_retry_backoff": [1000]}}, "policy-v1"),
    )
    monkeypatch.setattr(worker_plan_jobs.database, "get_all_recipes", lambda: [])

    def _fake_solve(req, recipes, policy=None, telemetry_out=None):
        if telemetry_out is not None:
            telemetry_out.update(
                {
                    "stage1_candidates": [{"recipe_id": "recipe-1", "model_score": 0.73}],
                    "selected_recipe_ids": ["recipe-1"],
                    "ranking_strategy": "stage1_ml_canary_plus_heuristic",
                    "ml_model_version": "shadow_v1",
                    "candidate_count_pre": 12,
                    "candidate_count_post": 4,
                    "ml_score_enabled": True,
                }
            )
        return (
            [
                {
                    "dayLabel": "Mon",
                    "meals": [{"mealLabel": "Breakfast", "recipeId": "recipe-1", "title": "Tinola Breakfast"}],
                    "totalCalories": 420,
                }
            ],
            "Success",
            {"candidatePoolSize": 4},
        )

    monkeypatch.setattr(worker_plan_jobs, "solve_meal_plan", _fake_solve)
    monkeypatch.setattr(worker_plan_jobs.database, "increment_plan_job_diagnostic", lambda key, delta=1: diag.append((key, delta)))
    monkeypatch.setattr(worker_plan_jobs.database, "record_ml_event", lambda event: recorded_events.append(event))
    monkeypatch.setattr(worker_plan_jobs.database, "record_stage1_candidate_features", lambda **kwargs: recorded_stage1.update(kwargs))

    def _capture_update(job_id, status, result_json=None, error=None, worker_id=None, increment_attempt=False, next_attempt_at=None):
        captured["job_id"] = job_id
        captured["status"] = status
        captured["result_json"] = result_json
        captured["worker_id"] = worker_id

    monkeypatch.setattr(worker_plan_jobs.database, "update_plan_job", _capture_update)

    processed = worker_plan_jobs.run_once()

    assert processed is True
    assert captured["status"] == "done"
    assert captured["job_id"] == "job-success"
    assert captured["worker_id"] == worker_plan_jobs.WORKER_ID
    assert recorded_stage1["request_id"] == "job-success"
    assert recorded_stage1["uid_hash"] == uid_hash("owner-async")
    assert recorded_stage1["ranking_strategy"] == "stage1_ml_canary_plus_heuristic"
    assert recorded_stage1["model_version"] == "shadow_v1"

    event_names = [event["event_name"] for event in recorded_events]
    assert "plan_generation_requested" in event_names
    assert "stage1_candidates_scored" in event_names
    assert "async_solver_completed" in event_names
    assert "plan_generated" in event_names
    assert {event["uid_hash"] for event in recorded_events} == {uid_hash("owner-async")}

    metric_keys = [k for k, _ in diag]
    assert "queue_worker_success_total" in metric_keys


def test_worker_no_safe_response_uses_structured_guidance_and_reason_codes(monkeypatch):
    captured = {}
    recorded_events = []
    monkeypatch.setattr(worker_plan_jobs, "QUEUE_BROKER", _NoBroker())

    monkeypatch.setattr(
        worker_plan_jobs.database,
        "claim_next_plan_job",
        lambda worker_id: {
            "id": "job-no-safe",
            "request": {
                "profile": {
                    "displayName": "NoSafeUser",
                    "age": 25,
                    "heightCm": 160,
                    "weightKg": 60,
                    "activityLevel": "Lightly Active",
                    "goal": "General Health",
                    "dietaryRestrictions": ["Budget Vegetarian", "Gluten-Free", "Dairy-Free"],
                    "allergies": [],
                    "pantryItems": [],
                    "weeklyBudgetPhp": 800,
                    "maxCookingTimeMinutes": 15,
                },
                "days": 7,
                "mealsPerDay": 3,
            },
            "ownerUid": "owner-nosafe",
            "attemptCount": 1,
        },
    )
    monkeypatch.setattr(
        worker_plan_jobs,
        "_runtime_policy",
        lambda: ({"sync_offline": {"dead_letter_threshold": 5, "sync_retry_backoff": [1000]}}, "policy-v1"),
    )
    monkeypatch.setattr(worker_plan_jobs.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(
        worker_plan_jobs,
        "solve_meal_plan",
        lambda req, recipes, policy=None, telemetry_out=None: (None, "Infeasible due to conflicting restrictions", None),
    )
    monkeypatch.setattr(worker_plan_jobs.database, "record_ml_event", lambda event: recorded_events.append(event))
    monkeypatch.setattr(worker_plan_jobs.database, "increment_plan_job_diagnostic", lambda key, delta=1: None)

    def _capture_update(job_id, status, result_json=None, error=None, worker_id=None, increment_attempt=False, next_attempt_at=None):
        captured["job_id"] = job_id
        captured["status"] = status
        captured["result_json"] = result_json

    monkeypatch.setattr(worker_plan_jobs.database, "update_plan_job", _capture_update)

    processed = worker_plan_jobs.run_once()

    assert processed is True
    assert captured["status"] == "done"
    body = json.loads(captured["result_json"])
    assert body["status"] == "no-safe-plan"
    assert "CONFLICTING_RESTRICTIONS" in body["machineReasonCodes"]
    assert body["diagnosticsReference"] == "job-no-safe"
    assert any("conflicts" in item.lower() for item in body["humanGuidance"])
    assert any("budget" in item.lower() for item in body["humanGuidance"])
    assert any("cooking" in item.lower() for item in body["humanGuidance"])
    assert any("reason_codes" in event for event in recorded_events if event["event_name"] == "no_safe_plan_encountered")
    assert {event["uid_hash"] for event in recorded_events} == {uid_hash("owner-nosafe")}
