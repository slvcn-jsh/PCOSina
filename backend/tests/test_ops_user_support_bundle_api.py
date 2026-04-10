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
    base = Path(__file__).resolve().parent / ".tmp_ops_user_support_bundle_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"ops_user_support_bundle_api_{uuid4().hex}.db"


def _request_json(display_name: str) -> str:
    return json.dumps(
        {
            "profile": {
                "displayName": display_name,
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


def test_ops_user_support_bundle_combines_profile_jobs_and_audit_trail(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    database.create_plan_job("support-job-1", request_json=_request_json("BundleUser"), owner_uid="bundle-user")
    database.update_plan_job("support-job-1", status="done", result_json=json.dumps({"status": "success"}), worker_id="worker-a")
    database.create_plan_job("support-job-2", request_json=_request_json("BundleUser"), owner_uid="bundle-user")
    database.update_plan_job("support-job-2", status="dead-letter", error="solver timeout", worker_id="worker-b")
    database.create_support_case(
        user_uid="bundle-user",
        summary="Bundle user reported plan mismatch",
        actor="ops-admin@example.com",
        related_job_id="support-job-2",
        priority="high",
        assignee="incident-lead@example.com",
        escalated=True,
        initial_note="Reviewing latest dead-letter run",
    )
    database.log_admin_action(
        "user_profile.inspect",
        actor="ops-admin@example.com",
        resource_type="user_profile",
        resource_id="bundle-user",
        details={"includeRaw": False},
    )

    fake_raw = {
        "displayName": "Bundle User",
        "goal": "General Health",
        "activityLevel": "Lightly Active",
        "isProfileCompleted": True,
        "updatedAtEpochMs": 1000,
        "artifactsUpdatedAtEpochMs": 2000,
        "pantryEntriesJson": '[{"id":"egg"}]',
    }
    monkeypatch.setattr(main, "_get_firestore_profile_snapshot", lambda uid: (fake_raw, main._cloud_profile_summary(uid, fake_raw)))

    ops_admin = {
        "uid": "ops-admin-1",
        "actor": "ops-admin@example.com",
        "roles": ["ops_admin"],
    }
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    try:
        with TestClient(main.app) as client:
            response = client.get("/ops/users/bundle-user/support-bundle")
            assert response.status_code == 200
            body = response.json()
            assert body["uid"] == "bundle-user"
            assert body["profileSummary"]["profile"]["displayName"] == "Bundle User"
            assert len(body["recentPlanJobs"]) == 2
            assert body["planJobCounts"]["done"] == 1
            assert body["planJobCounts"]["dead-letter"] == 1
            assert len(body["supportCases"]) == 1
            assert body["supportCases"][0]["userUid"] == "bundle-user"
            assert body["supportCases"][0]["relatedJobId"] == "support-job-2"
            assert body["supportCases"][0]["assignee"] == "incident-lead@example.com"
            assert body["supportCases"][0]["escalated"] is True
            assert any(item["action"] == "user_profile.inspect" for item in body["supportAuditTrail"])
            assert "profileDocument" not in body

            raw_response = client.get("/ops/users/bundle-user/support-bundle", params={"include_raw_profile": "true"})
            assert raw_response.status_code == 200
            assert raw_response.json()["profileDocument"]["displayName"] == "Bundle User"

            audit_resp = client.get(
                "/admin/audit/logs",
                params={"resource_type": "user_support_bundle", "resource_id": "bundle-user"},
            )
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "user_support_bundle.inspect" in actions
    finally:
        main.app.dependency_overrides = {}
