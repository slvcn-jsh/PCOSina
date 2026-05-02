import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi import HTTPException

from services.plan_job_ops_service import PlanJobOpsService


class _FakeStore:
    def health(self):
        return {"backend": "memory", "enabled": True}


class _FakeDatabase:
    def __init__(self):
        self.diag_calls = []
        self.logged_actions = []
        self.jobs = {
            "job-1": {
                "id": "job-1",
                "status": "dead-letter",
                "ownerUid": "user-1",
                "request": {
                    "profile": {
                        "displayName": "User 1",
                        "age": 24,
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
                },
                "attemptCount": 1,
            }
        }

    def get_plan_job_diagnostics(self):
        return {"metrics": {"sync_requests_total": 3}}

    def count_plan_jobs_by_status(self, status):
        return {"queued": 1, "running": 0, "done": 2, "error": 0, "dead-letter": 1}.get(status, 0)

    def list_plan_jobs(self, status=None, owner_uid=None, q=None, limit=100):
        items = list(self.jobs.values())
        if status:
            items = [item for item in items if item["status"] == status]
        if owner_uid:
            items = [item for item in items if item.get("ownerUid") == owner_uid]
        return items[:limit]

    def get_plan_job(self, job_id, any_owner=False):
        return self.jobs.get(job_id)

    def requeue_plan_job(self, job_id, reset_attempt_count=True):
        job = self.jobs.get(job_id)
        if not job:
            return None
        updated = dict(job)
        updated["status"] = "queued"
        if reset_attempt_count:
            updated["attemptCount"] = 0
        updated["error"] = None
        self.jobs[job_id] = updated
        return updated

    def log_admin_action(self, action, **kwargs):
        self.logged_actions.append((action, kwargs))


def test_plan_job_ops_service_diagnostics_list_requeue_and_replay():
    fake_db = _FakeDatabase()
    initialized_jobs = []
    dispatched_jobs = []

    service = PlanJobOpsService(
        database_module=fake_db,
        queue_broker=_FakeStore(),
        rate_limit_store=_FakeStore(),
        init_job=lambda job_id, **kwargs: initialized_jobs.append((job_id, kwargs)),
        dispatch_async_job=lambda job_id, request, **kwargs: dispatched_jobs.append((job_id, kwargs)) or {"executionMode": "queued"},
        increment_plan_job_diag=lambda key: fake_db.diag_calls.append(key),
    )

    diagnostics = service.diagnostics()
    assert diagnostics["queueStatus"]["dead-letter"] == 1

    listing = service.list_jobs(status="dead-letter")
    assert listing["count"] == 1

    detail = service.get_job_detail("job-1")
    assert detail["job"]["ownerUid"] == "user-1"

    requeued = service.requeue_job("job-1", principal={"actor": "ops@example.com"})
    assert requeued["job"]["status"] == "queued"
    assert dispatched_jobs[0][0] == "job-1"
    assert fake_db.logged_actions[-1][0] == "plan_job.requeue"

    replayed = service.replay_job("job-1", principal={"actor": "ops@example.com"})
    assert replayed["sourceJobId"] == "job-1"
    assert initialized_jobs
    assert fake_db.diag_calls == ["ops_plan_job_replay_total"]
    assert fake_db.logged_actions[-1][0] == "plan_job.replay"


def test_plan_job_ops_service_requeue_rejects_running_jobs():
    fake_db = _FakeDatabase()
    fake_db.jobs["job-1"]["status"] = "running"
    service = PlanJobOpsService(
        database_module=fake_db,
        queue_broker=_FakeStore(),
        rate_limit_store=_FakeStore(),
        init_job=lambda *args, **kwargs: None,
        dispatch_async_job=lambda *args, **kwargs: {},
        increment_plan_job_diag=lambda key: None,
    )

    try:
        service.requeue_job("job-1", principal={"actor": "ops@example.com"})
        assert False, "Expected HTTPException"
    except HTTPException as exc:
        assert exc.status_code == 409
        assert exc.detail == "Running jobs cannot be manually requeued"
