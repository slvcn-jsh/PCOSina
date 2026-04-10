import sys
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


class _FakeRateLimitStore:
    def __init__(self):
        self.calls = []
        self.counts = {}
        self.backend = "fake"

    def allow(self, key: str, *, window_seconds: int, max_requests: int, now=None) -> bool:
        self.calls.append((key, window_seconds, max_requests))
        self.counts[key] = self.counts.get(key, 0) + 1
        return self.counts[key] == 1

    def health(self):
        return {"backend": self.backend, "enabled": True}


def _fake_bearer(uid: str) -> str:
    header = base64.urlsafe_b64encode(json.dumps({"alg": "none"}).encode("utf-8")).decode("utf-8").rstrip("=")
    payload = base64.urlsafe_b64encode(json.dumps({"sub": uid}).encode("utf-8")).decode("utf-8").rstrip("=")
    return f"{header}.{payload}.signature"


def test_rate_limit_middleware_uses_configured_store():
    store = _FakeRateLimitStore()
    original_store = main.RATE_LIMIT_STORE
    try:
        main.RATE_LIMIT_STORE = store
        with TestClient(main.app) as client:
            first = client.get("/health")
            second = client.get("/health")
        assert first.status_code == 200
        assert second.status_code == 429
        assert store.calls[0][1] == main.RATE_LIMIT_WINDOW_SECONDS
        assert store.calls[0][2] == main.RATE_LIMIT_MAX
    finally:
        main.RATE_LIMIT_STORE = original_store


def test_rate_limit_bucket_scopes_authenticated_requests_by_route_and_uid():
    store = _FakeRateLimitStore()
    original_store = main.RATE_LIMIT_STORE
    try:
        main.RATE_LIMIT_STORE = store
        with TestClient(main.app) as client:
            first = client.get("/health", headers={"Authorization": f"Bearer {_fake_bearer('user-a')}"})
            second = client.get("/health", headers={"Authorization": f"Bearer {_fake_bearer('user-b')}"})
        assert first.status_code == 200
        assert second.status_code == 200
        assert store.calls[0][0] != store.calls[1][0]
        assert "/health|" in store.calls[0][0]
        assert "uid:user-a" in store.calls[0][0]
        assert "uid:user-b" in store.calls[1][0]
    finally:
        main.RATE_LIMIT_STORE = original_store


def test_runtime_readiness_rejects_memory_rate_limit_in_production(monkeypatch):
    monkeypatch.setattr(main, "IS_PRODUCTION", True)
    monkeypatch.setattr(main, "ENVIRONMENT", "production")
    monkeypatch.setattr(main, "RATE_LIMIT_BACKEND", "memory")
    monkeypatch.setattr(main, "ASYNC_MODE", "background")
    monkeypatch.setattr(main, "sentry_dsn", "https://dsn.example")
    monkeypatch.setenv("PCOSINA_ADMIN_SESSION_SECRET", "prod-secret")
    monkeypatch.setenv("FIREBASE_AUTH_DISABLED", "false")
    monkeypatch.setenv("DATABASE_URL", "postgres://db")
    monkeypatch.setenv("PCOSINA_ALLOWED_HOSTS", "pcosina.example.com")
    monkeypatch.setenv("FIREBASE_SERVICE_ACCOUNT_JSON", "{\"type\":\"service_account\"}")
    monkeypatch.setattr(main, "QUEUE_BROKER", type("Broker", (), {"backend": "db", "health": lambda self: {"backend": "db"}})())

    report = main._runtime_readiness_report()
    assert report["ok"] is False
    assert any("RATE_LIMIT_BACKEND=memory" in item for item in report["errors"])
