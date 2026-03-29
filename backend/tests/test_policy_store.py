import sys
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import policy_store


def test_policy_versioning_activation_and_rollback(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    first = policy_store.ensure_default_policy(actor="test")
    assert first["is_active"] is True

    created = policy_store.create_policy_version(
        policy_input={
            "schema_version": "1.0.0",
            "policy_name": "tuned-policy",
            "shortlist_limit": 90,
            "shortlist_limit_restricted": 140,
            "shortlist_keep_min": 25,
            "shortlist_keep_ratio": 0.85,
            "max_pool_size": 320,
            "tolerance_levels": [0.2, 0.3],
            "max_per_week": [2, 3, 4, 8],
            "solver_time_seconds": 7,
            "solver_max_seconds": 14,
            "total_solver_seconds": 30,
            "solver_workers": 2,
            "milp_weights": {
                "repeat_weight": 6,
                "group_weight": 2,
                "diversity_weight": 2,
                "pantry_weight": 1,
            },
            "hard_rule_mode": "strict",
            "allow_unsafe_overrides": False,
        },
        actor="test",
        activate=True,
    )
    assert created["is_active"] is True
    active = policy_store.get_active_policy()
    assert active is not None
    assert active["id"] == created["id"]

    rolled = policy_store.rollback_policy(actor="test")
    assert rolled["is_active"] is True
    assert rolled["id"] != created["id"]

    audit_rows = policy_store.list_policy_audit(limit=20)
    assert len(audit_rows) >= 3
