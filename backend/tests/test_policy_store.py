import sys
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import policy_store
from policy_config import load_policy


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


def test_ensure_default_policy_bootstraps_production_canary(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_canary_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    active = policy_store.ensure_default_policy(actor="test")

    resolved = load_policy(active["policy"]).to_runtime_dict(environment="production")
    assert resolved["stage1"]["ML_shadow_enabled"] is True
    assert resolved["stage1"]["ML_canary_enabled"] is True
    assert resolved["sre"]["canary_cohort_percent"] == 5.0
    assert resolved["stage1"]["max_candidates_per_slot"] == policy_store.PRODUCTION_STAGE1_MAX_CANDIDATES
    assert resolved["solver"]["solver_time_limit_seconds"] == policy_store.PRODUCTION_SOLVER_TIME_LIMIT_SECONDS
    assert resolved["solver"]["solver_max_seconds"] == policy_store.PRODUCTION_SOLVER_MAX_SECONDS
    assert resolved["solver"]["total_solver_seconds"] == policy_store.PRODUCTION_TOTAL_SOLVER_SECONDS
    assert resolved["solver"]["retry_attempts"] == policy_store.PRODUCTION_SOLVER_RETRY_ATTEMPTS
    assert resolved["solver"]["solver_workers"] == policy_store.PRODUCTION_SOLVER_WORKERS


def test_ensure_default_policy_upgrades_existing_policy_for_production_canary(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_upgrade_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    created = policy_store.create_policy_version(
        policy_input={
            "schema_version": "2.0.0",
            "policy_name": "manual-bootstrap",
            "environment_profile": "production",
            "stage1": {
                "ML_shadow_enabled": True,
                "ML_canary_enabled": False,
            },
            "sre": {
                "canary_cohort_percent": 0.0,
            },
        },
        actor="test",
        activate=True,
    )

    upgraded = policy_store.ensure_default_policy(actor="test")

    assert upgraded["id"] != created["id"]
    assert upgraded["rollback_of"] == created["id"]
    resolved = load_policy(upgraded["policy"]).to_runtime_dict(environment="production")
    assert resolved["stage1"]["ML_shadow_enabled"] is True
    assert resolved["stage1"]["ML_canary_enabled"] is True
    assert resolved["sre"]["canary_cohort_percent"] == 5.0
    assert resolved["stage1"]["max_candidates_per_slot"] == policy_store.PRODUCTION_STAGE1_MAX_CANDIDATES
    assert resolved["solver"]["solver_time_limit_seconds"] == policy_store.PRODUCTION_SOLVER_TIME_LIMIT_SECONDS
    assert resolved["solver"]["retry_attempts"] == policy_store.PRODUCTION_SOLVER_RETRY_ATTEMPTS


def test_ensure_default_policy_does_not_override_custom_latency_tuning(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_custom_latency_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    created = policy_store.create_policy_version(
        policy_input={
            "schema_version": "2.0.0",
            "policy_name": "ops-custom",
            "environment_profile": "production",
            "environment_overrides": {
                "production": {
                    "stage1": {
                        "ML_shadow_enabled": True,
                        "ML_canary_enabled": True,
                        "max_candidates_per_slot": 80,
                        "restricted_shortlist_multiplier": 1.2,
                        "pool_cap_top_share": 0.5,
                    },
                    "solver": {
                        "solver_time_limit_seconds": 5.0,
                        "solver_max_seconds": 9.0,
                        "total_solver_seconds": 18.0,
                        "retry_attempts": 1,
                        "solver_workers": 2,
                    },
                    "sre": {
                        "canary_cohort_percent": 5.0,
                    },
                }
            },
        },
        actor="test",
        activate=True,
    )

    resolved_before = load_policy(created["policy"]).to_runtime_dict(environment="production")
    active = policy_store.ensure_default_policy(actor="test")
    resolved_after = load_policy(active["policy"]).to_runtime_dict(environment="production")

    assert active["id"] == created["id"]
    assert resolved_after["stage1"]["max_candidates_per_slot"] == resolved_before["stage1"]["max_candidates_per_slot"]
    assert resolved_after["solver"]["total_solver_seconds"] == resolved_before["solver"]["total_solver_seconds"]


def test_bootstrap_policy_create_reuses_matching_active_policy(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_bootstrap_reuse_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    active = policy_store.ensure_default_policy(actor="system-bootstrap")

    reused = policy_store.create_policy_version(
        policy_input=active["policy"],
        actor="system-bootstrap",
        notes="bootstrap-production-canary-defaults",
        activate=True,
    )

    assert reused["id"] == active["id"]
