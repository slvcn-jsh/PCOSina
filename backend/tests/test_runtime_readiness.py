import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_database_bootstrap_on_startup_can_be_disabled_for_predeploy(monkeypatch):
    monkeypatch.delenv("PCOSINA_BOOTSTRAP_ON_STARTUP", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    monkeypatch.delenv("RENDER", raising=False)
    monkeypatch.setattr(main, "IS_PRODUCTION", False)
    assert main._bootstrap_database_on_startup() is True

    monkeypatch.setattr(main, "IS_PRODUCTION", True)
    assert main._bootstrap_database_on_startup() is False

    monkeypatch.setenv("PCOSINA_BOOTSTRAP_ON_STARTUP", "false")
    assert main._bootstrap_database_on_startup() is False


def test_database_bootstrap_on_startup_defaults_off_for_postgres_even_without_production_env(monkeypatch):
    monkeypatch.delenv("PCOSINA_BOOTSTRAP_ON_STARTUP", raising=False)
    monkeypatch.setenv("DATABASE_URL", "postgres://pcosina:secret@example.render.com/pcosina")
    monkeypatch.setattr(main, "IS_PRODUCTION", False)

    assert main._bootstrap_database_on_startup() is False


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


def test_seed_reviewed_price_rules_on_startup_uses_same_pytest_default_policy(monkeypatch):
    monkeypatch.delenv("PCOSINA_SEED_REVIEWED_PRICE_RULES", raising=False)
    monkeypatch.setenv("PYTEST_CURRENT_TEST", "backend/tests/test_runtime_readiness.py::test")

    assert main._seed_reviewed_price_rules_on_startup() is False

    monkeypatch.setenv("PCOSINA_SEED_REVIEWED_PRICE_RULES", "true")

    assert main._seed_reviewed_price_rules_on_startup() is True


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


def test_health_ready_defaults_to_shallow_process_readiness_outside_production(monkeypatch):
    main.app.dependency_overrides = {}
    monkeypatch.delenv("PCOSINA_HEALTH_READY_DEEP", raising=False)
    monkeypatch.setattr(main, "IS_PRODUCTION", False)
    monkeypatch.setattr(main, "_validate_runtime_readiness", lambda **kwargs: None)
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    def fail_if_schema_requested(**kwargs):
        assert kwargs.get("include_schema") is False
        return {
            "environment": "development",
            "asyncMode": "queued",
            "queueBackend": "db",
            "errors": [],
            "warnings": [],
            "ok": True,
        }

    monkeypatch.setattr(main, "_runtime_readiness_report", fail_if_schema_requested)

    with TestClient(main.app) as client:
        response = client.get("/health/ready")

    assert response.status_code == 200
    body = response.json()
    assert body["readinessMode"] == "shallow"
    assert body["dependencyChecks"] == "skipped"


def test_health_ready_defaults_to_deep_dependency_readiness_in_production(monkeypatch):
    main.app.dependency_overrides = {}
    monkeypatch.delenv("PCOSINA_HEALTH_READY_DEEP", raising=False)
    monkeypatch.setattr(main, "IS_PRODUCTION", True)
    monkeypatch.setattr(main, "_validate_runtime_readiness", lambda **kwargs: None)
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)
    monkeypatch.setattr(main, "init_firebase", lambda: None)

    def report(**kwargs):
        assert kwargs.get("include_schema") is True
        return {
            "environment": "production",
            "asyncMode": "queued",
            "queueBackend": "redis",
            "errors": ["db unavailable"],
            "warnings": [],
            "ok": False,
        }

    monkeypatch.setattr(main, "_runtime_readiness_report", report)

    with TestClient(main.app) as client:
        response = client.get("/health/ready")

    assert response.status_code == 503
    body = response.json()
    assert body["readinessMode"] == "deep"
    assert body["dependencyChecks"] == "enabled"


def test_health_ready_defaults_to_deep_dependency_readiness_on_render_postgres(monkeypatch):
    monkeypatch.setenv("RENDER", "true")
    monkeypatch.setenv("DATABASE_URL", "postgres://pcosina:secret@db.internal/pcosina")
    monkeypatch.setattr(main, "IS_PRODUCTION", False)
    monkeypatch.setattr(main, "IS_MANAGED_POSTGRES_RUNTIME", True)
    monkeypatch.delenv("PCOSINA_HEALTH_READY_DEEP", raising=False)

    assert main._deep_readiness_enabled() is True


def test_runtime_readiness_deep_reports_database_connectivity_failure(monkeypatch):
    monkeypatch.setattr(main, "IS_PRODUCTION", False)
    monkeypatch.setattr(main, "ENVIRONMENT", "development")
    monkeypatch.setattr(main.database, "check_database_connectivity", lambda: {
        "ok": False,
        "mode": "postgres",
        "latencyMs": 12,
        "error": "failed to resolve host 'db.internal'",
    })
    monkeypatch.setattr(main, "_schema_readiness_report", lambda: {
        "ok": True,
        "application": {"pending": []},
        "policy": {"pending": []},
        "pending": [],
    })
    monkeypatch.setattr(main.database, "get_recipe_catalog_nutrition_status", lambda: {
        "ok": True,
        "errors": [],
        "warnings": [],
    })

    report = main._runtime_readiness_report(include_schema=True)

    assert report["ok"] is False
    assert report["database"]["connectivity"]["ok"] is False
    assert any("Database connectivity check failed" in item for item in report["errors"])


def test_runtime_schema_bootstrap_needed_when_schema_probe_fails(monkeypatch):
    monkeypatch.setattr(main, "_schema_readiness_report", lambda: (_ for _ in ()).throw(RuntimeError("missing table")))

    assert main._runtime_schema_bootstrap_needed() is True


def test_runtime_schema_bootstrap_needed_when_migrations_pending(monkeypatch):
    monkeypatch.setattr(main, "_schema_readiness_report", lambda: {
        "ok": False,
        "pending": ["20260623_missing"],
    })

    assert main._runtime_schema_bootstrap_needed() is True


def test_runtime_policy_load_bootstraps_missing_policy_table(monkeypatch):
    monkeypatch.setattr(main, "ENVIRONMENT", "staging")
    main._policy_cache.clear()
    calls = []
    policy = main.default_policy().to_runtime_dict(environment="staging")

    def missing_policy_table_once():
        calls.append("get_active")
        if len(calls) == 1:
            raise RuntimeError('relation "policy_versions" does not exist')
        return {
            "id": "policy-test",
            "version_number": 1,
            "policy": policy,
        }

    monkeypatch.setattr(main.policy_store, "get_active_policy", missing_policy_table_once)
    monkeypatch.setattr(main.policy_store, "init_policy_store", lambda: calls.append("init_policy_store"))
    monkeypatch.setattr(
        main.policy_store,
        "ensure_default_policy",
        lambda actor: {
            "id": "policy-test",
            "version_number": 1,
            "policy": policy,
        },
    )

    policy_payload, version = main._load_runtime_policy(force_refresh=True)

    assert policy_payload
    assert version == "policy-v1:policy-test"
    assert calls == ["get_active", "init_policy_store"]


def test_health_deep_runs_dependency_readiness(monkeypatch):
    main.app.dependency_overrides = {}
    monkeypatch.setattr(main, "_validate_runtime_readiness", lambda **kwargs: None)
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    def report(**kwargs):
        assert kwargs.get("include_schema") is True
        return {
            "environment": "development",
            "asyncMode": "queued",
            "queueBackend": "db",
            "errors": ["db unavailable"],
            "warnings": [],
            "ok": False,
        }

    monkeypatch.setattr(main, "_runtime_readiness_report", report)

    with TestClient(main.app) as client:
        response = client.get("/health/deep")

    assert response.status_code == 503
    assert response.json()["readinessMode"] == "deep"


def test_root_head_probe_succeeds(monkeypatch):
    monkeypatch.setattr(main, "_validate_runtime_readiness", lambda **kwargs: None)
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    with TestClient(main.app) as client:
        response = client.head("/")

    assert response.status_code == 200


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
