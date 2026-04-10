import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_root_page_does_not_leak_admin_token(monkeypatch):
    with TestClient(main.app) as client:
        response = client.get("/")

    assert response.status_code == 200
    body = response.text
    assert "super-secret-token" not in body
    assert "/admin/login" in body


def test_admin_feedback_escapes_message_content(monkeypatch):
    monkeypatch.setattr(
        main.database,
        "get_recent_feedback",
        lambda limit=50, order="desc": [
            {"id": 1, "created_at": "2026-03-19T10:00:00Z", "message": "<script>alert('x')</script>"},
        ],
    )
    principal = {
        "uid": "admin-1",
        "actor": "admin@example.com",
        "roles": ["admin", "feedback_admin"],
        "nonce": "nonce-1",
        "authType": "session",
    }
    main.app.dependency_overrides[main.require_feedback_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            response = client.get("/admin/feedback")
        assert response.status_code == 200
        body = response.text
        assert "<script>alert('x')</script>" not in body
        assert "&lt;script&gt;alert" in body
        assert "csrf_token" in body
    finally:
        main.app.dependency_overrides = {}


def test_admin_feedback_shows_cross_console_links_for_multi_role_admin(monkeypatch):
    monkeypatch.setattr(main.database, "get_recent_feedback", lambda limit=50, order="desc": [])
    principal = {
        "uid": "admin-2",
        "actor": "multi-admin@example.com",
        "roles": ["feedback_admin", "content_admin", "ops_admin"],
        "nonce": "nonce-2",
        "authType": "session",
    }
    main.app.dependency_overrides[main.require_feedback_admin] = lambda: principal

    try:
        with TestClient(main.app) as client:
            response = client.get("/admin/feedback")

        assert response.status_code == 200
        assert "Switch console" in response.text
        assert "/admin/feedback" in response.text
        assert "/admin/content" in response.text
        assert "/admin/ops" in response.text
    finally:
        main.app.dependency_overrides = {}


def test_policy_admin_endpoint_rejects_legacy_admin_token_header(monkeypatch):
    monkeypatch.setenv("PCOSINA_ADMIN_CONFIG_TOKEN", "legacy-token")

    with TestClient(main.app) as client:
        response = client.get("/admin/policy/active", headers={"X-Admin-Token": "legacy-token"})

    assert response.status_code == 401
