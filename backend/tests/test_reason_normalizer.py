import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from services.reason_normalizer import normalize_reason_payload


def test_reason_normalizer_tag_and_text_merges_to_controlled_tags():
    payload = {
        "reason_tag": "manual_swap",
        "reason_text": "Masyadong mahal and missing ingredients sa pantry.",
        "plan_id": "plan-1",
        "slot_index": 2,
    }
    normalized = normalize_reason_payload("why_replaced_submitted", payload)
    assert normalized["reason_primary_tag"] in {"cost_too_high", "ingredient_unavailable", "user_preference"}
    assert "reason_tags" in normalized
    assert "cost_too_high" in normalized["reason_tags"]
    assert "ingredient_unavailable" in normalized["reason_tags"]
    assert normalized["reason_has_free_text"] is True
    assert normalized["reason_source"] == "tag_and_text"
    assert "reason_text" not in normalized
    assert normalized["reason_tag"] == normalized["reason_primary_tag"]


def test_reason_normalizer_fallback_for_skip_event():
    normalized = normalize_reason_payload(
        "why_skipped_submitted",
        {"plan_id": "plan-2", "slot_index": 0},
    )
    assert normalized["reason_primary_tag"] == "skipped_by_user"
    assert "skipped_by_user" in normalized["reason_tags"]
    assert normalized["reason_source"] == "none"


def test_reason_normalizer_noop_for_non_reason_events():
    payload = {"plan_id": "x", "status": "success"}
    normalized = normalize_reason_payload("plan_generated", payload)
    assert normalized == payload
