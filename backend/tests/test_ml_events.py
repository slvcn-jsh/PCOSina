import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main
from ml_events import EVENT_DEFINITIONS, build_event, validate_event


def test_required_behavior_events_exist():
    required = {
        "plan_generated",
        "plan_viewed",
        "meal_accepted",
        "meal_replaced",
        "meal_skipped",
        "recipe_opened",
        "grocery_completed",
        "pantry_item_added",
        "pantry_item_removed",
        "pantry_item_expired",
        "cook_completed",
        "no_safe_plan_encountered",
        "manual_override_attempted",
        "why_replaced_submitted",
        "why_skipped_submitted",
    }
    missing = sorted(required - set(EVENT_DEFINITIONS.keys()))
    assert missing == []


def test_event_validation_rejects_missing_required_fields():
    event = build_event(
        event_name="plan_generated",
        uid="user-1",
        request_id="req-1",
        policy_version="policy-v2",
        payload={"status": "success"},
    )
    ok, error = validate_event(event)
    assert not ok
    assert "plan_id" in error


def test_ml_events_endpoint_accepts_valid_event(monkeypatch):
    captured = {}

    def fake_record(event):
        captured["event"] = event

    monkeypatch.setattr(main.database, "record_ml_event", fake_record)
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "user-123"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "eventName": "plan_viewed",
        "requestId": "req-123",
        "payload": {"plan_id": "plan-1"},
    }
    with TestClient(main.app) as client:
        response = client.post("/ml/events", json=payload)

    main.app.dependency_overrides = {}

    assert response.status_code == 200
    assert response.json()["status"] == "accepted"
    event = captured.get("event")
    assert event is not None
    assert event["event_name"] == "plan_viewed"
    assert event["request_id"] == "req-123"
    assert event["policy_version"] == "policy-v1:test"


def test_ml_events_endpoint_normalizes_reason_payload(monkeypatch):
    captured = {}

    def fake_record(event):
        captured["event"] = event

    monkeypatch.setattr(main.database, "record_ml_event", fake_record)
    monkeypatch.setattr(main, "_load_runtime_policy", lambda force_refresh=False: ({}, "policy-v1:test"))

    main.app.dependency_overrides[main.require_firebase_auth] = lambda: {"uid": "user-abc"}
    main.app.dependency_overrides[main.require_app_check] = lambda: {"app_id": "test-app"}
    main.app.dependency_overrides[main.require_schema_version] = lambda: None

    payload = {
        "eventName": "why_replaced_submitted",
        "requestId": "req-r1",
        "payload": {
            "plan_id": "plan-9",
            "slot_index": 5,
            "reason_tag": "manual_swap",
            "reason_text": "Masyadong mahal at kulang ingredients sa pantry.",
        },
    }
    with TestClient(main.app) as client:
        response = client.post("/ml/events", json=payload)

    main.app.dependency_overrides = {}

    assert response.status_code == 200
    event = captured.get("event")
    assert event is not None
    assert event["event_name"] == "why_replaced_submitted"
    assert event["reason_primary_tag"] in {"cost_too_high", "ingredient_unavailable", "user_preference"}
    assert isinstance(event.get("reason_tags"), list)
    assert event.get("reason_has_free_text") is True
    assert "reason_text" not in event
