import json
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
    base = Path(__file__).resolve().parent / ".tmp_admin_ops_console_ui"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"admin_ops_console_ui_{uuid4().hex}.db"


def _ops_admin_principal() -> dict[str, object]:
    return {
        "uid": "ops-admin-ui-1",
        "actor": "ops-admin-ui@example.com",
        "roles": ["ops_admin"],
        "nonce": "ops-console-nonce",
        "authType": "session",
    }


def _request_json(display_name: str = "OpsConsoleUser") -> str:
    return json.dumps(
        {
            "profile": {
                "displayName": display_name,
                "age": 26,
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


def test_admin_login_page_links_to_ops_console(monkeypatch):
    principal = _ops_admin_principal()
    monkeypatch.setattr(main, "_principal_from_session_token", lambda token: principal)

    with TestClient(main.app) as client:
        response = client.get("/admin/login")

    assert response.status_code == 200
    assert "/admin/ops" in response.text
    assert "Ops Console" in response.text


def test_ops_console_shows_switch_console_links_for_multi_role_admin():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    principal = {
        **_ops_admin_principal(),
        "roles": ["ops_admin", "content_admin", "feedback_admin"],
    }
    main.app.dependency_overrides[main.require_ops_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            response = client.get("/admin/ops")

        assert response.status_code == 200
        assert "Switch console" in response.text
        assert "/admin/content" in response.text
        assert "/admin/feedback" in response.text
        assert "/admin/ops" in response.text
        assert "/admin/ops/audit-logs" in response.text
    finally:
        main.app.dependency_overrides = {}


def test_ops_console_renders_panel_health_when_one_dataset_fails(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    principal = _ops_admin_principal()
    main.app.dependency_overrides[main.require_ops_admin] = lambda: principal

    def fail_support_cases(*_args, **_kwargs):
        raise RuntimeError("support table unavailable")

    monkeypatch.setattr(database, "list_support_cases", fail_support_cases)

    try:
        with TestClient(main.app) as client:
            response = client.get("/admin/ops")

        assert response.status_code == 200
        assert "Ops Workflow" in response.text
        assert "Some Ops data could not be loaded." in response.text
        assert "Support cases is temporarily unavailable" in response.text
        assert "/admin/ops/admin-sessions" in response.text
        assert "Panel Health" in response.text
    finally:
        main.app.dependency_overrides = {}


def test_ops_support_case_console_stays_usable_when_queue_query_fails(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()

    principal = _ops_admin_principal()
    main.app.dependency_overrides[main.require_ops_admin] = lambda: principal

    def fail_support_cases(*_args, **_kwargs):
        raise RuntimeError("support table unavailable")

    monkeypatch.setattr(database, "list_support_cases", fail_support_cases)

    try:
        with TestClient(main.app) as client:
            response = client.get("/admin/ops/support-cases")

        assert response.status_code == 200
        assert "Support Case Console" in response.text
        assert "Support case queue is temporarily unavailable" in response.text
        assert "Create Support Case" in response.text
        assert "<select name=\"status\">" in response.text
    finally:
        main.app.dependency_overrides = {}


def test_ops_support_case_console_html_crud_and_escapes_summary():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    database.create_plan_job("ops-ui-job-1", request_json=_request_json(), owner_uid="ops-ui-user")
    database.update_plan_job("ops-ui-job-1", status="dead-letter", error="solver stalled", worker_id="worker-ui")

    principal = _ops_admin_principal()
    main.app.dependency_overrides[main.require_ops_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            page = client.get("/admin/ops/support-cases")
            assert page.status_code == 200
            assert "Support Case Console" in page.text
            assert "Create Support Case" in page.text

            create_token = main._build_admin_csrf_token(principal, "ops-support-case-create")
            create = client.post(
                "/admin/ops/support-cases/create",
                data={
                    "csrf_token": create_token,
                    "user_uid": "ops-ui-user",
                    "related_job_id": "ops-ui-job-1",
                    "summary": "Investigating <b>planner</b> failures",
                    "priority": "high",
                    "assignee": "tier2-ui@example.com",
                    "escalated": "true",
                    "initial_note": "Collected <script>alert(1)</script> trace details",
                },
                follow_redirects=False,
            )
            assert create.status_code == 303
            assert "notice=support_case_created" in create.headers["location"]

            items = database.list_support_cases(user_uid="ops-ui-user", limit=10)
            assert len(items) == 1
            case_id = items[0]["id"]

            update_token = main._build_admin_csrf_token(principal, "ops-support-case-update")
            update = client.post(
                f"/admin/ops/support-cases/{case_id}/update",
                data={
                    "csrf_token": update_token,
                    "summary": "Investigating <b>planner</b> failures across saved weeks",
                    "status": "investigating",
                    "priority": "urgent",
                    "assignee": "incident-ui@example.com",
                    "escalated": "true",
                },
                follow_redirects=False,
            )
            assert update.status_code == 303

            note_token = main._build_admin_csrf_token(principal, "ops-support-case-note")
            note = client.post(
                f"/admin/ops/support-cases/{case_id}/notes",
                data={
                    "csrf_token": note_token,
                    "message": "Retried with <script>alert(2)</script> saved-plan continuity enabled",
                },
                follow_redirects=False,
            )
            assert note.status_code == 303

            saved = database.get_support_case(case_id)
            assert saved is not None
            assert saved["status"] == "investigating"
            assert saved["priority"] == "urgent"
            assert saved["assignee"] == "incident-ui@example.com"
            assert saved["escalated"] is True
            assert len(saved["notes"]) == 2

            selected = client.get("/admin/ops/support-cases", params={"edit_case_id": case_id})
            assert selected.status_code == 200
            assert "Support Case Queue" in selected.text
            assert "<b>planner</b>" not in selected.text
            assert "&lt;b&gt;planner&lt;/b&gt;" in selected.text
            assert "<script>alert(2)</script>" not in selected.text
            assert "&lt;script&gt;alert(2)&lt;/script&gt;" in selected.text
    finally:
        main.app.dependency_overrides = {}


def test_ops_admin_sessions_console_html_revoke_and_cleanup(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")

    now_ms = 1_800_000_000_000
    _, live_session = main._issue_admin_session(
        {
            "uid": "ops-session-target",
            "email": "ops-session-target@example.com",
            "roles": ["ops_admin"],
            "actor": "ops-session-target@example.com",
            "emailVerified": True,
            "authType": "bearer",
        }
    )
    old_expired = database.register_admin_session(
        session_id="ops-expired-old",
        uid="ops-cleanup-target",
        email="ops-cleanup@example.com",
        actor="ops-cleanup@example.com",
        roles=["ops_admin"],
        auth_type="session",
        created_at=now_ms - (45 * 24 * 60 * 60 * 1000),
        expires_at=now_ms - (40 * 24 * 60 * 60 * 1000),
    )
    old_revoked = database.register_admin_session(
        session_id="ops-revoked-old",
        uid="ops-cleanup-target",
        email="ops-cleanup@example.com",
        actor="ops-cleanup@example.com",
        roles=["ops_admin"],
        auth_type="session",
        created_at=now_ms - (45 * 24 * 60 * 60 * 1000),
        expires_at=now_ms + (5 * 24 * 60 * 60 * 1000),
    )
    database.revoke_admin_session(old_revoked["id"], revoked_by="ops-admin-ui@example.com", reason="stale_cleanup_test")

    principal = _ops_admin_principal()
    main.app.dependency_overrides[main.require_ops_admin] = lambda: principal

    original_main_time = main.time.time
    original_db_time = database.time.time
    main.time.time = lambda: now_ms / 1000.0
    database.time.time = lambda: now_ms / 1000.0
    try:
        with TestClient(main.app) as client:
            page = client.get("/admin/ops/admin-sessions")
            assert page.status_code == 200
            assert "Admin Session Console" in page.text
            assert "Admin Session Maintenance" in page.text

            revoke_token = main._build_admin_csrf_token(principal, "ops-admin-session-revoke")
            revoke = client.post(
                f"/admin/ops/admin-sessions/{live_session['sessionId']}/revoke",
                data={
                    "csrf_token": revoke_token,
                    "uid": "ops-session-target",
                    "active_only": "false",
                    "reason": "manual_review",
                },
                follow_redirects=False,
            )
            assert revoke.status_code == 303
            assert "notice=admin_session_revoked" in revoke.headers["location"]

            revoked = database.get_admin_session(live_session["sessionId"], include_revoked=True)
            assert revoked is not None
            assert revoked["revokedAt"] is not None
            assert revoked["revokeReason"] == "manual_review"

            cleanup_token = main._build_admin_csrf_token(principal, "ops-admin-session-cleanup")
            cleanup = client.post(
                "/admin/ops/admin-sessions/cleanup",
                data={
                    "csrf_token": cleanup_token,
                    "retention_days": "30",
                    "include_revoked": "true",
                    "include_expired": "true",
                },
                follow_redirects=False,
            )
            assert cleanup.status_code == 303
            assert "notice=admin_sessions_cleaned" in cleanup.headers["location"]

            remaining = database.list_admin_sessions(uid="ops-cleanup-target", active_only=False, limit=10)
            remaining_ids = {item["id"] for item in remaining}
            assert old_expired["id"] not in remaining_ids
            assert old_revoked["id"] not in remaining_ids
    finally:
        main.time.time = original_main_time
        database.time.time = original_db_time
        main.app.dependency_overrides = {}


def test_ops_operator_access_console_html_save_revokes_sessions_and_escapes_reason(monkeypatch):
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "test-admin-session-secret")

    _, target_session = main._issue_admin_session(
        {
            "uid": "blocked-ui-operator",
            "email": "blocked-ui-operator@example.com",
            "roles": ["ops_admin"],
            "actor": "blocked-ui-operator@example.com",
            "emailVerified": True,
            "mfaVerified": True,
            "authType": "bearer",
        }
    )

    principal = _ops_admin_principal()
    main.app.dependency_overrides[main.require_ops_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            page = client.get("/admin/ops/operator-access")
            assert page.status_code == 200
            assert "Operator Access Console" in page.text
            assert "Operator Access Override" in page.text

            save_token = main._build_admin_csrf_token(principal, "ops-operator-access-save")
            save = client.post(
                "/admin/ops/operator-access/save",
                data={
                    "csrf_token": save_token,
                    "uid": "blocked-ui-operator",
                    "email": "blocked-ui-operator@example.com",
                    "blocked": "true",
                    "reason": "<b>offboarded</b>",
                    "revoke_active_sessions": "true",
                },
                follow_redirects=False,
            )
            assert save.status_code == 303
            assert "notice=operator_access_saved" in save.headers["location"]

            saved = database.get_operator_access_override("blocked-ui-operator")
            assert saved is not None
            assert saved["blocked"] is True
            assert saved["reason"] == "<b>offboarded</b>"

            session_record = database.get_admin_session(target_session["sessionId"], include_revoked=True)
            assert session_record is not None
            assert session_record["revokedAt"] is not None
            assert session_record["revokeReason"] == "operator_access_override"

            selected = client.get("/admin/ops/operator-access", params={"edit_uid": "blocked-ui-operator"})
            assert selected.status_code == 200
            assert "<b>offboarded</b>" not in selected.text
            assert "&lt;b&gt;offboarded&lt;/b&gt;" in selected.text
    finally:
        main.app.dependency_overrides = {}


def test_ops_audit_log_console_filters_events():
    db_path = _temp_db_path()
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)
    database.init_db()
    database.log_admin_action(
        "recipe.create",
        actor="content-admin-ui@example.com",
        resource_type="recipe",
        resource_id="recipe-ui-1",
        details={"title": "Audit Tinola"},
    )
    database.log_admin_action(
        "operator_access.upsert",
        actor="ops-admin-ui@example.com",
        resource_type="operator_access",
        resource_id="blocked-ui-1",
        details={"blocked": True},
    )

    principal = _ops_admin_principal()
    main.app.dependency_overrides[main.require_ops_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            page = client.get("/admin/ops/audit-logs", params={"resource_type": "operator_access"})

        assert page.status_code == 200
        assert "Audit Log Console" in page.text
        assert "Audit Log Filters" in page.text
        assert "operator_access.upsert" in page.text
        assert "blocked-ui-1" in page.text
        assert "recipe-ui-1" not in page.text
    finally:
        main.app.dependency_overrides = {}
