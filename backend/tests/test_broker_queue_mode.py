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
    base = Path(__file__).resolve().parent / ".tmp_broker_queue"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"broker_queue_{uuid4().hex}.db"


class _FakeBroker:
    def __init__(self):
        self.published: list[str] = []
        self.backend = "memory"

    def is_enabled(self) -> bool:
        return True

    def publish(self, job_id: str) -> bool:
        self.published.append(str(job_id))
        return True

    def pop(self, timeout_seconds: float = 1.0):
        return None

    def health(self):
        return {"backend": self.backend, "enabled": True, "queueDepth": len(self.published)}


def test_claim_plan_job_by_id_claims_once_only():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    request_json = json.dumps({"profile": {"displayName": "T", "age": 20, "heightCm": 160, "weightKg": 55, "activityLevel": "Lightly Active", "goal": "General Health", "dietaryRestrictions": [], "allergies": [], "pantryItems": []}, "days": 7, "mealsPerDay": 3})
    database.create_plan_job("broker-job-1", request_json=request_json, idempotency_key="idem-broker-1")

    first = database.claim_plan_job_by_id("broker-job-1", "worker-test")
    assert first is not None
    assert first["id"] == "broker-job-1"
    second = database.claim_plan_job_by_id("broker-job-1", "worker-test")
    assert second is None


def test_generate_plan_async_publishes_broker_signal(monkeypatch):
    fake_broker = _FakeBroker()
    monkeypatch.setattr(main, "QUEUE_BROKER", fake_broker)
    monkeypatch.setattr(main, "ASYNC_MODE", "queued")
    monkeypatch.setattr(main.database, "find_plan_job_by_idempotency", lambda *args, **kwargs: None)
    monkeypatch.setattr(main.database, "count_plan_jobs_by_status", lambda status: 0)
    monkeypatch.setattr(main, "_init_job", lambda job_id, request_json=None, idempotency_key=None: None)
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({"planning": {"planning_horizon_days": 7, "meals_per_day": 3}, "sync_offline": {"sync_batch_size": 100}, "solver": {"queue_priority_rules": {"default": "fifo"}}}, "policy-v-test"))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "test"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "profile": {
            "displayName": "BrokerUser",
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

    with TestClient(main.app) as client:
        resp = client.post("/generate-plan-async", json=payload)
    main.app.dependency_overrides = {}

    assert resp.status_code == 200
    body = resp.json()
    assert body["executionMode"] == "queued-broker"
    assert body["queueBackend"] == "memory"
    assert body["brokerSignalPublished"] is True
    assert len(fake_broker.published) == 1
