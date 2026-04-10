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
    base = Path(__file__).resolve().parent / ".tmp_ops_support_case_export_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"ops_support_case_export_api_{uuid4().hex}.db"


def _request_json(display_name: str = "ExportUser") -> str:
    return json.dumps(
        {
            "profile": {
                "displayName": display_name,
                "age": 28,
                "heightCm": 162,
                "weightKg": 62,
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


def test_ops_support_case_export_packages_case_profile_jobs_and_audit(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    database.create_plan_job("export-job-1", request_json=_request_json(), owner_uid="export-user")
    database.update_plan_job("export-job-1", status="dead-letter", error="solver timeout", worker_id="worker-export")
    database.log_admin_action(
        "user_profile.inspect",
        actor="ops-admin@example.com",
        resource_type="user_profile",
        resource_id="export-user",
        details={"includeRaw": False},
    )

    fake_raw = {
        "displayName": "Export User",
        "goal": "General Health",
        "activityLevel": "Lightly Active",
        "isProfileCompleted": True,
        "updatedAtEpochMs": 1000,
    }
    monkeypatch.setattr(main, "_get_firestore_profile_snapshot", lambda uid: (fake_raw, main._cloud_profile_summary(uid, fake_raw)))

    ops_admin = {
        "uid": "ops-admin-1",
        "actor": "ops-admin@example.com",
        "roles": ["ops_admin"],
    }
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin

    try:
        with TestClient(main.app) as client:
            create_resp = client.post(
                "/ops/support-cases",
                json={
                    "userUid": "export-user",
                    "relatedJobId": "export-job-1",
                    "summary": "Operator investigating repeated export job failures",
                    "priority": "high",
                    "assignee": "incident-lead@example.com",
                    "escalated": True,
                    "initialNote": "Collecting incident data",
                },
            )
            assert create_resp.status_code == 200
            support_case = create_resp.json()

            response = client.get(f"/ops/support-cases/{support_case['id']}/export")
            assert response.status_code == 200
            body = response.json()
            assert body["case"]["id"] == support_case["id"]
            assert body["case"]["assignee"] == "incident-lead@example.com"
            assert body["case"]["escalated"] is True
            assert body["profileSummary"]["profile"]["displayName"] == "Export User"
            assert body["relatedJob"]["id"] == "export-job-1"
            assert any(item["id"] == "export-job-1" for item in body["recentPlanJobs"])
            actions = [item["action"] for item in body["auditTrail"]]
            assert "support_case.create" in actions
            assert "user_profile.inspect" in actions
            assert "profileDocument" not in body

            raw_response = client.get(
                f"/ops/support-cases/{support_case['id']}/export",
                params={"include_raw_profile": "true"},
            )
            assert raw_response.status_code == 200
            assert raw_response.json()["profileDocument"]["displayName"] == "Export User"

            audit_resp = client.get(
                "/admin/audit/logs",
                params={"resource_type": "support_case", "resource_id": support_case["id"]},
            )
            assert audit_resp.status_code == 200
            audit_actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "support_case.export" in audit_actions
    finally:
        main.app.dependency_overrides = {}
