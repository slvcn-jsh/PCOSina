import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_ops_user_cloud_profile_returns_summary_and_optional_raw_document(monkeypatch):
    fake_raw = {
        "displayName": "Cloud User",
        "goal": "Weight Management",
        "activityLevel": "Lightly Active",
        "isProfileCompleted": True,
        "updatedAtEpochMs": 1111,
        "artifactsUpdatedAtEpochMs": 2222,
        "pantryEntriesJson": '[{"id":"egg"},{"id":"rice"}]',
        "groceryJson": '[{"id":"ginger"}]',
        "feedbackQueueJson": '[{"id":"fb-1"}]',
        "weeklyJournalMap": {"2026-03-16": "solid week"},
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
            summary_resp = client.get("/ops/users/user-123/cloud-profile")
            assert summary_resp.status_code == 200
            summary_body = summary_resp.json()
            assert summary_body["summary"]["uid"] == "user-123"
            assert summary_body["summary"]["profile"]["displayName"] == "Cloud User"
            assert summary_body["summary"]["artifacts"]["pantryEntriesJson"]["count"] == 2
            assert summary_body["summary"]["weeklyJournalCount"] == 1
            assert "document" not in summary_body

            raw_resp = client.get("/ops/users/user-123/cloud-profile", params={"include_raw": "true"})
            assert raw_resp.status_code == 200
            raw_body = raw_resp.json()
            assert raw_body["document"]["displayName"] == "Cloud User"

            audit_resp = client.get("/admin/audit/logs", params={"resource_type": "user_profile"})
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "user_profile.inspect" in actions
    finally:
        main.app.dependency_overrides = {}
