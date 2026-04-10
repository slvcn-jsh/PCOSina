import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import pytest

from services.sync_recovery import resolve_uid_scoped_snapshot


def test_cross_device_replay_last_write_wins_is_deterministic():
    uid = "uid-replay-1"
    local = {
        "uid": uid,
        "profile": {"weightKg": 60, "fieldUpdatedAtMs": {"weightKg": 100}, "updatedAtMs": 100},
        "pantry": [{"id": "egg", "quantity": 2, "updatedAtMs": 90}],
        "grocery": [{"id": "rice", "checked": False, "updatedAtMs": 90}],
        "logs": [],
        "savedPlans": [],
    }

    # Device B sync payload arrives from cloud.
    remote_b = {
        "uid": uid,
        "profile": {"weightKg": 62, "fieldUpdatedAtMs": {"weightKg": 200}, "updatedAtMs": 200},
        "pantry": [{"id": "egg", "quantity": 5, "updatedAtMs": 180}],
        "grocery": [{"id": "rice", "checked": False, "updatedAtMs": 120}],
        "logs": [{"id": "log-1", "note": "b", "updatedAtMs": 150}],
        "savedPlans": [{"id": "plan-b", "updatedAtMs": 160}],
    }
    merged_1, _ = resolve_uid_scoped_snapshot(
        uid=uid,
        local_snapshot=local,
        remote_snapshot=remote_b,
        conflict_resolution_policy="last_write_wins",
    )
    assert merged_1["profile"]["weightKg"] == 62
    assert next(item for item in merged_1["pantry"] if item.get("id") == "egg")["quantity"] == 5

    # Device A later updates pantry + grocery state locally.
    remote_a = {
        "uid": uid,
        "profile": {"weightKg": 61, "fieldUpdatedAtMs": {"weightKg": 190}, "updatedAtMs": 190},
        "pantry": [{"id": "egg", "quantity": 3, "updatedAtMs": 250}],
        "grocery": [{"id": "rice", "checked": True, "updatedAtMs": 260}],
        "logs": [{"id": "log-1", "note": "a", "updatedAtMs": 240}],
        "savedPlans": [{"id": "plan-a", "updatedAtMs": 245}],
    }
    merged_2, event = resolve_uid_scoped_snapshot(
        uid=uid,
        local_snapshot=merged_1,
        remote_snapshot=remote_a,
        conflict_resolution_policy="last_write_wins",
    )
    assert merged_2["profile"]["weightKg"] == 62  # newer profile field from device B remains
    assert next(item for item in merged_2["pantry"] if item.get("id") == "egg")["quantity"] == 3
    assert next(item for item in merged_2["grocery"] if item.get("id") == "rice")["checked"] is True
    assert event["artifactCounts"]["pantry"] >= 1

    # Replay same payload is idempotent in outcome.
    merged_3, _ = resolve_uid_scoped_snapshot(
        uid=uid,
        local_snapshot=merged_2,
        remote_snapshot=remote_a,
        conflict_resolution_policy="last_write_wins",
    )
    assert merged_3["profile"]["weightKg"] == merged_2["profile"]["weightKg"]
    assert merged_3["pantry"] == merged_2["pantry"]
    assert merged_3["grocery"] == merged_2["grocery"]


def test_cross_uid_replay_is_rejected():
    with pytest.raises(ValueError):
        resolve_uid_scoped_snapshot(
            uid="uid-a",
            local_snapshot={"uid": "uid-a"},
            remote_snapshot={"uid": "uid-b"},
            conflict_resolution_policy="last_write_wins",
        )
