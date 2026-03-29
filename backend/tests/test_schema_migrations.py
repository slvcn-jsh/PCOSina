import sys
from pathlib import Path
from uuid import uuid4
from unittest.mock import ANY

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import database
import main
import policy_store


def _temp_db_path(prefix: str) -> Path:
    base = Path(__file__).resolve().parent / ".tmp_schema_migrations"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"{prefix}_{uuid4().hex}.db"


def test_app_schema_migrations_are_tracked_and_idempotent():
    db_path = _temp_db_path("app_schema")
    database.DATABASE_URL = ""
    database.DB_NAME = str(db_path)

    database.init_db()
    first = database.get_schema_migration_status()
    assert first["registered"] >= 4
    assert first["applied"] == first["registered"]
    assert first["pending"] == []

    database.init_db()
    second = database.get_schema_migration_status()
    assert second["applied"] == first["applied"]
    assert second["pending"] == []


def test_policy_schema_migrations_are_tracked_and_idempotent(monkeypatch):
    db_path = _temp_db_path("policy_schema")
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(db_path))

    policy_store.init_policy_store()
    first = policy_store.get_schema_migration_status()
    assert first["registered"] >= 2
    assert first["applied"] == first["registered"]
    assert first["pending"] == []

    policy_store.init_policy_store()
    second = policy_store.get_schema_migration_status()
    assert second["applied"] == first["applied"]
    assert second["pending"] == []


def test_ops_schema_migrations_endpoint_returns_app_and_policy_status(monkeypatch):
    main.app.dependency_overrides[main.require_ops_admin] = lambda: {"actor": "ops-admin", "roles": ["ops_admin"]}
    monkeypatch.setattr(main.database, "get_schema_migration_status", lambda: {"scope": "app", "registered": 4, "applied": 4, "pending": [], "items": []})
    monkeypatch.setattr(main.policy_store, "get_schema_migration_status", lambda: {"scope": "policy", "registered": 2, "applied": 2, "pending": [], "items": []})
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)

    try:
        with TestClient(main.app) as client:
            response = client.get("/ops/schema/migrations")
        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "ok"
        assert body["app"]["scope"] == "app"
        assert body["policy"]["scope"] == "policy"
    finally:
        main.app.dependency_overrides = {}


class _FakeCursor:
    def __init__(self, owner):
        self.owner = owner

    def execute(self, sql, params=None):
        self.owner.executed.append((str(sql).strip(), params))


class _FakeConn:
    def __init__(self):
        self.executed = []
        self.commits = 0
        self.closed = False

    def cursor(self, *args, **kwargs):
        return _FakeCursor(self)

    def commit(self):
        self.commits += 1

    def close(self):
        self.closed = True


def test_init_db_uses_postgres_bootstrap_lock(monkeypatch):
    fake = _FakeConn()
    monkeypatch.setattr(database, "DATABASE_URL", "postgres://example")
    monkeypatch.setattr(database, "_connect", lambda: fake)
    seen = []
    monkeypatch.setattr(database, "_run_schema_migrations", lambda conn: seen.append(conn))

    database.init_db()

    assert seen == [fake]
    assert fake.commits == 1
    assert fake.closed is True
    assert fake.executed[0] == ("SELECT pg_advisory_lock(%s)", (database.SCHEMA_BOOTSTRAP_LOCK_KEY,))
    assert fake.executed[-1] == ("SELECT pg_advisory_unlock(%s)", (database.SCHEMA_BOOTSTRAP_LOCK_KEY,))


def test_init_policy_store_uses_postgres_bootstrap_lock(monkeypatch):
    fake = _FakeConn()
    monkeypatch.setattr(policy_store, "DATABASE_URL", "postgres://example")
    monkeypatch.setattr(policy_store, "_connect", lambda: fake)
    seen = []
    monkeypatch.setattr(policy_store, "_run_schema_migrations", lambda conn: seen.append(conn))

    policy_store.init_policy_store()

    assert seen == [fake]
    assert fake.commits == 1
    assert fake.closed is True
    assert fake.executed[0] == ("SELECT pg_advisory_lock(%s)", (policy_store.SCHEMA_BOOTSTRAP_LOCK_KEY,))
    assert fake.executed[-1] == ("SELECT pg_advisory_unlock(%s)", (policy_store.SCHEMA_BOOTSTRAP_LOCK_KEY,))
