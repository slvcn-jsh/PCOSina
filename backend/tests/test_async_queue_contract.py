import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


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


def test_generate_plan_async_queued_mode_persists_request(monkeypatch):
    captured = {}

    def fake_init_job(job_id: str, request_json: str | None = None, idempotency_key: str | None = None):
        captured["job_id"] = job_id
        captured["request_json"] = request_json
        captured["idempotency_key"] = idempotency_key

    monkeypatch.setattr(main, "ASYNC_MODE", "queued")
    monkeypatch.setattr(main, "QUEUE_BROKER", _NoBroker())
    monkeypatch.setattr(main, "_init_job", fake_init_job)
    monkeypatch.setattr(main.database, "find_plan_job_by_idempotency", lambda *args, **kwargs: None)
    monkeypatch.setattr(main.database, "count_plan_jobs_by_status", lambda status: 0)
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({"planning": {"planning_horizon_days": 7, "meals_per_day": 3}, "sync_offline": {"sync_batch_size": 100}, "solver": {"queue_priority_rules": {"default": "fifo"}}}, "policy-v-test"))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "test"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "profile": {
            "displayName": "AsyncUser",
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
        response = client.post(
            "/generate-plan-async",
            json=payload,
            headers={"Idempotency-Key": "async-idem"},
        )

    main.app.dependency_overrides = {}

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "queued"
    assert body["executionMode"] == "queued"
    assert captured["idempotency_key"] == "async-idem"
    assert '"displayName":"AsyncUser"' in (captured["request_json"] or "")
