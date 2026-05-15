import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_canary_webhook_receiver_accepts_valid_key(monkeypatch):
    monkeypatch.setenv("PCOSINA_WEBHOOK_RECEIVER_KEY", "test-webhook-key")
    main._canary_webhook_events.clear()

    seen_metrics: list[tuple[str, int]] = []
    monkeypatch.setattr(main, "_inc_plan_job_diag", lambda key, delta=1: seen_metrics.append((key, int(delta))))

    with TestClient(main.app) as client:
        response = client.post(
            "/ops/webhooks/canary/alert/test-webhook-key",
            json={"source": "unit-test", "status": "ok"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "accepted"
    assert body["channel"] == "alert"
    assert isinstance(body["eventId"], str) and body["eventId"]
    assert seen_metrics == [("canary_webhook_alert_received_total", 1)]
    assert len(main._canary_webhook_events) == 1
    assert main._canary_webhook_events[0]["channel"] == "alert"


def test_canary_webhook_receiver_accepts_header_key(monkeypatch):
    monkeypatch.setenv("PCOSINA_WEBHOOK_RECEIVER_KEY", "test-webhook-key")
    main._canary_webhook_events.clear()

    with TestClient(main.app) as client:
        response = client.post(
            "/ops/webhooks/canary/alert",
            headers={"X-PCOSINA-Webhook-Key": "test-webhook-key"},
            json={"source": "unit-test", "status": "ok"},
        )

    assert response.status_code == 200
    assert response.json()["status"] == "accepted"
    assert len(main._canary_webhook_events) == 1
    assert main._canary_webhook_events[0]["channel"] == "alert"


def test_canary_webhook_receiver_rejects_invalid_key(monkeypatch):
    monkeypatch.setenv("PCOSINA_WEBHOOK_RECEIVER_KEY", "test-webhook-key")
    main._canary_webhook_events.clear()

    with TestClient(main.app) as client:
        response = client.post(
            "/ops/webhooks/canary/dashboard/wrong-key",
            json={"source": "unit-test"},
        )

    assert response.status_code == 401
    assert response.json()["detail"] == "Unauthorized webhook receiver key"
    assert main._canary_webhook_events == []


def test_canary_webhook_recent_requires_admin_and_returns_items(monkeypatch):
    monkeypatch.setenv("PCOSINA_WEBHOOK_RECEIVER_KEY", "test-webhook-key")
    main._canary_webhook_events.clear()
    main.app.dependency_overrides[main.require_admin_config_token] = lambda: {"actor": "test-admin"}

    try:
        with TestClient(main.app) as client:
            post_resp = client.post(
                "/ops/webhooks/canary/dashboard/test-webhook-key",
                json={"source": "unit-test", "panel": "canary"},
            )
            assert post_resp.status_code == 200

            recent_resp = client.get("/ops/webhooks/canary/recent?limit=10")
        assert recent_resp.status_code == 200
        payload = recent_resp.json()
        assert payload["status"] == "ok"
        assert payload["count"] >= 1
        assert payload["items"][0]["channel"] == "dashboard"
        assert payload["receiverKeyConfigured"] is True
    finally:
        main.app.dependency_overrides = {}
