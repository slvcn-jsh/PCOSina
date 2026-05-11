import sys
from pathlib import Path

from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import main


def test_mobile_operator_access_accepts_allowlisted_email_without_claims(monkeypatch):
    monkeypatch.setenv("PCOSINA_OPS_ADMIN_EMAILS", "ops-mobile@example.com")
    monkeypatch.setenv("PCOSINA_REQUIRE_OPERATOR_MFA", "true")
    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {
        "uid": "ops-mobile-1",
        "email": "ops-mobile@example.com",
        "email_verified": True,
    }

    try:
        with TestClient(main.app) as client:
            response = client.get("/mobile/operator/access")
    finally:
        main.app.dependency_overrides = {}

    assert response.status_code == 200
    body = response.json()
    assert body["allowed"] is True
    assert "ops_admin" in body["roles"]
    assert body["emailVerified"] is True
    assert body["authType"] == "bearer"


def test_mobile_operator_access_rejects_non_operator_account(monkeypatch):
    monkeypatch.setenv("PCOSINA_REQUIRE_OPERATOR_MFA", "true")
    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {
        "uid": "normal-user-1",
        "email": "normal@example.com",
        "email_verified": True,
    }

    try:
        with TestClient(main.app) as client:
            response = client.get("/mobile/operator/access")
    finally:
        main.app.dependency_overrides = {}

    assert response.status_code == 403
    assert "Admin role required" in response.json()["detail"]
