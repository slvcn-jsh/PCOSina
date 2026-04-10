import json
import sqlite3
import time
from pathlib import Path
from uuid import uuid4

import database


def _prepare_temp_db() -> Path:
    tmp_root = Path(__file__).resolve().parent / ".tmp_plan_job_diagnostics"
    tmp_root.mkdir(parents=True, exist_ok=True)
    db_path = tmp_root / f"plan_jobs_diag_{uuid4().hex}.db"
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    return db_path


def test_claim_next_plan_job_respects_next_attempt_at():
    _prepare_temp_db()
    now = int(time.time() * 1000)
    database.create_plan_job("job-1", request_json=json.dumps({"x": 1}), idempotency_key="idem-1", owner_uid="owner-a")
    database.update_plan_job("job-1", status="queued", next_attempt_at=now + 60000)

    # Force check window before next_attempt_at.
    conn = sqlite3.connect(database.DB_NAME)
    try:
        cur = conn.cursor()
        cur.execute("UPDATE plan_jobs SET next_attempt_at = ? WHERE id = ?", (now + 60000, "job-1"))
        conn.commit()
    finally:
        conn.close()

    job = database.claim_next_plan_job("worker-a")
    assert job is None

    conn = sqlite3.connect(database.DB_NAME)
    try:
        cur = conn.cursor()
        cur.execute("UPDATE plan_jobs SET next_attempt_at = 0 WHERE id = ?", ("job-1",))
        conn.commit()
    finally:
        conn.close()

    claimed = database.claim_next_plan_job("worker-a")
    assert claimed is not None
    assert claimed["id"] == "job-1"
    assert claimed["ownerUid"] == "owner-a"


def test_plan_job_diagnostics_counter_increment():
    _prepare_temp_db()
    database.increment_plan_job_diagnostic("queue_worker_claimed_total")
    database.increment_plan_job_diagnostic("queue_worker_claimed_total", delta=2)
    database.increment_plan_job_diagnostic("sync_requests_total", delta=5)

    payload = database.get_plan_job_diagnostics()
    metrics = payload.get("metrics") or {}
    assert int(metrics.get("queue_worker_claimed_total") or 0) == 3
    assert int(metrics.get("sync_requests_total") or 0) == 5
