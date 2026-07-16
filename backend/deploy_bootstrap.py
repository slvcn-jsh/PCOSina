"""Run production database migrations and idempotent seed operations once per deploy."""

from __future__ import annotations

import time

import database
import policy_store
from price_catalog import invalidate_override_cache as invalidate_price_rule_cache


def _result_summary(result) -> str:
    if result is None:
        return "none"
    if not isinstance(result, dict):
        return type(result).__name__
    keys = (
        "id",
        "version_number",
        "sourceCount",
        "beforeCount",
        "afterCount",
        "insertedCount",
        "updatedCount",
        "skippedExistingCount",
        "deactivatedMissingSeedCount",
    )
    parts = [f"{key}={result[key]}" for key in keys if key in result]
    return ",".join(parts) or f"dict_keys={len(result)}"


def _run_stage(name: str, callback):
    started = time.perf_counter()
    print(f"DEPLOY_BOOTSTRAP stage={name} status=started", flush=True)
    try:
        result = callback()
    except Exception as exc:
        elapsed_ms = round((time.perf_counter() - started) * 1000)
        print(
            "DEPLOY_BOOTSTRAP "
            f"stage={name} status=failed elapsed_ms={elapsed_ms} "
            f"error_type={type(exc).__name__} error={exc}",
            flush=True,
        )
        raise
    elapsed_ms = round((time.perf_counter() - started) * 1000)
    print(
        "DEPLOY_BOOTSTRAP "
        f"stage={name} status=completed elapsed_ms={elapsed_ms} "
        f"result={_result_summary(result)}",
        flush=True,
    )
    return result


def main() -> int:
    started = time.perf_counter()
    print("DEPLOY_BOOTSTRAP status=started", flush=True)
    _run_stage("application_schema", database.init_db)
    _run_stage(
        "recipe_seed",
        lambda: database.seed_recipes(force_reseed=True, deactivate_missing_seed=True),
    )
    _run_stage("reviewed_price_seed", database.seed_reviewed_price_rules)
    invalidate_price_rule_cache()
    _run_stage("nutrition_correction_seed", database.seed_nutrition_corrections)
    _run_stage("policy_schema", policy_store.init_policy_store)
    _run_stage(
        "default_policy",
        lambda: policy_store.ensure_default_policy(actor="render-predeploy"),
    )
    elapsed_ms = round((time.perf_counter() - started) * 1000)
    print(f"DEPLOY_BOOTSTRAP status=completed elapsed_ms={elapsed_ms}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
