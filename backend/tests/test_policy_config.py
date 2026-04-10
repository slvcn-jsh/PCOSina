import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import pytest

from policy_config import PlannerPolicyConfig, load_policy


def test_default_policy_is_strict_and_valid():
    policy = PlannerPolicyConfig()
    assert policy.hard_rule_mode == "strict"
    assert policy.allow_unsafe_overrides is False
    assert policy.nutrition.calorie_max > policy.nutrition.calorie_min
    assert policy.planning.planning_horizon_days == 7
    assert policy.stage1.max_candidates_per_slot >= 10


def test_policy_rejects_unsafe_override_flag():
    with pytest.raises(ValueError):
        load_policy({
            "schema_version": "1.0.0",
            "policy_name": "unsafe",
            "shortlist_limit": 80,
            "shortlist_limit_restricted": 120,
            "shortlist_keep_min": 20,
            "shortlist_keep_ratio": 0.8,
            "max_pool_size": 300,
            "tolerance_levels": [0.2, 0.3, 0.4],
            "max_per_week": [2, 3, 4, 10],
            "solver_time_seconds": 6,
            "solver_max_seconds": 12,
            "total_solver_seconds": 25,
            "solver_workers": 4,
            "milp_weights": {
                "repeat_weight": 5,
                "group_weight": 2,
                "diversity_weight": 1,
                "pantry_weight": 1,
            },
            "hard_rule_mode": "strict",
            "allow_unsafe_overrides": True,
        })


def test_policy_rejects_non_strict_hard_rule_mode():
    with pytest.raises(ValueError):
        PlannerPolicyConfig(hard_rule_mode="relaxed")


def test_policy_rejects_invalid_distribution_sum():
    with pytest.raises(ValueError):
        PlannerPolicyConfig(
            nutrition={
                "meal_distribution_targets": [0.9, 0.9, 0.9]
            }
        )
