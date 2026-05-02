import json
import time
import uuid
from typing import Any, Callable, Dict

from fastapi import HTTPException

from domain.models import GeneratePlanRequest


class PlanJobOpsService:
    def __init__(
        self,
        *,
        database_module: Any,
        queue_broker: Any,
        rate_limit_store: Any,
        init_job: Callable[..., None],
        dispatch_async_job: Callable[..., Dict[str, Any]],
        increment_plan_job_diag: Callable[[str], None],
    ):
        self._database = database_module
        self._queue_broker = queue_broker
        self._rate_limit_store = rate_limit_store
        self._init_job = init_job
        self._dispatch_async_job = dispatch_async_job
        self._increment_plan_job_diag = increment_plan_job_diag

    def diagnostics(self) -> Dict[str, Any]:
        counters = self._database.get_plan_job_diagnostics()
        queue_status = {
            "queued": self._database.count_plan_jobs_by_status("queued"),
            "running": self._database.count_plan_jobs_by_status("running"),
            "done": self._database.count_plan_jobs_by_status("done"),
            "error": self._database.count_plan_jobs_by_status("error"),
            "dead-letter": self._database.count_plan_jobs_by_status("dead-letter"),
        }
        return {
            "status": "ok",
            "queueStatus": queue_status,
            "diagnostics": counters,
            "queueBroker": self._queue_broker.health(),
            "rateLimit": self._rate_limit_store.health(),
            "generatedAtMs": int(time.time() * 1000),
        }

    def list_jobs(
        self,
        *,
        status: str | None = None,
        owner_uid: str | None = None,
        q: str | None = None,
        limit: int = 100,
    ) -> Dict[str, Any]:
        items = self._database.list_plan_jobs(status=status, owner_uid=owner_uid, q=q, limit=limit)
        return {"status": "ok", "items": items, "count": len(items), "generatedAtMs": int(time.time() * 1000)}

    def get_job_detail(self, job_id: str) -> Dict[str, Any]:
        job = self._database.get_plan_job(job_id, any_owner=True)
        if not job:
            raise HTTPException(status_code=404, detail="Job not found")
        return {"status": "ok", "job": job, "generatedAtMs": int(time.time() * 1000)}

    def requeue_job(self, job_id: str, *, principal: Dict[str, Any]) -> Dict[str, Any]:
        existing = self._database.get_plan_job(job_id, any_owner=True)
        if not existing:
            raise HTTPException(status_code=404, detail="Job not found")
        if str(existing.get("status") or "").lower() == "running":
            raise HTTPException(status_code=409, detail="Running jobs cannot be manually requeued")
        request_payload = existing.get("request")
        if not isinstance(request_payload, dict):
            raise HTTPException(status_code=409, detail="Job cannot be requeued without a stored request payload")
        request = GeneratePlanRequest.model_validate(request_payload)
        job = self._database.requeue_plan_job(job_id, reset_attempt_count=True)
        if not job:
            raise HTTPException(status_code=409, detail="Job could not be requeued")
        owner_uid = str(existing.get("ownerUid") or "").strip() or None
        dispatch = self._dispatch_async_job(job_id, request, owner_uid=owner_uid)
        self._database.log_admin_action(
            "plan_job.requeue",
            actor=str(principal.get("actor") or "ops-admin"),
            resource_type="plan_job",
            resource_id=job_id,
            details={"previousStatus": existing.get("status"), "ownerUid": existing.get("ownerUid"), **dispatch},
        )
        return {"status": "ok", "job": job, **dispatch}

    def replay_job(self, job_id: str, *, principal: Dict[str, Any]) -> Dict[str, Any]:
        existing = self._database.get_plan_job(job_id, any_owner=True)
        if not existing:
            raise HTTPException(status_code=404, detail="Job not found")
        request_payload = existing.get("request")
        if not isinstance(request_payload, dict):
            raise HTTPException(status_code=409, detail="Job cannot be replayed without a stored request payload")
        request = GeneratePlanRequest.model_validate(request_payload)
        replay_job_id = uuid.uuid4().hex
        request_json = request.model_dump_json() if hasattr(request, "model_dump_json") else json.dumps(request.model_dump())
        owner_uid = str(existing.get("ownerUid") or "").strip() or None
        self._init_job(replay_job_id, request_json=request_json, owner_uid=owner_uid)
        self._increment_plan_job_diag("ops_plan_job_replay_total")
        dispatch = self._dispatch_async_job(replay_job_id, request, owner_uid=owner_uid)
        replay_job = self._database.get_plan_job(replay_job_id, any_owner=True)
        self._database.log_admin_action(
            "plan_job.replay",
            actor=str(principal.get("actor") or "ops-admin"),
            resource_type="plan_job",
            resource_id=replay_job_id,
            details={"sourceJobId": job_id, "ownerUid": owner_uid, **dispatch},
        )
        return {
            "status": "ok",
            "sourceJobId": job_id,
            "job": replay_job,
            **dispatch,
        }
