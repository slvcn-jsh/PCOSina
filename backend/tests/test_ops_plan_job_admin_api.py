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
    base = Path(__file__).resolve().parent / ".tmp_ops_plan_job_admin_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"ops_plan_job_admin_api_{uuid4().hex}.db"


class _FakeBroker:
    def __init__(self):
        self.backend = "memory"
        self.published: list[str] = []

    def is_enabled(self) -> bool:
        return True

    def publish(self, job_id: str) -> bool:
        self.published.append(str(job_id))
        return True

    def health(self):
        return {"backend": self.backend, "enabled": True, "queueDepth": len(self.published)}


def _request_json() -> str:
    return json.dumps(
        {
            "profile": {
                "displayName": "OpsUser",
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


def test_ops_can_list_inspect_requeue_and_replay_plan_jobs():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    database.create_plan_job("job-done-1", request_json=_request_json(), owner_uid="user-a", idempotency_key="idem-a")
    database.update_plan_job("job-done-1", status="done", result_json=json.dumps({"status": "success"}), worker_id="worker-a")

    database.create_plan_job("job-dead-1", request_json=_request_json(), owner_uid="user-b", idempotency_key="idem-b")
    database.update_plan_job("job-dead-1", status="dead-letter", error="solver timeout", worker_id="worker-b", increment_attempt=True)

    ops_admin = {
        "uid": "ops-admin-1",
        "actor": "ops-admin@example.com",
        "roles": ["ops_admin"],
    }
    fake_broker = _FakeBroker()

    original_broker = main.QUEUE_BROKER
    original_async_mode = main.ASYNC_MODE
    main.QUEUE_BROKER = fake_broker
    main.ASYNC_MODE = "queued"
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin

    try:
        with TestClient(main.app) as client:
            list_resp = client.get("/ops/plan-jobs", params={"status": "dead-letter"})
            assert list_resp.status_code == 200
            items = list_resp.json()["items"]
            assert any(item["id"] == "job-dead-1" for item in items)
            assert all("request" not in item for item in items)

            detail_resp = client.get("/ops/plan-jobs/job-done-1")
            assert detail_resp.status_code == 200
            detail = detail_resp.json()["job"]
            assert detail["ownerUid"] == "user-a"
            assert detail["request"]["profile"]["displayName"] == "OpsUser"
            assert detail["result"]["status"] == "success"

            requeue_resp = client.post("/ops/plan-jobs/job-dead-1/requeue")
            assert requeue_resp.status_code == 200
            requeued = requeue_resp.json()["job"]
            assert requeued["status"] == "queued"
            assert requeued["attemptCount"] == 0
            assert requeued.get("error") in (None, "")
            assert "job-dead-1" in fake_broker.published

            replay_resp = client.post("/ops/plan-jobs/job-done-1/replay")
            assert replay_resp.status_code == 200
            replay_body = replay_resp.json()
            replay_job = replay_body["job"]
            assert replay_body["sourceJobId"] == "job-done-1"
            assert replay_job["id"] != "job-done-1"
            assert replay_job["ownerUid"] == "user-a"
            assert replay_job["status"] == "queued"
            assert replay_job["id"] in fake_broker.published

            audit_resp = client.get("/admin/audit/logs", params={"resource_type": "plan_job"})
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "plan_job.requeue" in actions
            assert "plan_job.replay" in actions
    finally:
        main.QUEUE_BROKER = original_broker
        main.ASYNC_MODE = original_async_mode
        main.app.dependency_overrides = {}
