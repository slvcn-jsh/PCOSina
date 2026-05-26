from __future__ import annotations

from copy import deepcopy
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator

POLICY_SCHEMA_VERSION = "2.0.0"
SUPPORTED_POLICY_SCHEMA_VERSIONS = {"1.0.0", "2.0.0"}
PRODUCTION_CANARY_BOOTSTRAP_PERCENT = 5.0
PRODUCTION_STAGE1_MAX_CANDIDATES = 64
PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER = 1.15
PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE = 0.45
PRODUCTION_SOLVER_TIME_LIMIT_SECONDS = 4.0
PRODUCTION_SOLVER_MAX_SECONDS = 7.0
PRODUCTION_TOTAL_SOLVER_SECONDS = 14.0
PRODUCTION_SOLVER_RETRY_ATTEMPTS = 1
PRODUCTION_SOLVER_WORKERS = 4


def _default_environment_overrides() -> Dict[str, Dict[str, Any]]:
    return {
        "staging": {
            "stage1": {
                "max_candidates_per_slot": PRODUCTION_STAGE1_MAX_CANDIDATES,
                "restricted_shortlist_multiplier": PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER,
                "pool_cap_top_share": PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE,
            },
            "solver": {
                "solver_time_limit_seconds": PRODUCTION_SOLVER_TIME_LIMIT_SECONDS,
                "solver_max_seconds": PRODUCTION_SOLVER_MAX_SECONDS,
                "total_solver_seconds": PRODUCTION_TOTAL_SOLVER_SECONDS,
                "retry_attempts": PRODUCTION_SOLVER_RETRY_ATTEMPTS,
                "solver_workers": PRODUCTION_SOLVER_WORKERS,
            },
        },
        "production": {
            "stage1": {
                "ML_shadow_enabled": True,
                "ML_canary_enabled": True,
                "max_candidates_per_slot": PRODUCTION_STAGE1_MAX_CANDIDATES,
                "restricted_shortlist_multiplier": PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER,
                "pool_cap_top_share": PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE,
            },
            "solver": {
                "solver_time_limit_seconds": PRODUCTION_SOLVER_TIME_LIMIT_SECONDS,
                "solver_max_seconds": PRODUCTION_SOLVER_MAX_SECONDS,
                "total_solver_seconds": PRODUCTION_TOTAL_SOLVER_SECONDS,
                "retry_attempts": PRODUCTION_SOLVER_RETRY_ATTEMPTS,
                "solver_workers": PRODUCTION_SOLVER_WORKERS,
            },
            "sre": {
                "canary_cohort_percent": PRODUCTION_CANARY_BOOTSTRAP_PERCENT,
            },
        }
    }


class NutritionPolicy(BaseModel):
    calorie_min: int = Field(default=1200, ge=800, le=4000)
    calorie_max: int = Field(default=3200, ge=1000, le=5000)
    carb_min: int = Field(default=120, ge=0, le=700)
    carb_max: int = Field(default=420, ge=50, le=900)
    protein_min: int = Field(default=55, ge=0, le=400)
    protein_max: int = Field(default=220, ge=20, le=500)
    fat_min: int = Field(default=35, ge=0, le=250)
    fat_max: int = Field(default=140, ge=20, le=350)
    fiber_min: int = Field(default=20, ge=0, le=120)
    sodium_max: int = Field(default=2300, ge=500, le=7000)
    sugar_max: int = Field(default=50, ge=0, le=250)
    meal_distribution_targets: List[float] = Field(default_factory=lambda: [0.3, 0.35, 0.35])
    daily_tolerance_percent: float = Field(default=0.20, ge=0.01, le=0.80)
    weekly_tolerance_percent: float = Field(default=0.10, ge=0.0, le=0.50)

    @model_validator(mode="after")
    def validate_ranges(self) -> "NutritionPolicy":
        if self.calorie_max <= self.calorie_min:
            raise ValueError("nutrition.calorie_max must be > calorie_min")
        if self.carb_max < self.carb_min:
            raise ValueError("nutrition.carb_max must be >= carb_min")
        if self.protein_max < self.protein_min:
            raise ValueError("nutrition.protein_max must be >= protein_min")
        if self.fat_max < self.fat_min:
            raise ValueError("nutrition.fat_max must be >= fat_min")
        targets = [float(v) for v in self.meal_distribution_targets if float(v) > 0]
        if len(targets) < 1:
            raise ValueError("nutrition.meal_distribution_targets must contain positive values")
        total = sum(targets)
        if not (0.95 <= total <= 1.05):
            raise ValueError("nutrition.meal_distribution_targets must sum near 1.0")
        self.meal_distribution_targets = targets
        return self


class SnackRules(BaseModel):
    enabled: bool = False
    max_snacks_per_day: int = Field(default=0, ge=0, le=4)
    snack_calorie_cap: int = Field(default=200, ge=50, le=800)


class PlanningPolicy(BaseModel):
    planning_horizon_days: int = Field(default=7, ge=1, le=31)
    meals_per_day: int = Field(default=3, ge=1, le=6)
    snack_rules: SnackRules = Field(default_factory=SnackRules)
    recipe_repeat_limits: List[int] = Field(default_factory=lambda: [2, 3, 4, 10])
    cuisine_diversity_weight: int = Field(default=2, ge=0, le=100)
    pantry_utilization_weight: int = Field(default=1, ge=0, le=100)
    grocery_cost_weight: int = Field(default=1, ge=0, le=100)
    prep_time_weight: int = Field(default=1, ge=0, le=100)
    acceptance_score_weight: int = Field(default=1, ge=0, le=100)
    substitution_penalty: int = Field(default=2, ge=0, le=100)
    meal_min_calorie_target: int = Field(default=180, ge=50, le=1200)
    group_limit_floor: int = Field(default=2, ge=1, le=14)
    diversity_min_token_target: int = Field(default=5, ge=1, le=50)
    infeasibility_relaxation_order: List[str] = Field(
        default_factory=lambda: [
            "daily_tolerance_percent",
            "recipe_repeat_limits",
        ]
    )

    @model_validator(mode="after")
    def validate_limits(self) -> "PlanningPolicy":
        values = sorted(set(int(v) for v in self.recipe_repeat_limits if 1 <= int(v) <= 50))
        if not values:
            raise ValueError("planning.recipe_repeat_limits must contain at least one value")
        self.recipe_repeat_limits = values
        if not self.infeasibility_relaxation_order:
            raise ValueError("planning.infeasibility_relaxation_order must not be empty")
        return self


class Stage1Policy(BaseModel):
    max_candidates_per_slot: int = Field(default=120, ge=10, le=5000)
    ranking_cutoff: float = Field(default=0.80, ge=0.01, le=1.0)
    similarity_threshold: float = Field(default=0.85, ge=0.0, le=1.0)
    restricted_shortlist_multiplier: float = Field(default=1.25, ge=1.0, le=3.0)
    budget_keep_min_count: int = Field(default=10, ge=1, le=500)
    budget_keep_min_ratio: float = Field(default=0.25, ge=0.0, le=1.0)
    pantry_match_threshold: int = Field(default=0, ge=0, le=50)
    minimum_candidates_required: int = Field(default=10, ge=1, le=200)
    pool_cap_top_share: float = Field(default=0.60, ge=0.05, le=0.95)
    pre_pricing_pruning_enabled: bool = True
    pre_pricing_candidate_cap: Optional[int] = Field(default=None, ge=50, le=5000)
    pre_pricing_candidate_multiplier: float = Field(default=5.0, ge=1.0, le=20.0)
    pre_pricing_bucket_reserve: int = Field(default=32, ge=0, le=500)
    pre_pricing_restricted_enabled: bool = False
    exclusion_penalty_weights: Dict[str, float] = Field(
        default_factory=lambda: {
            "allergy": 1000.0,
            "restriction": 500.0,
            "prep_time": 50.0,
        }
    )
    cold_start_defaults: Dict[str, Any] = Field(
        default_factory=lambda: {
            "activityLevel": "Lightly Active",
            "goal": "General Health",
        }
    )
    ML_shadow_enabled: bool = True
    ML_canary_enabled: bool = False
    ML_score_weight: float = Field(default=0.15, ge=0.0, le=1.0)
    ML_score_cap: float = Field(default=0.30, ge=0.0, le=1.0)


class SolverPolicy(BaseModel):
    solver_time_limit_seconds: float = Field(default=6.0, ge=1.0, le=300.0)
    max_solution_count: int = Field(default=1, ge=1, le=100)
    optimality_gap_target: float = Field(default=0.05, ge=0.0, le=1.0)
    infeasibility_diagnostic_depth: int = Field(default=30, ge=1, le=1000)
    worker_memory_limit: int = Field(default=1024, ge=128, le=65536)
    retry_attempts: int = Field(default=2, ge=0, le=20)
    timeout_ms: int = Field(default=120000, ge=1000, le=900000)
    circuit_breaker_threshold: int = Field(default=5, ge=1, le=500)
    queue_priority_rules: Dict[str, Any] = Field(default_factory=lambda: {"default": "fifo"})
    solver_max_seconds: float = Field(default=12.0, ge=1.0, le=300.0)
    total_solver_seconds: float = Field(default=25.0, ge=3.0, le=600.0)
    solver_workers: int = Field(default=4, ge=1, le=32)

    @model_validator(mode="after")
    def validate_solver_bounds(self) -> "SolverPolicy":
        if self.solver_max_seconds < self.solver_time_limit_seconds:
            raise ValueError("solver.solver_max_seconds must be >= solver_time_limit_seconds")
        if self.total_solver_seconds < self.solver_max_seconds:
            raise ValueError("solver.total_solver_seconds must be >= solver_max_seconds")
        return self


class SyncOfflinePolicy(BaseModel):
    sync_batch_size: int = Field(default=100, ge=1, le=10000)
    sync_retry_backoff: List[int] = Field(default_factory=lambda: [1000, 3000, 5000])
    dead_letter_threshold: int = Field(default=5, ge=1, le=100)
    local_cache_ttl: int = Field(default=600, ge=1, le=86400)
    local_cache_max_entries: int = Field(default=200, ge=1, le=50000)
    conflict_resolution_policy: str = Field(default="last_write_wins")
    reinstall_recovery_timeout: int = Field(default=30000, ge=1000, le=600000)
    offline_read_guarantees: List[str] = Field(
        default_factory=lambda: [
            "latest_saved_plan",
            "pantry",
            "grocery",
            "profile",
            "progress_logs",
        ]
    )


class SrePolicy(BaseModel):
    latency_slo_p50_ms: int = Field(default=2500, ge=1, le=120000)
    latency_slo_p95_ms: int = Field(default=7000, ge=1, le=120000)
    latency_slo_p99_ms: int = Field(default=12000, ge=1, le=180000)
    crash_free_target: float = Field(default=0.995, ge=0.0, le=1.0)
    api_error_budget: float = Field(default=0.01, ge=0.0, le=1.0)
    canary_cohort_percent: float = Field(default=5.0, ge=0.0, le=100.0)
    rollback_trigger_thresholds: Dict[str, float] = Field(
        default_factory=lambda: {
            "hard_violation_rate": 0.0,
            "latency_p95_ms": 7000.0,
            "api_error_rate": 0.01,
        }
    )
    observability_sampling_rate: float = Field(default=0.1, ge=0.0, le=1.0)


class SecurityPolicy(BaseModel):
    token_ttl: int = Field(default=3600, ge=60, le=604800)
    key_rotation_days: int = Field(default=90, ge=1, le=3650)
    audit_log_retention_days: int = Field(default=365, ge=1, le=3650)
    backup_retention_days: int = Field(default=30, ge=1, le=3650)
    encryption_required_fields: List[str] = Field(
        default_factory=lambda: [
            "profile",
            "pantry",
            "logs",
            "grocery",
        ]
    )


class MilpWeights(BaseModel):
    repeat_weight: int = Field(default=5, ge=0, le=100)
    group_weight: int = Field(default=2, ge=0, le=100)
    diversity_weight: int = Field(default=1, ge=0, le=100)
    pantry_weight: int = Field(default=1, ge=0, le=100)


class PlannerPolicyConfig(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: str = POLICY_SCHEMA_VERSION
    policy_name: str = Field(default="default")
    environment_profile: str = Field(default="production")
    environment_overrides: Dict[str, Dict[str, Any]] = Field(default_factory=_default_environment_overrides)

    nutrition: NutritionPolicy = Field(default_factory=NutritionPolicy)
    planning: PlanningPolicy = Field(default_factory=PlanningPolicy)
    stage1: Stage1Policy = Field(default_factory=Stage1Policy)
    solver: SolverPolicy = Field(default_factory=SolverPolicy)
    sync_offline: SyncOfflinePolicy = Field(default_factory=SyncOfflinePolicy)
    sre: SrePolicy = Field(default_factory=SrePolicy)
    security: SecurityPolicy = Field(default_factory=SecurityPolicy)

    milp_weights: MilpWeights = Field(default_factory=MilpWeights)
    hard_rule_mode: str = Field(default="strict")
    allow_unsafe_overrides: bool = False

    @model_validator(mode="after")
    def validate_structure(self) -> "PlannerPolicyConfig":
        if self.schema_version not in SUPPORTED_POLICY_SCHEMA_VERSIONS:
            raise ValueError(
                f"Unsupported schema_version '{self.schema_version}'. Supported: {sorted(SUPPORTED_POLICY_SCHEMA_VERSIONS)}"
            )
        if self.allow_unsafe_overrides:
            raise ValueError("allow_unsafe_overrides must remain false")
        if self.hard_rule_mode.lower() != "strict":
            raise ValueError("hard_rule_mode must be 'strict' to preserve safety contract")
        env = self.environment_profile.strip().lower()
        if env not in {"development", "staging", "production"}:
            raise ValueError("environment_profile must be development|staging|production")
        self.environment_profile = env
        return self

    def resolve_for_environment(self, environment: str) -> Dict[str, Any]:
        base = self.model_dump()
        env = (environment or "").strip().lower()
        overrides = self.environment_overrides.get(env)
        if not isinstance(overrides, dict) or not overrides:
            return base
        merged = deepcopy(base)
        _deep_merge(merged, overrides)
        return merged

    def to_runtime_dict(self, environment: Optional[str] = None) -> Dict[str, Any]:
        if environment:
            return self.resolve_for_environment(environment)
        return self.model_dump()


def _deep_merge(base: Dict[str, Any], override: Dict[str, Any]) -> None:
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            _deep_merge(base[key], value)
        else:
            base[key] = value


class PolicyCreateRequest(BaseModel):
    policy: Dict[str, Any]
    notes: Optional[str] = None
    activate: bool = False


class PolicyActivateRequest(BaseModel):
    policy_id: str
    notes: Optional[str] = None


class PolicyRollbackRequest(BaseModel):
    target_policy_id: Optional[str] = None
    notes: Optional[str] = None


def _legacy_to_v2(payload: Dict[str, Any]) -> Dict[str, Any]:
    if "nutrition" in payload and "planning" in payload and "stage1" in payload:
        return payload

    legacy = deepcopy(payload)
    migrated: Dict[str, Any] = {
        "schema_version": POLICY_SCHEMA_VERSION,
        "policy_name": legacy.get("policy_name", "migrated-legacy-policy"),
        "environment_profile": legacy.get("environment_profile", "production"),
        "environment_overrides": legacy.get("environment_overrides", {}),
        "nutrition": {
            "daily_tolerance_percent": (
                legacy.get("tolerance_levels", [0.2])[0] if isinstance(legacy.get("tolerance_levels"), list) and legacy.get("tolerance_levels") else 0.2
            ),
        },
        "planning": {
            "recipe_repeat_limits": legacy.get("max_per_week", [2, 3, 4, 10]),
        },
        "stage1": {
            "max_candidates_per_slot": max(
                int(legacy.get("shortlist_limit", 80)),
                int(legacy.get("shortlist_limit_restricted", 120)),
            ),
            "ranking_cutoff": float(legacy.get("shortlist_keep_ratio", 0.8)),
        },
        "solver": {
            "solver_time_limit_seconds": float(legacy.get("solver_time_seconds", 6.0)),
            "solver_max_seconds": float(legacy.get("solver_max_seconds", 12.0)),
            "total_solver_seconds": float(legacy.get("total_solver_seconds", 25.0)),
            "solver_workers": int(legacy.get("solver_workers", 4)),
        },
        "milp_weights": legacy.get(
            "milp_weights",
            {
                "repeat_weight": 5,
                "group_weight": 2,
                "diversity_weight": 1,
                "pantry_weight": 1,
            },
        ),
        "hard_rule_mode": legacy.get("hard_rule_mode", "strict"),
        "allow_unsafe_overrides": bool(legacy.get("allow_unsafe_overrides", False)),
    }

    # Allow explicit legacy single-value knobs to map into v2 namespaces.
    if "max_pool_size" in legacy:
        migrated.setdefault("stage1", {})["max_candidates_per_slot"] = int(legacy["max_pool_size"])
    if "shortlist_keep_min" in legacy:
        migrated.setdefault("stage1", {})["pantry_match_threshold"] = 0
    if "policy_name" in legacy and isinstance(legacy.get("policy_name"), str):
        migrated["policy_name"] = legacy["policy_name"]
    return migrated


def load_policy(payload: Dict[str, Any]) -> PlannerPolicyConfig:
    migrated = _legacy_to_v2(payload)
    return PlannerPolicyConfig.model_validate(migrated)


def default_policy() -> PlannerPolicyConfig:
    return PlannerPolicyConfig()


def resolve_policy_for_environment(payload: Dict[str, Any], environment: str) -> Dict[str, Any]:
    policy = load_policy(payload)
    return policy.to_runtime_dict(environment=environment)
