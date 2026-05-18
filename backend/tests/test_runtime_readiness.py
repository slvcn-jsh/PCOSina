import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_seed_nutrition_corrections_on_startup_defaults_on_outside_pytest(monkeypatch):
    monkeypatch.delenv("PCOSINA_SEED_NUTRITION_CORRECTIONS", raising=False)
    monkeypatch.delenv("PYTEST_CURRENT_TEST", raising=False)

    assert main._seed_nutrition_corrections_on_startup() is True


def test_seed_nutrition_corrections_on_startup_skips_pytest_by_default(monkeypatch):
    monkeypatch.delenv("PCOSINA_SEED_NUTRITION_CORRECTIONS", raising=False)
    monkeypatch.setenv("PYTEST_CURRENT_TEST", "backend/tests/test_runtime_readiness.py::test")

    assert main._seed_nutrition_corrections_on_startup() is False

    monkeypatch.setenv("PCOSINA_SEED_NUTRITION_CORRECTIONS", "true")

    assert main._seed_nutrition_corrections_on_startup() is True


def test_runtime_readiness_reports_production_errors(monkeypatch):
    monkeypatch.setattr(main, "IS_PRODUCTION", True)
    monkeypatch.setattr(main, "ENVIRONMENT", "production")
    monkeypatch.setattr(main, "ASYNC_MODE", "queued")
    monkeypatch.setattr(main, "sentry_dsn", "")
    monkeypatch.setenv("RENDER_GIT_COMMIT", "2049bafe2711ad36378531288bf4f7c2879d4f18")
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "")
    monkeypatch.setenv("FIREBASE_AUTH_DISABLED", "true")
    monkeypatch.setenv("DATABASE_URL", "")
    monkeypatch.setenv("PCOSINA_ALLOWED_HOSTS", "*")
    monkeypatch.setenv("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL", "false")
    monkeypatch.setenv("PCOSINA_REQUIRE_OPERATOR_MFA", "false")
    monkeypatch.setenv("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", "false")
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS", "0")
    monkeypatch.setenv("PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID", "0")
    monkeypatch.setenv("FIREBASE_SERVICE_ACCOUNT_JSON", "")
    monkeypatch.setenv("FIREBASE_CREDENTIALS_PATH", "missing-service-account.json")
    monkeypatch.setattr(main, "QUEUE_BROKER", type("MemoryBroker", (), {"backend": "memory", "health": lambda self: {"backend": "memory"}})())

    report = main._runtime_readiness_report()

    assert report["ok"] is False
    assert report["release"]["gitCommitShort"] == "2049baf"
    assert any("PCOSINA_ADMIN_SESSION_SECRET" in item for item in report["errors"])
    assert any("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL" in item for item in report["errors"])
    assert any("PCOSINA_REQUIRE_OPERATOR_MFA" in item for item in report["errors"])
    assert any("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH" in item for item in report["errors"])
    assert any("PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS" in item for item in report["errors"])
    assert any("PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID" in item for item in report["errors"])
    assert any("FIREBASE_AUTH_DISABLED" in item for item in report["errors"])
    assert any("Postgres DATABASE_URL" in item for item in report["errors"])
    assert any("wildcard" in item for item in report["errors"])
    assert any("memory is not allowed" in item for item in report["errors"])
    assert any("PCOSINA_UID_HASH_SALT" in item for item in report["errors"])
    assert any("SENTRY_DSN" in item for item in report["warnings"])


def test_health_ready_returns_503_when_not_ready():
    main.app.dependency_overrides = {}
    original = main._runtime_readiness_report
    original_validate = main._validate_runtime_readiness
    original_rate_limit_allowed = main._rate_limit_allowed
    try:
        main._runtime_readiness_report = lambda **kwargs: {
            "environment": "production",
            "asyncMode": "queued",
            "queueBackend": "memory",
            "errors": ["misconfigured"],
            "warnings": [],
            "ok": False,
        }
        main._validate_runtime_readiness = lambda **kwargs: None
        main._rate_limit_allowed = lambda ip, now=None: True
        with TestClient(main.app) as client:
            response = client.get("/health/ready")
        assert response.status_code == 503
        assert response.json()["errors"] == ["misconfigured"]
    finally:
        main._runtime_readiness_report = original
        main._validate_runtime_readiness = original_validate
        main._rate_limit_allowed = original_rate_limit_allowed


def test_runtime_readiness_includes_schema_status_and_flags_pending(monkeypatch):
    monkeypatch.setattr(main, "IS_PRODUCTION", False)
    monkeypatch.setattr(main, "ENVIRONMENT", "development")
    monkeypatch.setattr(main, "_schema_readiness_report", lambda: {
        "ok": False,
        "application": {"pending": ["20260319_app_999_test"]},
        "policy": {"pending": []},
        "pending": ["20260319_app_999_test"],
    })

    report = main._runtime_readiness_report(include_schema=True)

    assert report["ok"] is False
    assert report["schemaMigrations"]["pending"] == ["20260319_app_999_test"]
    assert any("Pending schema migrations detected" in item for item in report["errors"])


def test_db_status_hides_database_details_in_production(monkeypatch):
    monkeypatch.setattr(main, "IS_PRODUCTION", True)
    monkeypatch.setattr(main, "_validate_runtime_readiness", lambda **_: None)
    monkeypatch.setattr(main, "init_firebase", lambda: None)

    with TestClient(main.app) as client:
        response = client.get("/db-status")

    assert response.status_code == 200
    body = response.json()
    assert body == {"status": "restricted"}
    assert "db_module" not in body
    assert "sample" not in body
