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
    base = Path(__file__).resolve().parent / ".tmp_admin_operator_auth_policy"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"admin_operator_auth_policy_{uuid4().hex}.db"


def test_admin_session_rejects_stale_firebase_auth(monkeypatch):
    monkeypatch.setenv("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", "true")
    monkeypatch.setenv("PCOSINA_ADMIN_MAX_AUTH_AGE_SECONDS", "300")
    monkeypatch.setattr(
        main,
        "_verify_firebase_id_token",
        lambda token: {
            "uid": "ops-admin-1",
            "email": "ops-admin@example.com",
            "email_verified": True,
            "pcosina_roles": ["ops_admin"],
            "auth_time": int(time.time()) - 3600,
        },
    )

    with TestClient(main.app) as client:
        response = client.post("/admin/session", data={"id_token": "stale-token"})

    assert response.status_code == 403
    assert "recent Firebase sign-in" in response.json()["detail"]


def test_admin_session_rejects_missing_mfa_when_required(monkeypatch):
    monkeypatch.setenv("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", "false")
    monkeypatch.setenv("PCOSINA_REQUIRE_OPERATOR_MFA", "true")
    monkeypatch.setattr(
        main,
        "_verify_firebase_id_token",
        lambda token: {
            "uid": "ops-admin-mfa-1",
            "email": "ops-admin-mfa@example.com",
            "email_verified": True,
            "pcosina_roles": ["ops_admin"],
            "auth_time": int(time.time()) - 30,
        },
    )

    with TestClient(main.app) as client:
        response = client.post("/admin/session", data={"id_token": "no-mfa-token"})

    assert response.status_code == 403
    assert "MFA-verified" in response.json()["detail"]


def test_admin_session_accepts_recent_firebase_auth(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", "true")
    monkeypatch.setenv("PCOSINA_REQUIRE_OPERATOR_MFA", "true")
    monkeypatch.setenv("PCOSINA_ADMIN_MAX_AUTH_AGE_SECONDS", "300")
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")
    monkeypatch.setattr(
        main,
        "_verify_firebase_id_token",
        lambda token: {
            "uid": "ops-admin-1",
            "email": "ops-admin@example.com",
            "email_verified": True,
            "pcosina_roles": ["ops_admin"],
            "auth_time": int(time.time()) - 30,
            "firebase": {"sign_in_second_factor": "phone"},
        },
    )

    with TestClient(main.app) as client:
        response = client.post("/admin/session", data={"id_token": "fresh-token"}, follow_redirects=False)

    assert response.status_code == 303
    assert response.headers["location"] == "/admin/feedback"
    assert main.ADMIN_SESSION_COOKIE in response.headers.get("set-cookie", "")
    actions = database.list_admin_action_logs(resource_type="admin_session")
    assert any(item["action"] == "admin_session.create" for item in actions)


def test_bearer_admin_access_rejects_missing_mfa_when_required(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_REQUIRE_OPERATOR_MFA", "true")
    monkeypatch.setattr(
        main,
        "_verify_firebase_id_token",
        lambda token: {
            "uid": "content-admin-no-mfa",
            "email": "content-admin@example.com",
            "email_verified": True,
            "pcosina_roles": ["content_admin"],
            "auth_time": int(time.time()) - 30,
        },
    )

    with TestClient(main.app) as client:
        response = client.get("/admin/recipes", headers={"Authorization": "Bearer no-mfa"})

    assert response.status_code == 403
    assert "MFA-verified" in response.json()["detail"]


def test_admin_logout_writes_audit_event(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", "false")
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")
    monkeypatch.setattr(
        main,
        "_verify_firebase_id_token",
        lambda token: {
            "uid": "ops-admin-2",
            "email": "ops-admin2@example.com",
            "email_verified": True,
            "pcosina_roles": ["ops_admin"],
            "auth_time": int(time.time()) - 30,
        },
    )

    with TestClient(main.app) as client:
        create_response = client.post("/admin/session", data={"id_token": "fresh-token"}, follow_redirects=False)
        assert create_response.status_code == 303
        logout_response = client.post("/admin/logout", follow_redirects=False)

    assert logout_response.status_code == 303
    actions = database.list_admin_action_logs(resource_type="admin_session")
    action_names = [item["action"] for item in actions]
    assert "admin_session.create" in action_names
    assert "admin_session.logout" in action_names
    active_sessions = database.list_admin_sessions(uid="ops-admin-2", active_only=True)
    assert active_sessions == []


def test_revoked_admin_session_cookie_no_longer_authenticates(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", "false")
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")
    monkeypatch.setattr(
        main,
        "_verify_firebase_id_token",
        lambda token: {
            "uid": "ops-admin-3",
            "email": "ops-admin3@example.com",
            "email_verified": True,
            "pcosina_roles": ["feedback_admin"],
            "auth_time": int(time.time()) - 30,
        },
    )

    with TestClient(main.app) as client:
        create_response = client.post("/admin/session", data={"id_token": "fresh-token"}, follow_redirects=False)
        assert create_response.status_code == 303
        session_record = database.list_admin_sessions(uid="ops-admin-3", active_only=True)[0]
        database.revoke_admin_session(session_record["id"], revoked_by="ops-admin@example.com", reason="manual_test")
        page_response = client.get("/admin/login")

    assert page_response.status_code == 200
    assert "Create admin session" in page_response.text


def test_idle_timed_out_admin_session_cookie_no_longer_authenticates(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", "false")
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS", "60")
    monkeypatch.setattr(
        main,
        "_verify_firebase_id_token",
        lambda token: {
            "uid": "ops-admin-4",
            "email": "ops-admin4@example.com",
            "email_verified": True,
            "pcosina_roles": ["feedback_admin"],
            "auth_time": int(time.time()) - 30,
        },
    )

    with TestClient(main.app) as client:
        create_response = client.post("/admin/session", data={"id_token": "fresh-token"}, follow_redirects=False)
        assert create_response.status_code == 303
        session_record = database.list_admin_sessions(uid="ops-admin-4", active_only=True)[0]
        original_main_time = main.time.time
        original_db_time = database.time.time
        try:
            advanced_seconds = (session_record["lastSeenAt"] / 1000.0) + 120.0
            main.time.time = lambda: advanced_seconds
            database.time.time = lambda: advanced_seconds
            page_response = client.get("/admin/login")
        finally:
            main.time.time = original_main_time
            database.time.time = original_db_time

    assert page_response.status_code == 200
    assert "Create admin session" in page_response.text
    stale_record = database.get_admin_session(session_record["id"], include_revoked=True)
    assert stale_record is not None
    assert stale_record["revokeReason"] == "idle_timeout"


def test_admin_session_limit_revokes_oldest_sessions_on_issue(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")
    monkeypatch.setenv("PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID", "2")

    original_main_time = main.time.time
    original_db_time = database.time.time
    clock = {"now": 1_800_000_000.0}
    shared_time = lambda: clock["now"]
    main.time.time = shared_time
    database.time.time = shared_time
    try:
        clock["now"] = 1_800_000_000.0
        _, first_session = main._issue_admin_session(
            {
                "uid": "ops-admin-limit",
                "email": "ops-limit@example.com",
                "roles": ["ops_admin"],
                "actor": "ops-limit@example.com",
                "emailVerified": True,
                "authType": "bearer",
            }
        )
        clock["now"] = 1_800_000_001.0
        _, second_session = main._issue_admin_session(
            {
                "uid": "ops-admin-limit",
                "email": "ops-limit@example.com",
                "roles": ["ops_admin"],
                "actor": "ops-limit@example.com",
                "emailVerified": True,
                "authType": "bearer",
            }
        )
        clock["now"] = 1_800_000_002.0
        _, third_session = main._issue_admin_session(
            {
                "uid": "ops-admin-limit",
                "email": "ops-limit@example.com",
                "roles": ["ops_admin"],
                "actor": "ops-limit@example.com",
                "emailVerified": True,
                "authType": "bearer",
            }
        )
    finally:
        main.time.time = original_main_time
        database.time.time = original_db_time

    active_sessions = database.list_admin_sessions(uid="ops-admin-limit", active_only=True, limit=10)
    assert {item["id"] for item in active_sessions} == {second_session["sessionId"], third_session["sessionId"]}

    first_record = database.get_admin_session(first_session["sessionId"], include_revoked=True)
    assert first_record is not None
    assert first_record["revokedAt"] is not None
    assert first_record["revokeReason"] == "session_limit"

    actions = database.list_admin_action_logs(resource_type="admin_session", resource_id="ops-admin-limit")
    assert any(item["action"] == "admin_session.limit_revoke" for item in actions)
