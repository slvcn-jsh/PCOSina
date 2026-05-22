import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import pytest
from fastapi.testclient import TestClient

import main


@pytest.fixture(autouse=True)
def _disable_feedback_cleanup(monkeypatch):
    monkeypatch.setattr(main.database, "cleanup_feedback", lambda retention_days=365: 0, raising=False)


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
        assert "Tools" in response.text
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


def test_public_feedback_rejects_empty_message(monkeypatch):
    saved = []
    monkeypatch.setattr(main.database, "save_feedback", lambda message: saved.append(message))

    with TestClient(main.app) as client:
        response = client.post(
            "/feedback",
            json={"message": "   "},
            headers={"X-PCOSINA-Schema-Version": "1.2.0"},
        )

    assert response.status_code == 400
    assert saved == []


def test_public_feedback_requires_app_check_when_enforced(monkeypatch):
    monkeypatch.setenv("PCOSINA_ENFORCE_APP_CHECK", "true")
    monkeypatch.setattr(main.firebase_admin, "_apps", [object()])
    monkeypatch.setattr(main.app_check, "verify_token", lambda token: {"app_id": "pcosina-test"})
    saved = []
    monkeypatch.setattr(main.database, "save_feedback", lambda message: saved.append(message))

    with TestClient(main.app) as client:
        missing = client.post(
            "/feedback",
            json={"message": "valid"},
            headers={"X-PCOSINA-Schema-Version": "1.2.0"},
        )
        allowed = client.post(
            "/feedback",
            json={"message": "valid"},
            headers={
                "X-PCOSINA-Schema-Version": "1.2.0",
                "X-Firebase-AppCheck": "app-check-token",
            },
        )

    assert missing.status_code == 401
    assert allowed.status_code == 200
    assert saved == ["valid"]


def test_public_feedback_uses_feedback_specific_quota(monkeypatch):
    saved = []
    monkeypatch.setattr(main.database, "save_feedback", lambda message: saved.append(message))
    monkeypatch.setattr(main, "_feedback_rate_limit_allowed", lambda request: False)

    with TestClient(main.app) as client:
        response = client.post(
            "/feedback",
            json={"message": "valid"},
            headers={"X-PCOSINA-Schema-Version": "1.2.0"},
        )

    assert response.status_code == 429
    assert saved == []


def test_public_feedback_rejects_oversized_message(monkeypatch):
    saved = []
    monkeypatch.setattr(main.database, "save_feedback", lambda message: saved.append(message))

    with TestClient(main.app) as client:
        response = client.post(
            "/feedback",
            json={"message": "x" * 2001},
            headers={"X-PCOSINA-Schema-Version": "1.2.0"},
        )

    assert response.status_code == 422
    assert saved == []


def test_public_feedback_rejects_malformed_payload(monkeypatch):
    saved = []
    monkeypatch.setattr(main.database, "save_feedback", lambda message: saved.append(message))

    with TestClient(main.app) as client:
        response = client.post(
            "/feedback",
            json={"body": "missing message field"},
            headers={"X-PCOSINA-Schema-Version": "1.2.0"},
        )

    assert response.status_code == 422
    assert saved == []


def test_public_feedback_rejects_extra_fields(monkeypatch):
    saved = []
    monkeypatch.setattr(main.database, "save_feedback", lambda message: saved.append(message))

    with TestClient(main.app) as client:
        response = client.post(
            "/feedback",
            json={"message": "valid", "uid": "leaked-user-id"},
            headers={"X-PCOSINA-Schema-Version": "1.2.0"},
        )

    assert response.status_code == 422
    assert saved == []


def test_public_feedback_strips_message_before_save(monkeypatch):
    saved = []
    monkeypatch.setattr(main.database, "save_feedback", lambda message: saved.append(message))

    with TestClient(main.app) as client:
        response = client.post(
            "/feedback",
            json={"message": "  Helpful note  "},
            headers={"X-PCOSINA-Schema-Version": "1.2.0"},
        )

    assert response.status_code == 200
    assert saved == ["Helpful note"]
