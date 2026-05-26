import sys
import sqlite3
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


def test_postgres_connect_uses_connection_pool_when_available(monkeypatch):
    class FakeRawConnection:
        closed = False

        def __init__(self):
            self.rollbacks = 0

        def rollback(self):
            self.rollbacks += 1

    class FakePool:
        instances = []

        def __init__(self, conninfo, min_size, max_size, timeout, open):
            self.conninfo = conninfo
            self.min_size = min_size
            self.max_size = max_size
            self.timeout = timeout
            self.open = open
            self.raw = FakeRawConnection()
            self.returned = []
            self.closed = False
            FakePool.instances.append(self)

        def getconn(self):
            return self.raw

        def putconn(self, conn):
            self.returned.append(conn)

        def close(self):
            self.closed = True

    monkeypatch.setattr(database, "DATABASE_URL", "postgresql://db.example/pcosina")
    monkeypatch.setattr(database, "psycopg", object())
    monkeypatch.setattr(database, "ConnectionPool", FakePool)
    monkeypatch.setenv("PCOSINA_DB_POOL_ENABLED", "true")
    monkeypatch.setenv("PCOSINA_DB_POOL_MIN_SIZE", "2")
    monkeypatch.setenv("PCOSINA_DB_POOL_MAX_SIZE", "4")
    monkeypatch.setenv("PCOSINA_DB_POOL_TIMEOUT_SECONDS", "9")
    database._close_postgres_pool()

    conn = database._connect()
    conn.close()

    pool = FakePool.instances[-1]
    assert pool.conninfo == "postgresql://db.example/pcosina"
    assert pool.min_size == 2
    assert pool.max_size == 4
    assert pool.timeout == 9
    assert pool.returned == [pool.raw]
    assert pool.raw.rollbacks == 1
    status = database.get_database_connection_pool_status()
    assert status["enabled"] is True
    assert status["driverAvailable"] is True
    assert status["open"] is True

    database._close_postgres_pool()
    assert pool.closed is True


def test_sqlite_admin_content_schema_enforces_bounds(tmp_path, monkeypatch):
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(tmp_path / "constraints.db"))
    database.init_db()
    conn = database._connect()
    try:
        cur = conn.cursor()
        with pytest.raises(sqlite3.IntegrityError):
            cur.execute(
                """
                INSERT INTO recipes (id, title, meal_type, calories, protein, carbs, fats, fiber, minutes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ("bad_recipe", "Bad Recipe", "Breakfast", -1, 10, 20, 5, 3, 15),
            )
        with pytest.raises(sqlite3.IntegrityError):
            cur.execute(
                """
                INSERT INTO ingredient_price_rules (id, keywords_json, price_php, category, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                ("bad_price", '["rice"]', 0, "Dry Goods", 1, 1),
            )
        with pytest.raises(sqlite3.IntegrityError):
            cur.execute(
                """
                INSERT INTO recipe_nutrition_corrections (id, recipe_id, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """,
                ("bad_correction", "recipe_1", 1, 1),
            )
    finally:
        conn.close()


def test_postgres_check_constraint_helper_uses_typed_catalog_query():
    class FakeCursor:
        def __init__(self):
            self.executed = []

        def execute(self, sql, params=None):
            self.executed.append((str(sql).strip(), params))

        def fetchone(self):
            return None

    cur = FakeCursor()

    database._add_postgres_check_constraint(
        cur,
        table_name="recipes",
        constraint_name="recipes_calories_bounds",
        expression="calories BETWEEN 1 AND 3000",
    )

    assert len(cur.executed) == 2
    lookup_sql, lookup_params = cur.executed[0]
    assert "DO $$" not in lookup_sql
    assert "conname = %s::name" in lookup_sql
    assert "conrelid = %s::regclass" in lookup_sql
    assert lookup_params == ("recipes_calories_bounds", "recipes")
    alter_sql, alter_params = cur.executed[1]
    assert 'ALTER TABLE "recipes"' in alter_sql
    assert 'ADD CONSTRAINT "recipes_calories_bounds"' in alter_sql
    assert "CHECK (calories BETWEEN 1 AND 3000) NOT VALID" in alter_sql
    assert alter_params is None


def test_postgres_check_constraint_helper_skips_existing_constraint():
    class FakeCursor:
        def __init__(self):
            self.executed = []

        def execute(self, sql, params=None):
            self.executed.append((str(sql).strip(), params))

        def fetchone(self):
            return (1,)

    cur = FakeCursor()

    database._add_postgres_check_constraint(
        cur,
        table_name="recipes",
        constraint_name="recipes_calories_bounds",
        expression="calories BETWEEN 1 AND 3000",
    )

    assert len(cur.executed) == 1


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
