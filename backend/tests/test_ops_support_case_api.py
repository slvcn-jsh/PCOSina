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
    base = Path(__file__).resolve().parent / ".tmp_ops_support_case_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"ops_support_case_api_{uuid4().hex}.db"


def _request_json(display_name: str = "SupportUser") -> str:
    return json.dumps(
        {
            "profile": {
                "displayName": display_name,
                "age": 24,
                "heightCm": 161,
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
    )


def test_ops_support_case_crud_notes_and_audit_trail():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    database.create_plan_job("support-job-1", request_json=_request_json(), owner_uid="support-user")
    database.update_plan_job("support-job-1", status="dead-letter", error="solver timeout", worker_id="worker-z")

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
                    "userUid": "support-user",
                    "relatedJobId": "support-job-1",
                    "summary": "User reported repeated dead-letter planning failures",
                    "priority": "high",
                    "assignee": "tier2-ops@example.com",
                    "initialNote": "Investigating optimizer retries",
                },
            )
            assert create_resp.status_code == 200
            created = create_resp.json()
            case_id = created["id"]
            assert created["status"] == "open"
            assert created["priority"] == "high"
            assert created["assignee"] == "tier2-ops@example.com"
            assert created["escalated"] is False
            assert created["relatedJobId"] == "support-job-1"
            assert len(created["notes"]) == 1
            assert created["notes"][0]["author"] == "ops-admin@example.com"

            list_resp = client.get("/ops/support-cases", params={"user_uid": "support-user", "q": "dead-letter"})
            assert list_resp.status_code == 200
            items = list_resp.json()["items"]
            assert any(item["id"] == case_id for item in items)

            get_resp = client.get(f"/ops/support-cases/{case_id}")
            assert get_resp.status_code == 200
            assert get_resp.json()["summary"].startswith("User reported")

            update_resp = client.patch(
                f"/ops/support-cases/{case_id}",
                json={
                    "status": "investigating",
                    "priority": "urgent",
                    "assignee": "incident-lead@example.com",
                    "escalated": True,
                    "summary": "Investigating repeated optimizer failures",
                },
            )
            assert update_resp.status_code == 200
            updated = update_resp.json()
            assert updated["status"] == "investigating"
            assert updated["priority"] == "urgent"
            assert updated["assignee"] == "incident-lead@example.com"
            assert updated["escalated"] is True
            assert updated["updatedBy"] == "ops-admin@example.com"

            filtered_resp = client.get(
                "/ops/support-cases",
                params={"assignee": "incident-lead@example.com", "escalated": "true"},
            )
            assert filtered_resp.status_code == 200
            filtered_items = filtered_resp.json()["items"]
            assert len(filtered_items) == 1
            assert filtered_items[0]["id"] == case_id

            note_resp = client.post(
                f"/ops/support-cases/{case_id}/notes",
                json={"message": "Requested the latest support bundle from the operator console"},
            )
            assert note_resp.status_code == 200
            noted = note_resp.json()
            assert len(noted["notes"]) == 2
            assert noted["notes"][-1]["message"].startswith("Requested the latest support bundle")

            clear_resp = client.patch(
                f"/ops/support-cases/{case_id}",
                json={"clearAssignee": True, "escalated": False},
            )
            assert clear_resp.status_code == 200
            cleared = clear_resp.json()
            assert cleared["assignee"] is None
            assert cleared["escalated"] is False

            audit_resp = client.get(
                "/admin/audit/logs",
                params={"resource_type": "support_case", "resource_id": case_id},
            )
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "support_case.create" in actions
            assert "support_case.update" in actions
            assert "support_case.note" in actions
    finally:
        main.app.dependency_overrides = {}


def test_ops_support_case_create_rejects_missing_related_job():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    ops_admin = {
        "uid": "ops-admin-1",
        "actor": "ops-admin@example.com",
        "roles": ["ops_admin"],
    }
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin

    try:
        with TestClient(main.app) as client:
            response = client.post(
                "/ops/support-cases",
                json={
                    "userUid": "support-user",
                    "relatedJobId": "missing-job",
                    "summary": "Missing related job should be rejected",
                },
            )
            assert response.status_code == 404
            assert response.json()["detail"] == "Related plan job not found"
    finally:
        main.app.dependency_overrides = {}
