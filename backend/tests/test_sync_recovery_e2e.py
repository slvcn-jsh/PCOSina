import json
import sqlite3
import sys
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database
import worker_plan_jobs
from services.sync_recovery import resolve_uid_scoped_snapshot


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_sync_recovery"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"sync_recovery_{uuid4().hex}.db"


def _valid_request_json() -> str:
    payload = {
        "profile": {
            "displayName": "RecoveryUser",
            "age": 26,
            "heightCm": 160,
            "weightKg": 58,
            "activityLevel": "Lightly Active",
            "goal": "General Health",
            "dietaryRestrictions": [],
            "allergies": [],
            "pantryItems": [],
        },
        "days": 7,
        "mealsPerDay": 3,
    }
    return json.dumps(payload)


def test_retry_dead_letter_persists_after_reinit_and_recovery_assertion(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    worker_plan_jobs.database.DATABASE_URL = ""
    worker_plan_jobs.database.DB_NAME = str(db_path)

    database.create_plan_job("retry-job-1", request_json=_valid_request_json(), idempotency_key="retry-idem-1")
    monkeypatch.setattr(
        worker_plan_jobs,
        "_runtime_policy",
        lambda: ({"sync_offline": {"dead_letter_threshold": 2, "sync_retry_backoff": [1]}}, "policy-v-test"),
    )
    monkeypatch.setattr(worker_plan_jobs.database, "get_all_recipes", lambda: [])
    monkeypatch.setattr(worker_plan_jobs, "solve_meal_plan", lambda req, recipes, policy=None: (_ for _ in ()).throw(RuntimeError("boom")))
    monkeypatch.setattr(worker_plan_jobs.time, "sleep", lambda seconds: None)

    first = worker_plan_jobs.run_once()
    assert first is True
    first_job = database.get_plan_job("retry-job-1")
    assert first_job is not None
    assert first_job["status"] == "queued"

    conn = sqlite3.connect(str(db_path))
    try:
        conn.execute("UPDATE plan_jobs SET next_attempt_at = 0 WHERE id = ?", ("retry-job-1",))
        conn.commit()
    finally:
        conn.close()

    second = worker_plan_jobs.run_once()
    assert second is True
    second_job = database.get_plan_job("retry-job-1")
    assert second_job is not None
    assert second_job["status"] == "dead-letter"

    # Simulated reinstall/restart path: DB init should preserve terminal state and diagnostics.
    database.init_db()
    recovered_job = database.get_plan_job("retry-job-1")
    assert recovered_job is not None
    assert recovered_job["status"] == "dead-letter"
    diag = database.get_plan_job_diagnostics().get("metrics") or {}
    assert int(diag.get("queue_worker_retry_queued_total") or 0) >= 1
    assert int(diag.get("queue_worker_dead_letter_total") or 0) >= 1


def test_sync_conflict_resolution_uid_scope_and_lww_merge():
    local = {
        "uid": "uid-123",
        "profile": {
            "displayName": "A",
            "weightKg": 61,
            "fieldUpdatedAtMs": {"displayName": 100, "weightKg": 110},
            "updatedAtMs": 110,
        },
        "pantry": [{"id": "egg", "quantity": 6, "updatedAtMs": 100}],
        "grocery": [{"id": "rice", "checked": False, "updatedAtMs": 90}],
        "logs": [{"id": "log-1", "note": "old", "updatedAtMs": 80}],
        "savedPlans": [{"id": "plan-a", "updatedAtMs": 70}],
    }
    remote = {
        "uid": "uid-123",
        "profile": {
            "displayName": "Alicia",
            "weightKg": 60,
            "fieldUpdatedAtMs": {"displayName": 130, "weightKg": 105},
            "updatedAtMs": 130,
        },
        "pantry": [{"id": "egg", "quantity": 12, "updatedAtMs": 140}],
        "grocery": [{"id": "rice", "checked": True, "updatedAtMs": 120}],
        "logs": [{"id": "log-1", "note": "new", "updatedAtMs": 130}],
        "savedPlans": [{"id": "plan-b", "updatedAtMs": 150}],
    }

    merged, event = resolve_uid_scoped_snapshot(
        uid="uid-123",
        local_snapshot=local,
        remote_snapshot=remote,
        conflict_resolution_policy="last_write_wins",
    )

    assert merged["uid"] == "uid-123"
    assert merged["profile"]["displayName"] == "Alicia"
    assert merged["profile"]["weightKg"] == 61  # local wins this field by per-field timestamp
    egg = next(item for item in merged["pantry"] if item.get("id") == "egg")
    assert int(egg.get("quantity") or 0) == 12
    rice = next(item for item in merged["grocery"] if item.get("id") == "rice")
    assert bool(rice.get("checked")) is True
    assert event["eventName"] == "recovery_complete"
    assert event["conflictResolutionPolicy"] == "last_write_wins"
    assert int(event["artifactCounts"]["pantry"]) >= 1


def test_dead_letter_state_survives_recovery_snapshot_merge():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    # Seed one dead-letter job to represent async failure before reinstall.
    database.create_plan_job("dead-1", request_json=_valid_request_json(), idempotency_key="idem-dead-1")
    database.update_plan_job("dead-1", status="dead-letter", error="solver timeout", worker_id="worker-x")
    baseline = database.get_plan_job("dead-1")
    assert baseline is not None
    assert baseline["status"] == "dead-letter"

    local = {
        "uid": "uid-900",
        "profile": {
            "displayName": "Local",
            "fieldUpdatedAtMs": {"displayName": 100},
            "updatedAtMs": 100,
        },
        "pantry": [{"id": "egg", "quantity": 4, "updatedAtMs": 110}],
        "grocery": [{"id": "rice", "checked": False, "updatedAtMs": 90}],
        "logs": [{"id": "log-1", "note": "local", "updatedAtMs": 95}],
        "savedPlans": [{"id": "plan-local", "updatedAtMs": 85}],
    }
    remote = {
        "uid": "uid-900",
        "profile": {
            "displayName": "Remote",
            "fieldUpdatedAtMs": {"displayName": 120},
            "updatedAtMs": 120,
        },
        "pantry": [{"id": "egg", "quantity": 9, "updatedAtMs": 150}],
        "grocery": [{"id": "rice", "checked": True, "updatedAtMs": 130}],
        "logs": [{"id": "log-1", "note": "remote", "updatedAtMs": 125}],
        "savedPlans": [{"id": "plan-remote", "updatedAtMs": 160}],
    }

    merged, event = resolve_uid_scoped_snapshot(
        uid="uid-900",
        local_snapshot=local,
        remote_snapshot=remote,
        conflict_resolution_policy="last_write_wins",
    )
    assert merged["profile"]["displayName"] == "Remote"
    assert next(item for item in merged["pantry"] if item.get("id") == "egg")["quantity"] == 9
    assert event["eventName"] == "recovery_complete"
    assert int(event["artifactCounts"]["savedPlans"]) >= 1

    # Reinstall/reinit should not mutate async terminal diagnostics state.
    database.init_db()
    after = database.get_plan_job("dead-1")
    assert after is not None
    assert after["status"] == "dead-letter"


def test_sync_conflict_resolution_normalizes_name_fallback_keys():
    merged, event = resolve_uid_scoped_snapshot(
        uid="uid-normalized",
        local_snapshot={
            "uid": "uid-normalized",
            "profile": {},
            "pantry": [{"name": "Brown Rice", "quantity": 1, "updatedAtMs": 100}],
            "grocery": [{"name": "Chicken-Breast", "checked": False, "updatedAtMs": 90}],
            "logs": [],
            "savedPlans": [],
        },
        remote_snapshot={
            "uid": "uid-normalized",
            "profile": {},
            "pantry": [{"name": "brown/rice", "quantity": 3, "updatedAtMs": 150}],
            "grocery": [{"name": "chicken breast", "checked": True, "updatedAtMs": 120}],
            "logs": [],
            "savedPlans": [],
        },
        conflict_resolution_policy="last_write_wins",
    )

    assert len(merged["pantry"]) == 1
    assert len(merged["grocery"]) == 1
    assert merged["pantry"][0]["quantity"] == 3
    assert merged["grocery"][0]["checked"] is True
    assert int(event["artifactCounts"]["pantry"]) == 1
    assert int(event["artifactCounts"]["grocery"]) == 1
