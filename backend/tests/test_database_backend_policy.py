import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database
from db_url import is_postgres_database_url
import main
import policy_store


def test_postgres_database_url_detection_is_scheme_based():
    assert is_postgres_database_url("postgres://user:pass@example.com/db")
    assert is_postgres_database_url("postgresql://user:pass@example.com/db")

    assert not is_postgres_database_url("")
    assert not is_postgres_database_url("sqlite:///pcosina.db")
    assert not is_postgres_database_url("postgresqlite://pcosina.db")
    assert not is_postgres_database_url("postgres-local-file")


def test_database_modules_disable_sqlite_fallback_in_production(monkeypatch):
    monkeypatch.setenv("PCOSINA_ENV", "production")
    monkeypatch.setattr(database, "DATABASE_URL", "postgresqlite://not-postgres")
    monkeypatch.setattr(policy_store, "DATABASE_URL", "postgresqlite://not-postgres")

    assert database.db_mode() == "sqlite"
    assert policy_store.db_mode() == "sqlite"
    with pytest.raises(RuntimeError, match="Production requires a Postgres DATABASE_URL"):
        database._connect()
    with pytest.raises(RuntimeError, match="Production requires a Postgres DATABASE_URL"):
        policy_store._connect()


def test_runtime_readiness_rejects_invalid_database_url_scheme(monkeypatch):
    monkeypatch.setattr(main, "IS_PRODUCTION", True)
    monkeypatch.setattr(main, "ENVIRONMENT", "production")
    monkeypatch.setattr(main, "ASYNC_MODE", "background")
    monkeypatch.setattr(main, "RATE_LIMIT_BACKEND", "redis")
    monkeypatch.setattr(main, "sentry_dsn", "https://dsn.example")
    monkeypatch.setattr(main, "_app_check_enforced", lambda: True)
    monkeypatch.setattr(main, "_operator_require_verified_email", lambda: True)
    monkeypatch.setattr(main, "_operator_require_mfa_for_admin_access", lambda: True)
    monkeypatch.setattr(main, "_operator_require_recent_auth_for_admin_session", lambda: True)
    monkeypatch.setattr(main, "_admin_session_idle_timeout_seconds", lambda: 1800)
    monkeypatch.setattr(main, "_admin_max_active_sessions_per_uid", lambda: 3)
    monkeypatch.setattr(main, "_firebase_credentials_configured", lambda: True)
    monkeypatch.setattr(main, "_uid_hash_salt_configured_for_production", lambda: True)
    monkeypatch.setattr(main, "QUEUE_BROKER", type("Broker", (), {"backend": "db", "health": lambda self: {"backend": "db"}})())
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "prod-secret")
    monkeypatch.setenv("FIREBASE_AUTH_DISABLED", "false")
    monkeypatch.setenv("DATABASE_URL", "postgresqlite://not-postgres")
    monkeypatch.setenv("PCOSINA_ALLOWED_HOSTS", "pcosina.example.com")
    monkeypatch.setenv("PCOSINA_REDIS_URL", "redis://cache.example")

    report = main._runtime_readiness_report()

    assert report["ok"] is False
    assert any("Postgres DATABASE_URL" in item for item in report["errors"])


def test_runtime_readiness_accepts_postgresql_database_url_scheme(monkeypatch):
    monkeypatch.setattr(main, "IS_PRODUCTION", True)
    monkeypatch.setattr(main, "ENVIRONMENT", "production")
    monkeypatch.setattr(main, "ASYNC_MODE", "background")
    monkeypatch.setattr(main, "RATE_LIMIT_BACKEND", "redis")
    monkeypatch.setattr(main, "sentry_dsn", "https://dsn.example")
    monkeypatch.setattr(main, "_app_check_enforced", lambda: True)
    monkeypatch.setattr(main, "_operator_require_verified_email", lambda: True)
    monkeypatch.setattr(main, "_operator_require_mfa_for_admin_access", lambda: True)
    monkeypatch.setattr(main, "_operator_require_recent_auth_for_admin_session", lambda: True)
    monkeypatch.setattr(main, "_admin_session_idle_timeout_seconds", lambda: 1800)
    monkeypatch.setattr(main, "_admin_max_active_sessions_per_uid", lambda: 3)
    monkeypatch.setattr(main, "_firebase_credentials_configured", lambda: True)
    monkeypatch.setattr(main, "_uid_hash_salt_configured_for_production", lambda: True)
    monkeypatch.setattr(main, "QUEUE_BROKER", type("Broker", (), {"backend": "db", "health": lambda self: {"backend": "db"}})())
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "prod-secret")
    monkeypatch.setenv("FIREBASE_AUTH_DISABLED", "false")
    monkeypatch.setenv("DATABASE_URL", "postgresql://db.example/pcosina")
    monkeypatch.setenv("PCOSINA_ALLOWED_HOSTS", "pcosina.example.com")
    monkeypatch.setenv("PCOSINA_REDIS_URL", "redis://cache.example")

    report = main._runtime_readiness_report()

    assert not any("Postgres DATABASE_URL" in item for item in report["errors"])
