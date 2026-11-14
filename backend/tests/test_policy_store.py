import sys
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import policy_store
from policy_config import default_policy, load_policy


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
    staging = load_policy(active["policy"]).to_runtime_dict(environment="staging")
    assert staging["stage1"]["max_candidates_per_slot"] == policy_store.PRODUCTION_STAGE1_MAX_CANDIDATES
    assert staging["solver"]["solver_time_limit_seconds"] == policy_store.PRODUCTION_SOLVER_TIME_LIMIT_SECONDS
    assert staging["solver"]["solver_max_seconds"] == policy_store.PRODUCTION_SOLVER_MAX_SECONDS
    assert staging["solver"]["total_solver_seconds"] == policy_store.PRODUCTION_TOTAL_SOLVER_SECONDS
    assert staging["solver"]["retry_attempts"] == policy_store.PRODUCTION_SOLVER_RETRY_ATTEMPTS
    assert staging["solver"]["solver_workers"] == policy_store.PRODUCTION_SOLVER_WORKERS


def test_ensure_default_policy_upgrades_canonical_stage1_to_on(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_canonical_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    payload = default_policy().to_runtime_dict()
    payload["stage1"]["canonical_features_enabled"] = False
    payload["environment_overrides"]["staging"]["stage1"]["canonical_features_enabled"] = False
    payload["environment_overrides"]["production"]["stage1"]["canonical_features_enabled"] = False
    policy_store.create_policy_version(
        policy_input=payload,
        actor="test",
        notes="legacy-canonical-off",
        activate=True,
    )

    upgraded = policy_store.ensure_default_policy(actor="test")

    for environment in ("development", "staging", "production"):
        resolved = load_policy(upgraded["policy"]).to_runtime_dict(environment=environment)
        assert resolved["stage1"]["canonical_features_enabled"] is True


def test_ensure_default_policy_upgrades_default_semantic_variety(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_semantic_variety_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    payload = default_policy().to_runtime_dict()
    payload["planning"].update(
        {
            "semantic_ingredient_family_escape_repeat_limit": 10,
            "semantic_ingredient_family_max_share": 0.70,
            "semantic_ingredient_family_max_per_week": None,
            "semantic_ingredient_family_min_candidate_share": 0.15,
            "semantic_fatigue_family_min_candidate_share": 0.25,
            "semantic_ingredient_family_max_capped_families": 8,
            "semantic_family_soft_limit_per_week": 10,
            "semantic_family_diversity_weight": 12,
        }
    )
    created = policy_store.create_policy_version(
        policy_input=payload,
        actor="test",
        notes="legacy-semantic-variety",
        activate=True,
    )

    upgraded = policy_store.ensure_default_policy(actor="test")

    assert upgraded["id"] != created["id"]
    assert upgraded["rollback_of"] == created["id"]
    planning = load_policy(upgraded["policy"]).to_runtime_dict()["planning"]
    assert planning["semantic_ingredient_family_escape_repeat_limit"] == 0
    assert planning["semantic_ingredient_family_max_share"] is None
    assert planning["semantic_ingredient_family_max_per_week"] == 8
    assert planning["semantic_ingredient_family_min_candidate_share"] == 0.0
    assert planning["semantic_fatigue_family_min_candidate_share"] == 0.0
    assert planning["semantic_ingredient_family_max_capped_families"] == 16
    assert planning["semantic_family_soft_limit_per_week"] == 6
    assert planning["semantic_family_diversity_weight"] == 36


def test_ensure_default_policy_upgrades_default_staging_performance(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_staging_perf_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    created = policy_store.create_policy_version(
        policy_input={
            "schema_version": "2.0.0",
            "policy_name": "default",
            "environment_profile": "production",
            "environment_overrides": {
                "production": {
                    "stage1": {
                        "ML_shadow_enabled": True,
                        "ML_canary_enabled": True,
                        "max_candidates_per_slot": policy_store.PRODUCTION_STAGE1_MAX_CANDIDATES,
                        "restricted_shortlist_multiplier": policy_store.PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER,
                        "pool_cap_top_share": policy_store.PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE,
                    },
                    "solver": {
                        "solver_time_limit_seconds": policy_store.PRODUCTION_SOLVER_TIME_LIMIT_SECONDS,
                        "solver_max_seconds": policy_store.PRODUCTION_SOLVER_MAX_SECONDS,
                        "total_solver_seconds": policy_store.PRODUCTION_TOTAL_SOLVER_SECONDS,
                        "retry_attempts": policy_store.PRODUCTION_SOLVER_RETRY_ATTEMPTS,
                        "solver_workers": policy_store.PRODUCTION_SOLVER_WORKERS,
                    },
                    "sre": {
                        "canary_cohort_percent": policy_store.PRODUCTION_CANARY_BOOTSTRAP_PERCENT,
                    },
                }
            },
        },
        actor="test",
        activate=True,
    )

    upgraded = policy_store.ensure_default_policy(actor="test")

    assert upgraded["id"] != created["id"]
    assert upgraded["rollback_of"] == created["id"]
    staging = load_policy(upgraded["policy"]).to_runtime_dict(environment="staging")
    assert staging["stage1"]["max_candidates_per_slot"] == policy_store.PRODUCTION_STAGE1_MAX_CANDIDATES
    assert staging["solver"]["total_solver_seconds"] == policy_store.PRODUCTION_TOTAL_SOLVER_SECONDS
    assert staging["solver"]["retry_attempts"] == policy_store.PRODUCTION_SOLVER_RETRY_ATTEMPTS


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


def test_ensure_default_policy_adds_staging_performance_when_missing(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_missing_staging_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    created = policy_store.create_policy_version(
        policy_input={
            "schema_version": "2.0.0",
            "policy_name": "ops-production-only",
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

    upgraded = policy_store.ensure_default_policy(actor="test")

    assert upgraded["id"] != created["id"]
    staging = load_policy(upgraded["policy"]).to_runtime_dict(environment="staging")
    assert staging["stage1"]["max_candidates_per_slot"] == policy_store.PRODUCTION_STAGE1_MAX_CANDIDATES
    assert staging["solver"]["total_solver_seconds"] == policy_store.PRODUCTION_TOTAL_SOLVER_SECONDS
    assert staging["solver"]["retry_attempts"] == policy_store.PRODUCTION_SOLVER_RETRY_ATTEMPTS
    production = load_policy(upgraded["policy"]).to_runtime_dict(environment="production")
    assert production["stage1"]["max_candidates_per_slot"] == 80
    assert production["solver"]["total_solver_seconds"] == 18.0


def test_ensure_default_policy_preserves_explicit_staging_latency_tuning(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_store"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_store_custom_staging_{uuid.uuid4().hex}.db"
    monkeypatch.setattr(policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(policy_store, "DB_NAME", str(test_db))

    policy_store.init_policy_store()
    created = policy_store.create_policy_version(
        policy_input={
            "schema_version": "2.0.0",
            "policy_name": "ops-staging-custom",
            "environment_profile": "production",
            "environment_overrides": {
                "production": {
                    "stage1": {
                        "ML_shadow_enabled": True,
                        "ML_canary_enabled": True,
                        "max_candidates_per_slot": policy_store.PRODUCTION_STAGE1_MAX_CANDIDATES,
                        "restricted_shortlist_multiplier": policy_store.PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER,
                        "pool_cap_top_share": policy_store.PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE,
                    },
                    "solver": {
                        "solver_time_limit_seconds": policy_store.PRODUCTION_SOLVER_TIME_LIMIT_SECONDS,
                        "solver_max_seconds": policy_store.PRODUCTION_SOLVER_MAX_SECONDS,
                        "total_solver_seconds": policy_store.PRODUCTION_TOTAL_SOLVER_SECONDS,
                        "retry_attempts": policy_store.PRODUCTION_SOLVER_RETRY_ATTEMPTS,
                        "solver_workers": policy_store.PRODUCTION_SOLVER_WORKERS,
                    },
                    "sre": {
                        "canary_cohort_percent": 5.0,
                    },
                },
                "staging": {
                    "stage1": {
                        "max_candidates_per_slot": 96,
                    },
                    "solver": {
                        "total_solver_seconds": 20.0,
                        "retry_attempts": 2,
                    },
                },
            },
        },
        actor="test",
        activate=True,
    )

    active = policy_store.ensure_default_policy(actor="test")
    staging = load_policy(active["policy"]).to_runtime_dict(environment="staging")

    assert active["id"] == created["id"]
    assert staging["stage1"]["max_candidates_per_slot"] == 96
    assert staging["solver"]["total_solver_seconds"] == 20.0
    assert staging["solver"]["retry_attempts"] == 2


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

    assert active["rollback_of"] == created["id"]
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
