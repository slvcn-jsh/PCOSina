import json
import sys
import uuid
from pathlib import Path

from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import main


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_admin_policy_console_ui"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"admin_policy_console_ui_{uuid.uuid4().hex}.db"


def _policy_admin_principal() -> dict[str, object]:
    return {
        "uid": "policy-admin-ui-1",
        "actor": "policy-admin-ui@example.com",
        "roles": ["policy_admin"],
        "nonce": "policy-console-nonce",
        "authType": "session",
    }


def test_admin_login_page_links_to_policy_console(monkeypatch):
    principal = _policy_admin_principal()
    monkeypatch.setattr(main, "_principal_from_session_token", lambda token: principal)

    with TestClient(main.app) as client:
        response = client.get("/admin/login")

    assert response.status_code == 200
    assert "/admin/policy" in response.text
    assert "Planner Settings" in response.text


def test_policy_console_shows_switch_console_links_for_multi_role_admin(monkeypatch):
    db_path = _temp_db_path()
    monkeypatch.setattr(main.policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(main.policy_store, "DB_NAME", str(db_path))
    main.policy_store.init_policy_store()
    main.policy_store.ensure_default_policy(actor="policy-ui")

    principal = {
        **_policy_admin_principal(),
        "roles": ["policy_admin", "content_admin", "ops_admin", "feedback_admin"],
    }
    main.app.dependency_overrides[main.require_policy_admin] = lambda: principal
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    try:
        with TestClient(main.app) as client:
            response = client.get("/admin/policy")

        assert response.status_code == 200
        assert "Tools" in response.text
        assert "/admin/policy" in response.text
        assert "/admin/content" in response.text
        assert "/admin/ops" in response.text
        assert "/admin/feedback" in response.text
    finally:
        main.app.dependency_overrides = {}


def test_policy_console_html_create_activate_and_rollback(monkeypatch):
    db_path = _temp_db_path()
    monkeypatch.setattr(main.policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(main.policy_store, "DB_NAME", str(db_path))
    main.policy_store.init_policy_store()
    main.policy_store.ensure_default_policy(actor="policy-ui-bootstrap")
    main._load_runtime_policy(force_refresh=True)

    principal = _policy_admin_principal()
    main.app.dependency_overrides[main.require_policy_admin] = lambda: principal
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    try:
        with TestClient(main.app) as client:
            page = client.get("/admin/policy")
            assert page.status_code == 200
            assert "Policy Console" in page.text
            assert "Create Policy Version" in page.text
            assert "Recent Policy Audit" in page.text

            active = main.policy_store.get_active_policy()
            assert active is not None
            new_policy = json.loads(json.dumps(active["policy"]))
            new_policy["policy_name"] = "ui-policy-version"
            new_policy["stage1"]["max_candidates_per_slot"] = 77

            create_token = main._build_admin_csrf_token(principal, "policy-create")
            create = client.post(
                "/admin/policy/create",
                data={
                    "csrf_token": create_token,
                    "notes": "ui-created-version",
                    "policy_json": json.dumps(new_policy, indent=2, sort_keys=True),
                },
                follow_redirects=False,
            )
            assert create.status_code == 303
            assert "notice=policy_saved" in create.headers["location"]

            versions = main.policy_store.list_policy_versions(limit=10)
            created = next(item for item in versions if (item.get("policy") or {}).get("policy_name") == "ui-policy-version")
            created_id = created["id"]
            assert created["is_active"] is False

            selected = client.get("/admin/policy", params={"source_policy_id": created_id})
            assert selected.status_code == 200
            assert "Loaded from version" in selected.text
            assert "ui-policy-version" in selected.text

            activate_token = main._build_admin_csrf_token(principal, "policy-activate")
            activate = client.post(
                "/admin/policy/activate-form",
                data={
                    "csrf_token": activate_token,
                    "policy_id": created_id,
                    "notes": "ui-activate",
                },
                follow_redirects=False,
            )
            assert activate.status_code == 303
            assert "notice=policy_activated" in activate.headers["location"]

            active_after_activate = main.policy_store.get_active_policy()
            assert active_after_activate is not None
            assert active_after_activate["id"] == created_id

            rollback_token = main._build_admin_csrf_token(principal, "policy-rollback")
            rollback = client.post(
                "/admin/policy/rollback-form",
                data={
                    "csrf_token": rollback_token,
                    "notes": "ui-rollback",
                },
                follow_redirects=False,
            )
            assert rollback.status_code == 303
            assert "notice=policy_rolled_back" in rollback.headers["location"]

            active_after_rollback = main.policy_store.get_active_policy()
            assert active_after_rollback is not None
            assert active_after_rollback["id"] != created_id
            assert active_after_rollback.get("rollback_of") == created_id
    finally:
        main.app.dependency_overrides = {}


def test_policy_console_rejects_invalid_json(monkeypatch):
    db_path = _temp_db_path()
    monkeypatch.setattr(main.policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(main.policy_store, "DB_NAME", str(db_path))
    main.policy_store.init_policy_store()
    main.policy_store.ensure_default_policy(actor="policy-ui-bootstrap")

    principal = _policy_admin_principal()
    main.app.dependency_overrides[main.require_policy_admin] = lambda: principal
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    try:
        with TestClient(main.app) as client:
            create_token = main._build_admin_csrf_token(principal, "policy-create")
            response = client.post(
                "/admin/policy/create",
                data={
                    "csrf_token": create_token,
                    "notes": "bad-json",
                    "policy_json": "{not-valid-json}",
                },
                follow_redirects=False,
            )

        assert response.status_code == 303
        assert "error=Invalid+policy+JSON" in response.headers["location"]
    finally:
        main.app.dependency_overrides = {}
