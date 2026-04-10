from __future__ import annotations

import csv
from pathlib import Path
from typing import Any, Dict, List

import sys


ROOT = Path(__file__).resolve().parents[1]
BACKEND_DIR = ROOT / "backend"
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from policy_config import default_policy


REQUIRED_KEYS = [
    "nutrition.calorie_min",
    "nutrition.calorie_max",
    "nutrition.carb_min",
    "nutrition.carb_max",
    "nutrition.protein_min",
    "nutrition.protein_max",
    "nutrition.fat_min",
    "nutrition.fat_max",
    "nutrition.fiber_min",
    "nutrition.sodium_max",
    "nutrition.sugar_max",
    "nutrition.meal_distribution_targets",
    "nutrition.daily_tolerance_percent",
    "nutrition.weekly_tolerance_percent",
    "planning.planning_horizon_days",
    "planning.meals_per_day",
    "planning.snack_rules",
    "planning.recipe_repeat_limits",
    "planning.cuisine_diversity_weight",
    "planning.pantry_utilization_weight",
    "planning.grocery_cost_weight",
    "planning.prep_time_weight",
    "planning.acceptance_score_weight",
    "planning.substitution_penalty",
    "planning.infeasibility_relaxation_order",
    "stage1.max_candidates_per_slot",
    "stage1.ranking_cutoff",
    "stage1.similarity_threshold",
    "stage1.pantry_match_threshold",
    "stage1.exclusion_penalty_weights",
    "stage1.cold_start_defaults",
    "stage1.ML_shadow_enabled",
    "stage1.ML_canary_enabled",
    "stage1.ML_score_weight",
    "stage1.ML_score_cap",
    "solver.solver_time_limit_seconds",
    "solver.max_solution_count",
    "solver.optimality_gap_target",
    "solver.infeasibility_diagnostic_depth",
    "solver.worker_memory_limit",
    "solver.retry_attempts",
    "solver.timeout_ms",
    "solver.circuit_breaker_threshold",
    "solver.queue_priority_rules",
    "sync_offline.sync_batch_size",
    "sync_offline.sync_retry_backoff",
    "sync_offline.dead_letter_threshold",
    "sync_offline.local_cache_ttl",
    "sync_offline.conflict_resolution_policy",
    "sync_offline.reinstall_recovery_timeout",
    "sync_offline.offline_read_guarantees",
    "sre.latency_slo_p50_ms",
    "sre.latency_slo_p95_ms",
    "sre.latency_slo_p99_ms",
    "sre.crash_free_target",
    "sre.api_error_budget",
    "sre.canary_cohort_percent",
    "sre.rollback_trigger_thresholds",
    "sre.observability_sampling_rate",
    "security.token_ttl",
    "security.key_rotation_days",
    "security.audit_log_retention_days",
    "security.backup_retention_days",
    "security.encryption_required_fields",
]


ADDITIONAL_KEYS = [
    "planning.meal_min_calorie_target",
    "planning.group_limit_floor",
    "planning.diversity_min_token_target",
    "stage1.restricted_shortlist_multiplier",
    "stage1.budget_keep_min_count",
    "stage1.budget_keep_min_ratio",
    "stage1.minimum_candidates_required",
    "stage1.pool_cap_top_share",
    "solver.solver_max_seconds",
    "solver.total_solver_seconds",
    "solver.solver_workers",
    "sync_offline.local_cache_max_entries",
    "milp_weights.repeat_weight",
    "milp_weights.group_weight",
    "milp_weights.diversity_weight",
    "milp_weights.pantry_weight",
]


ALLOWED_RANGE: Dict[str, str] = {
    "nutrition.calorie_min": "800..4000",
    "nutrition.calorie_max": "1000..5000",
    "nutrition.carb_min": "0..700",
    "nutrition.carb_max": "50..900",
    "nutrition.protein_min": "0..400",
    "nutrition.protein_max": "20..500",
    "nutrition.fat_min": "0..250",
    "nutrition.fat_max": "20..350",
    "nutrition.fiber_min": "0..120",
    "nutrition.sodium_max": "500..7000",
    "nutrition.sugar_max": "0..250",
    "nutrition.meal_distribution_targets": "array sum~1.0",
    "nutrition.daily_tolerance_percent": "0.01..0.80",
    "nutrition.weekly_tolerance_percent": "0.0..0.5",
    "planning.planning_horizon_days": "1..31",
    "planning.meals_per_day": "1..6",
    "planning.snack_rules": "bounded object",
    "planning.recipe_repeat_limits": "int[] each 1..50",
    "planning.cuisine_diversity_weight": "0..100",
    "planning.pantry_utilization_weight": "0..100",
    "planning.grocery_cost_weight": "0..100",
    "planning.prep_time_weight": "0..100",
    "planning.acceptance_score_weight": "0..100",
    "planning.substitution_penalty": "0..100",
    "planning.infeasibility_relaxation_order": "non-empty string[]",
    "stage1.max_candidates_per_slot": "10..5000",
    "stage1.ranking_cutoff": "0.01..1.0",
    "stage1.similarity_threshold": "0..1.0",
    "stage1.pantry_match_threshold": "0..50",
    "stage1.exclusion_penalty_weights": "numeric map",
    "stage1.cold_start_defaults": "bounded object",
    "stage1.ML_shadow_enabled": "bool",
    "stage1.ML_canary_enabled": "bool",
    "stage1.ML_score_weight": "0..1.0",
    "stage1.ML_score_cap": "0..1.0",
    "solver.solver_time_limit_seconds": "1..300",
    "solver.max_solution_count": "1..100",
    "solver.optimality_gap_target": "0..1.0",
    "solver.infeasibility_diagnostic_depth": "1..1000",
    "solver.worker_memory_limit": "128..65536",
    "solver.retry_attempts": "0..20",
    "solver.timeout_ms": "1000..900000",
    "solver.circuit_breaker_threshold": "1..500",
    "solver.queue_priority_rules": "object",
    "sync_offline.sync_batch_size": "1..10000",
    "sync_offline.sync_retry_backoff": "int[] each 1..3600000",
    "sync_offline.dead_letter_threshold": "1..100",
    "sync_offline.local_cache_ttl": "1..86400",
    "sync_offline.conflict_resolution_policy": "string enum via validator",
    "sync_offline.reinstall_recovery_timeout": "1000..600000",
    "sync_offline.offline_read_guarantees": "non-empty string[]",
    "sre.latency_slo_p50_ms": "1..120000",
    "sre.latency_slo_p95_ms": "1..120000",
    "sre.latency_slo_p99_ms": "1..180000",
    "sre.crash_free_target": "0..1.0",
    "sre.api_error_budget": "0..1.0",
    "sre.canary_cohort_percent": "0..100",
    "sre.rollback_trigger_thresholds": "numeric map",
    "sre.observability_sampling_rate": "0..1.0",
    "security.token_ttl": "60..604800",
    "security.key_rotation_days": "1..3650",
    "security.audit_log_retention_days": "1..3650",
    "security.backup_retention_days": "1..3650",
    "security.encryption_required_fields": "non-empty string[]",
    "planning.meal_min_calorie_target": "50..1200",
    "planning.group_limit_floor": "1..14",
    "planning.diversity_min_token_target": "1..50",
    "stage1.restricted_shortlist_multiplier": "1.0..3.0",
    "stage1.budget_keep_min_count": "1..500",
    "stage1.budget_keep_min_ratio": "0..1.0",
    "stage1.minimum_candidates_required": "1..200",
    "stage1.pool_cap_top_share": "0.05..0.95",
    "solver.solver_max_seconds": "1..300",
    "solver.total_solver_seconds": "3..600",
    "solver.solver_workers": "1..32",
    "sync_offline.local_cache_max_entries": "1..50000",
    "milp_weights.repeat_weight": "0..100",
    "milp_weights.group_weight": "0..100",
    "milp_weights.diversity_weight": "0..100",
    "milp_weights.pantry_weight": "0..100",
}


METADATA: Dict[str, Dict[str, str]] = {
    "nutrition.calorie_min": {"old_location": "backend/services/meal_planner.py: target floor clamp", "safety_criticality": "high", "override_permissions": "admin_only_guarded", "notes": "migrated from hardcoded calorie floor"},
    "nutrition.daily_tolerance_percent": {"old_location": "backend/services/meal_planner.py: tolerance_levels default [0.2,0.3,0.4]", "safety_criticality": "high", "override_permissions": "admin_only_guarded", "notes": "controls feasible relaxation ladder"},
    "planning.planning_horizon_days": {"old_location": "backend/domain/models.py: GeneratePlanRequest.days default=7", "safety_criticality": "high", "override_permissions": "admin_only_guarded", "notes": "validated in API + solver"},
    "planning.meals_per_day": {"old_location": "backend/domain/models.py: GeneratePlanRequest.mealsPerDay default=3", "safety_criticality": "high", "override_permissions": "admin_only_guarded", "notes": "validated in API + solver"},
    "stage1.max_candidates_per_slot": {"old_location": "backend/services/meal_planner.py: shortlist limit constants", "safety_criticality": "high", "override_permissions": "admin_only_guarded", "notes": "controls stage-1 tractability"},
    "stage1.ranking_cutoff": {"old_location": "backend/services/meal_planner.py: shortlist_keep_ratio", "safety_criticality": "medium", "override_permissions": "admin_only", "notes": "budget-aware shortlist retain ratio"},
    "solver.solver_time_limit_seconds": {"old_location": "backend/services/meal_planner.py: PCOSINA_SOLVER_TIME_SECONDS", "safety_criticality": "high", "override_permissions": "ops_admin_only", "notes": "base per-attempt solver budget"},
    "solver.timeout_ms": {"old_location": "backend/services/meal_planner.py + backend/main.py runtime caps", "safety_criticality": "high", "override_permissions": "ops_admin_only", "notes": "global call timeout guard"},
    "sync_offline.local_cache_ttl": {"old_location": "backend/main.py: PLAN_CACHE_TTL_SECONDS", "safety_criticality": "medium", "override_permissions": "admin_only", "notes": "offline continuity cache ttl"},
    "security.token_ttl": {"old_location": "backend/main.py: IDEMPOTENCY_TTL_SECONDS", "safety_criticality": "high", "override_permissions": "security_admin_only", "notes": "token/idempotency retention policy"},
}


def _flatten(data: Dict[str, Any], prefix: str = "") -> Dict[str, Any]:
    out: Dict[str, Any] = {}
    for key, value in data.items():
        dotted = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            out.update(_flatten(value, dotted))
        else:
            out[dotted] = value
    return out


def _get_value(payload: Dict[str, Any], key: str) -> Any:
    current: Any = payload
    for part in key.split("."):
        if not isinstance(current, dict) or part not in current:
            raise KeyError(key)
        current = current[part]
    return current


def _default_metadata_for(key: str) -> Dict[str, str]:
    if key.startswith("nutrition.") or key.startswith("security.") or key.startswith("solver.circuit_breaker"):
        criticality = "high"
        permission = "admin_only_guarded"
    elif key.startswith("solver.") or key.startswith("sync_offline.") or key.startswith("sre."):
        criticality = "medium"
        permission = "ops_admin_only"
    else:
        criticality = "medium"
        permission = "admin_only"
    return {
        "old_location": "N/A (new explicit policy control)",
        "safety_criticality": criticality,
        "override_permissions": permission,
        "notes": "externalized into typed policy schema",
    }


def _build_rows() -> List[Dict[str, Any]]:
    default_payload = default_policy().to_runtime_dict()
    keys = REQUIRED_KEYS + [k for k in ADDITIONAL_KEYS if k not in REQUIRED_KEYS]
    rows: List[Dict[str, Any]] = []
    for key in keys:
        try:
            default_value = _get_value(default_payload, key)
        except KeyError as exc:
            raise ValueError(f"Missing key in policy default payload: {key}") from exc
        meta = _default_metadata_for(key)
        meta.update(METADATA.get(key, {}))
        rows.append(
            {
                "old_location": meta["old_location"],
                "new_config_key": key,
                "default": default_value,
                "allowed_range": ALLOWED_RANGE.get(key, "See schema bounds"),
                "safety_criticality": meta["safety_criticality"],
                "override_permissions": meta["override_permissions"],
                "notes": meta["notes"],
            }
        )
    return rows


def _write_csv(rows: List[Dict[str, Any]], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "old_location",
        "new_config_key",
        "default",
        "allowed_range",
        "safety_criticality",
        "override_permissions",
        "notes",
    ]
    with output_path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(fp, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def _write_md(rows: List[Dict[str, Any]], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Configuration Migration Report",
        "",
        "Generated by `scripts/generate_config_migration_report.py`.",
        "",
        f"- Required keys covered: `{len(REQUIRED_KEYS)}`",
        f"- Additional keys covered: `{len(rows) - len(REQUIRED_KEYS)}`",
        "",
        "| old location | new config key | default | allowed range | safety criticality | override permissions |",
        "|---|---|---:|---|---|---|",
    ]
    for row in rows:
        default_text = str(row["default"]).replace("\n", " ")
        lines.append(
            f"| {row['old_location']} | `{row['new_config_key']}` | `{default_text}` | {row['allowed_range']} | {row['safety_criticality']} | {row['override_permissions']} |"
        )
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    rows = _build_rows()
    csv_path = ROOT / "docs" / "roadmap" / "config_migration_report.csv"
    md_path = ROOT / "docs" / "roadmap" / "config_migration_report.md"
    _write_csv(rows, csv_path)
    _write_md(rows, md_path)
    print(f"Wrote {len(rows)} rows -> {csv_path}")
    print(f"Wrote markdown -> {md_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
