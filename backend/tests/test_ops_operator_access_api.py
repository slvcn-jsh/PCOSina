import sys
import time
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database
import main


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_ops_operator_access_api"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"ops_operator_access_api_{uuid4().hex}.db"


def test_ops_can_block_operator_and_revoke_active_sessions(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    _token, session_principal = main._issue_admin_session(
        {
            "uid": "blocked-operator-1",
            "email": "blocked-operator@example.com",
            "roles": ["ops_admin"],
            "actor": "blocked-operator@example.com",
            "emailVerified": True,
            "mfaVerified": True,
            "authType": "bearer",
        }
    )

    ops_admin = {
        "uid": "ops-admin-1",
        "actor": "ops-admin@example.com",
        "roles": ["ops_admin"],
    }
    main.app.dependency_overrides[main.require_ops_admin] = lambda: ops_admin

    try:
        with TestClient(main.app) as client:
            response = client.put(
                "/ops/operator-access/blocked-operator-1",
                json={
                    "email": "blocked-operator@example.com",
                    "blocked": True,
                    "reason": "offboarded",
                    "revokeActiveSessions": True,
                },
            )
            assert response.status_code == 200
            body = response.json()
            assert body["item"]["uid"] == "blocked-operator-1"
            assert body["item"]["blocked"] is True
            assert body["item"]["reason"] == "offboarded"
            assert body["revokedSessionCount"] == 1

            list_response = client.get("/ops/operator-access", params={"blocked_only": "true"})
            assert list_response.status_code == 200
            assert any(item["uid"] == "blocked-operator-1" for item in list_response.json()["items"])

            session_record = database.get_admin_session(session_principal["sessionId"], include_revoked=True)
            assert session_record is not None
            assert session_record["revokedAt"] is not None
            assert session_record["revokeReason"] == "operator_access_override"

            audit_response = client.get(
                "/admin/audit/logs",
                params={"resource_type": "operator_access", "resource_id": "blocked-operator-1"},
            )
            assert audit_response.status_code == 200
            actions = [item["action"] for item in audit_response.json()["items"]]
            assert "operator_access.upsert" in actions
    finally:
        main.app.dependency_overrides = {}


def test_blocked_operator_cannot_create_new_admin_session(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    database.upsert_operator_access_override(
        "blocked-operator-2",
        email="blocked-two@example.com",
        blocked=True,
        reason="compromised_account",
        updated_by="ops-admin@example.com",
    )
    monkeypatch.setattr(
        main,
        "_verify_firebase_id_token",
        lambda token: {
            "uid": "blocked-operator-2",
            "email": "blocked-two@example.com",
            "email_verified": True,
            "pcosina_roles": ["ops_admin"],
            "auth_time": int(time.time()) - 30,
            "firebase": {"sign_in_second_factor": "phone"},
        },
    )

    with TestClient(main.app) as client:
        response = client.post("/admin/session", data={"id_token": "blocked-token"})

    assert response.status_code == 403
    assert "server-side override" in response.json()["detail"]


def test_blocked_operator_existing_cookie_is_revoked_on_next_request(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    token, session_principal = main._issue_admin_session(
        {
            "uid": "blocked-operator-3",
            "email": "blocked-three@example.com",
            "roles": ["feedback_admin"],
            "actor": "blocked-three@example.com",
            "emailVerified": True,
            "mfaVerified": True,
            "authType": "bearer",
        }
    )
    database.upsert_operator_access_override(
        "blocked-operator-3",
        email="blocked-three@example.com",
        blocked=True,
        reason="manual_disable",
        updated_by="ops-admin@example.com",
    )

    with TestClient(main.app) as client:
        client.cookies.set(main.ADMIN_SESSION_COOKIE, token, path="/admin")
        response = client.get("/admin/login")

    assert response.status_code == 200
    assert "Create admin session" in response.text
    blocked_record = database.get_admin_session(session_principal["sessionId"], include_revoked=True)
    assert blocked_record is not None
    assert blocked_record["revokeReason"] == "operator_access_blocked"
