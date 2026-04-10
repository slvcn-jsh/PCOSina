import sys
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database
import main


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_ops_admin_session_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"ops_admin_session_api_{uuid4().hex}.db"


def test_ops_can_list_and_revoke_admin_sessions(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")

    _token, session_principal = main._issue_admin_session(
        {
            "uid": "ops-admin-1",
            "email": "ops-admin@example.com",
            "roles": ["ops_admin"],
            "actor": "ops-admin@example.com",
            "emailVerified": True,
            "authType": "bearer",
        }
    )

    ops_admin = {
        "uid": "ops-admin-2",
        "actor": "ops-admin2@example.com",
        "roles": ["ops_admin"],
    }
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin

    try:
        with TestClient(main.app) as client:
            list_resp = client.get("/ops/admin-sessions", params={"uid": "ops-admin-1"})
            assert list_resp.status_code == 200
            items = list_resp.json()["items"]
            assert len(items) == 1
            assert items[0]["id"] == session_principal["sessionId"]
            assert items[0]["revokedAt"] is None

            revoke_resp = client.post(
                f"/ops/admin-sessions/{session_principal['sessionId']}/revoke",
                json={"reason": "manual_review"},
            )
            assert revoke_resp.status_code == 200
            revoked = revoke_resp.json()
            assert revoked["id"] == session_principal["sessionId"]
            assert revoked["revokedAt"] is not None
            assert revoked["revokeReason"] == "manual_review"

            inactive_list = client.get("/ops/admin-sessions", params={"uid": "ops-admin-1", "active_only": "false"})
            assert inactive_list.status_code == 200
            assert inactive_list.json()["items"][0]["revokedAt"] is not None

            audit_resp = client.get(
                "/admin/audit/logs",
                params={"resource_type": "admin_session", "resource_id": session_principal["sessionId"]},
            )
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "admin_session.revoke" in actions
    finally:
        main.app.dependency_overrides = {}


def test_ops_can_bulk_revoke_admin_sessions_for_uid(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")

    _, current_session = main._issue_admin_session(
        {
            "uid": "ops-admin-bulk",
            "email": "ops-bulk@example.com",
            "roles": ["ops_admin"],
            "actor": "ops-bulk@example.com",
            "emailVerified": True,
            "authType": "bearer",
        }
    )
    _, second_session = main._issue_admin_session(
        {
            "uid": "ops-admin-bulk",
            "email": "ops-bulk@example.com",
            "roles": ["ops_admin"],
            "actor": "ops-bulk@example.com",
            "emailVerified": True,
            "authType": "bearer",
        }
    )

    ops_admin = {
        "uid": "ops-admin-bulk",
        "actor": "ops-bulk@example.com",
        "roles": ["ops_admin"],
        "sessionId": current_session["sessionId"],
    }
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin

    try:
        with TestClient(main.app) as client:
            response = client.post(
                "/ops/admin-sessions/revoke-user/ops-admin-bulk",
                json={"reason": "compromise_response", "excludeCurrentSession": True},
            )
            assert response.status_code == 200
            body = response.json()
            assert body["uid"] == "ops-admin-bulk"
            assert body["count"] == 2
            assert body["revokedCount"] == 1

            sessions = {item["id"]: item for item in body["items"]}
            assert sessions[current_session["sessionId"]]["revokedAt"] is None
            assert sessions[second_session["sessionId"]]["revokedAt"] is not None

            audit_resp = client.get(
                "/admin/audit/logs",
                params={"resource_type": "admin_session", "resource_id": "ops-admin-bulk"},
            )
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "admin_session.revoke_user" in actions
    finally:
        main.app.dependency_overrides = {}


def test_ops_can_cleanup_stale_admin_sessions(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    now_ms = 1_800_000_000_000
    old_active = database.register_admin_session(
        session_id="expired-old",
        uid="ops-admin-cleanup",
        email="cleanup@example.com",
        actor="cleanup@example.com",
        roles=["ops_admin"],
        auth_type="session",
        created_at=now_ms - (40 * 24 * 60 * 60 * 1000),
        expires_at=now_ms - (35 * 24 * 60 * 60 * 1000),
    )
    old_revoked = database.register_admin_session(
        session_id="revoked-old",
        uid="ops-admin-cleanup",
        email="cleanup@example.com",
        actor="cleanup@example.com",
        roles=["ops_admin"],
        auth_type="session",
        created_at=now_ms - (40 * 24 * 60 * 60 * 1000),
        expires_at=now_ms + (5 * 24 * 60 * 60 * 1000),
    )
    database.revoke_admin_session(old_revoked["id"], revoked_by="ops-admin@example.com", reason="stale_cleanup_test")
    kept_session = database.register_admin_session(
        session_id="fresh-active",
        uid="ops-admin-cleanup",
        email="cleanup@example.com",
        actor="cleanup@example.com",
        roles=["ops_admin"],
        auth_type="session",
        created_at=now_ms - (2 * 24 * 60 * 60 * 1000),
        expires_at=now_ms + (2 * 24 * 60 * 60 * 1000),
    )

    ops_admin = {
        "uid": "ops-admin-cleaner",
        "actor": "ops-cleaner@example.com",
        "roles": ["ops_admin"],
    }
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin

    original_time = main.time.time
    original_db_time = database.time.time
    main.time.time = lambda: now_ms / 1000.0
    database.time.time = lambda: now_ms / 1000.0
    try:
        with TestClient(main.app) as client:
            response = client.post(
                "/ops/admin-sessions/cleanup",
                json={"retentionDays": 30, "includeRevoked": True, "includeExpired": True},
            )
            assert response.status_code == 200
            body = response.json()
            assert body["deletedCount"] == 2

            remaining = database.list_admin_sessions(uid="ops-admin-cleanup", active_only=False, limit=10)
            remaining_ids = {item["id"] for item in remaining}
            assert old_active["id"] not in remaining_ids
            assert old_revoked["id"] not in remaining_ids
            assert kept_session["id"] in remaining_ids

            audit_resp = client.get(
                "/admin/audit/logs",
                params={"resource_type": "admin_session", "resource_id": "cleanup"},
            )
            assert audit_resp.status_code == 200
            actions = [item["action"] for item in audit_resp.json()["items"]]
            assert "admin_session.cleanup" in actions
    finally:
        main.time.time = original_time
        database.time.time = original_db_time
        main.app.dependency_overrides = {}
