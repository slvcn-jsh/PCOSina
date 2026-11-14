from __future__ import annotations

import json
import os
import time
import uuid

if (
    os.getenv("RENDER", "").strip()
    or os.getenv("DATABASE_URL", "").strip().lower().startswith(("postgres://", "postgresql://"))
):
    os.environ.setdefault("PCOSINA_DB_POOL_MIN_SIZE", "0")
    os.environ.setdefault("PCOSINA_DB_POOL_MAX_SIZE", "2")
    os.environ.setdefault("PCOSINA_DB_POOL_TIMEOUT_SECONDS", "30")

import database
import policy_store
import queue_broker
from db_url import is_postgres_database_url
from ml_events import build_event, uid_hash, validate_event
from policy_config import default_policy, resolve_policy_for_environment
from domain.models import GeneratePlanRequest, GeneratePlanResponse
from services.meal_planner import solve_meal_plan
from services.plan_response_builder import build_no_safe_plan_response
from price_catalog import invalidate_override_cache as invalidate_price_rule_cache

WORKER_ID = os.getenv("PCOSINA_WORKER_ID", f"worker-{uuid.uuid4().hex[:8]}")
POLL_SECONDS = float(os.getenv("PCOSINA_WORKER_POLL_SECONDS", "1.0"))
QUEUE_BROKER = queue_broker.build_broker_from_env()


def _seed_reviewed_price_rules_on_startup() -> bool:
    configured = os.getenv("PCOSINA_SEED_REVIEWED_PRICE_RULES", "").strip().lower()
    if configured:
        return configured in ("1", "true", "yes", "on")
    return True


def _bootstrap_database_on_startup() -> bool:
    configured = os.getenv("PCOSINA_BOOTSTRAP_ON_STARTUP", "").strip().lower()
    if configured:
        return configured in ("1", "true", "yes", "on")
    if is_postgres_database_url(os.getenv("DATABASE_URL", "")):
        return False
    if os.getenv("RENDER", "").strip():
        return False
    environment = os.getenv("PCOSINA_ENV", "development").strip().lower()
    return environment not in ("prod", "production")


def _inc_diag(metric_key: str, delta: int = 1) -> None:
    try:
        database.increment_plan_job_diagnostic(metric_key, delta=delta)
    except Exception:
        # Never block worker progression due to diagnostics side-channel failures.
        pass


def _runtime_policy():
    active = policy_store.get_active_policy()
    if active and isinstance(active.get("policy"), dict):
        env = os.getenv("PCOSINA_ENV", "production").strip().lower()
        return resolve_policy_for_environment(active["policy"], env), f"policy-v{active.get('version_number')}:{active.get('id')}"
    return default_policy().to_runtime_dict(environment=os.getenv("PCOSINA_ENV", "production").strip().lower()), "default:2.0.0"


def _policy_value(policy: dict, path: str, default):
    current = policy
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return default
        current = current.get(part)
    return default if current is None else current


def _reason_feedback_features_for_uid(uid: str | None) -> dict:
    uid_token = str(uid or "").strip()
    if not uid_token:
        return {}
    getter = getattr(database, "get_reason_feedback_features", None)
    if not callable(getter):
        return {}
    try:
        return getter(uid_hash(uid_token))
    except Exception:
        return {}


def _solve_with_telemetry(
    request: GeneratePlanRequest,
    recipes: list[dict],
    policy_payload: dict,
    *,
    reason_feedback_features: dict | None = None,
):
    telemetry: dict = {}
    ml_feature_context = {
        "reason_feedback_features": reason_feedback_features or {},
    }
    try:
        result, msg, explanation = solve_meal_plan(
            request,
            recipes,
            policy=policy_payload,
            telemetry_out=telemetry,
            ml_feature_context=ml_feature_context,
        )
    except TypeError as exc:
        if "ml_feature_context" in str(exc):
            result, msg, explanation = solve_meal_plan(
                request,
                recipes,
                policy=policy_payload,
                telemetry_out=telemetry,
            )
        elif "telemetry_out" in str(exc):
            result, msg, explanation = solve_meal_plan(request, recipes, policy=policy_payload)
        else:
            raise
    return result, msg, explanation, telemetry


def _emit_ml_event(
    event_name: str,
    payload: dict,
    *,
    uid: str | None,
    request_id: str | None,
    policy_version: str | None,
) -> None:
    event = build_event(
        event_name=event_name,
        uid=uid,
        request_id=request_id,
        policy_version=policy_version,
        payload=payload,
    )
    valid, error = validate_event(event)
    if not valid:
        print("ML_EVENT_DROPPED", json.dumps({"event_name": event_name, "error": error}, sort_keys=True))
        return
    database.record_ml_event(event)
    print("ML_EVENT", json.dumps(event, sort_keys=True))


def _emit_planner_event(event: str, payload: dict, *, uid: str | None = None, policy_version: str | None = None) -> None:
    request_id = str(payload.get("requestId") or payload.get("request_id") or "none")
    normalized_payload = dict(payload)
    normalized_payload.setdefault("reason_codes", normalized_payload.get("reasonCodes", []))
    normalized_payload.setdefault("runtime_ms", normalized_payload.get("runtimeMs"))
    if "days" in normalized_payload:
        normalized_payload.setdefault("meals_per_day", normalized_payload.get("mealsPerDay"))
    _emit_ml_event(
        event_name=event,
        payload=normalized_payload,
        uid=uid,
        request_id=request_id,
        policy_version=policy_version or "unknown",
    )


def _optional_int(value):
    if value is None:
        return None
    try:
        return int(value)
    except Exception:
        return None


def _emit_planner_timing_log(
    *,
    request_id: str,
    policy_version: str | None,
    runtime_ms: int,
    telemetry: dict | None,
) -> None:
    telemetry_payload = dict(telemetry or {})
    payload = {
        "requestId": str(request_id or "none"),
        "policyVersion": str(policy_version or "unknown"),
        "runtimeMs": max(0, int(runtime_ms or 0)),
        "candidateCountPre": _optional_int(telemetry_payload.get("candidate_count_pre")),
        "candidateCountPost": _optional_int(telemetry_payload.get("candidate_count_post")),
        "rankingStrategy": str(telemetry_payload.get("ranking_strategy") or "unknown"),
        "phaseTimingsMs": telemetry_payload.get("phase_timings_ms") or {},
        "stage1Diag": telemetry_payload.get("stage1_diag") or {},
        "pricingDiagnostics": telemetry_payload.get("pricing_diagnostics") or {},
        "solverBudget": telemetry_payload.get("solver_budget") or {},
        "budgetDiagnostics": telemetry_payload.get("budget_diagnostics") or {},
        "budgetExceededStage": telemetry_payload.get("budget_exceeded_stage"),
        "solvePairDiagnostics": telemetry_payload.get("solve_pair_diagnostics") or [],
    }
    print("PLANNER_TIMING", json.dumps(payload, sort_keys=True))


def _emit_async_failure_event(
    job_id: str,
    *,
    uid: str | None,
    policy_version: str | None,
    runtime_ms: int,
    reason_codes: list[str],
    failure_status: str,
    error_type: str | None = None,
) -> None:
    payload = {
        "requestId": job_id,
        "status": "error",
        "runtimeMs": max(0, int(runtime_ms or 0)),
        "policyVersion": policy_version or "unknown",
        "reasonCodes": reason_codes,
        "failureStatus": failure_status,
    }
    if error_type:
        payload["errorType"] = error_type
    _emit_planner_event(
        "async_solver_completed",
        payload,
        uid=uid,
        policy_version=policy_version,
    )


def _backoff_sleep_ms(attempt_count: int, backoff: list[int]) -> int:
    if not backoff:
        return 0
    idx = max(0, min(len(backoff) - 1, attempt_count - 1))
    return int(max(0, backoff[idx]))


def _claim_job() -> dict | None:
    if QUEUE_BROKER.is_enabled():
        signal = QUEUE_BROKER.pop(timeout_seconds=POLL_SECONDS)
        if signal:
            _inc_diag("queue_worker_broker_signal_received_total")
            claimed = database.claim_plan_job_by_id(str(signal), WORKER_ID)
            if claimed:
                _inc_diag("queue_worker_broker_claimed_total")
                return claimed
            _inc_diag("queue_worker_broker_stale_signal_total")
        else:
            _inc_diag("queue_worker_broker_empty_poll_total")
    return database.claim_next_plan_job(WORKER_ID)


def run_once() -> bool:
    job = _claim_job()
    if not job:
        _inc_diag("queue_worker_idle_polls_total")
        return False

    _inc_diag("queue_worker_claimed_total")
    job_id = job.get("id")
    payload = job.get("request")
    uid = str(job.get("ownerUid") or "").strip() or None
    attempt_count = int(job.get("attemptCount") or 0)
    policy_payload, policy_version = _runtime_policy()
    dead_letter_threshold = int(_policy_value(policy_payload, "sync_offline.dead_letter_threshold", 5))
    retry_backoff = _policy_value(policy_payload, "sync_offline.sync_retry_backoff", [1000, 3000, 5000]) or [1000]
    if not isinstance(retry_backoff, list):
        retry_backoff = [1000]
    failure_status = "dead-letter" if attempt_count >= dead_letter_threshold else "queued"
    normalized_backoff: list[int] = []
    for value in retry_backoff:
        try:
            parsed = int(value)
        except Exception:
            continue
        if parsed > 0:
            normalized_backoff.append(parsed)
    backoff_ms = _backoff_sleep_ms(attempt_count, normalized_backoff or [1000])

    if not payload:
        _inc_diag("queue_worker_failures_total")
        _inc_diag("queue_worker_dead_letter_total" if failure_status == "dead-letter" else "queue_worker_retry_queued_total")
        next_attempt_at = int(time.time() * 1000) + backoff_ms if failure_status == "queued" else None
        database.update_plan_job(
            job_id,
            status=failure_status,
            error="Missing request payload",
            worker_id=WORKER_ID,
            next_attempt_at=next_attempt_at,
        )
        _emit_async_failure_event(
            str(job_id or "none"),
            uid=uid,
            policy_version=policy_version,
            runtime_ms=0,
            reason_codes=["MISSING_REQUEST_PAYLOAD"],
            failure_status=failure_status,
        )
        if failure_status == "queued" and backoff_ms > 0:
            _inc_diag("queue_worker_backoff_applied_total")
            time.sleep(backoff_ms / 1000.0)
        return True

    try:
        req = GeneratePlanRequest.model_validate(payload)
    except Exception as exc:
        _inc_diag("queue_worker_failures_total")
        _inc_diag("queue_worker_dead_letter_total" if failure_status == "dead-letter" else "queue_worker_retry_queued_total")
        next_attempt_at = int(time.time() * 1000) + backoff_ms if failure_status == "queued" else None
        database.update_plan_job(
            job_id,
            status=failure_status,
            error=f"Invalid request payload: {exc}",
            worker_id=WORKER_ID,
            next_attempt_at=next_attempt_at,
        )
        _emit_async_failure_event(
            str(job_id or "none"),
            uid=uid,
            policy_version=policy_version,
            runtime_ms=0,
            reason_codes=["INVALID_REQUEST_PAYLOAD"],
            failure_status=failure_status,
            error_type=type(exc).__name__,
        )
        if failure_status == "queued" and backoff_ms > 0:
            _inc_diag("queue_worker_backoff_applied_total")
            time.sleep(backoff_ms / 1000.0)
        return True

    started_ms = int(time.time() * 1000)
    recipes = database.get_all_recipes()
    try:
        _emit_planner_event(
            "plan_generation_requested",
            {
                "requestId": job_id,
                "days": int(req.days or 7),
                "mealsPerDay": int(req.mealsPerDay or 3),
                "restrictionCount": len(req.profile.dietaryRestrictions or []),
                "allergyCount": len(req.profile.allergies or []),
                "budgetWeeklyPhp": req.profile.weeklyBudgetPhp,
                "maxCookingTimeMinutes": req.profile.maxCookingTimeMinutes,
                "async": True,
            },
            uid=uid,
            policy_version=policy_version,
        )
        result, msg, explanation, telemetry = _solve_with_telemetry(
            req,
            recipes,
            policy_payload,
            reason_feedback_features=_reason_feedback_features_for_uid(uid),
        )
    except Exception as exc:
        _inc_diag("queue_worker_failures_total")
        _inc_diag("queue_worker_dead_letter_total" if failure_status == "dead-letter" else "queue_worker_retry_queued_total")
        next_attempt_at = int(time.time() * 1000) + backoff_ms if failure_status == "queued" else None
        database.update_plan_job(
            job_id,
            status=failure_status,
            error=f"Solver exception: {exc}",
            worker_id=WORKER_ID,
            next_attempt_at=next_attempt_at,
        )
        _emit_async_failure_event(
            str(job_id or "none"),
            uid=uid,
            policy_version=policy_version,
            runtime_ms=max(0, int(time.time() * 1000) - started_ms),
            reason_codes=["SOLVER_EXCEPTION"],
            failure_status=failure_status,
            error_type=type(exc).__name__,
        )
        if failure_status == "queued" and backoff_ms > 0:
            _inc_diag("queue_worker_backoff_applied_total")
            time.sleep(backoff_ms / 1000.0)
        return True
    completed_ms = int(time.time() * 1000)
    runtime_ms = max(0, completed_ms - started_ms)

    if telemetry.get("stage1_candidates"):
        database.record_stage1_candidate_features(
            request_id=job_id,
            uid_hash=uid_hash(uid),
            candidates=telemetry.get("stage1_candidates") or [],
            selected_recipe_ids=telemetry.get("selected_recipe_ids") or [],
            ranking_strategy=str(telemetry.get("ranking_strategy") or "stage1_heuristic_with_ml_shadow"),
            model_version=str(telemetry.get("ml_model_version") or "shadow_v0"),
            generated_at_ms=completed_ms,
        )
    _emit_ml_event(
        event_name="stage1_candidates_scored",
        payload={
            "candidate_count_pre": _optional_int(telemetry.get("candidate_count_pre")),
            "candidate_count_post": _optional_int(telemetry.get("candidate_count_post")),
            "ranking_strategy": str(telemetry.get("ranking_strategy") or "stage1_heuristic_with_ml_shadow"),
            "ml_score_enabled": bool(telemetry.get("ml_score_enabled", True)),
            "ml_model_version": str(telemetry.get("ml_model_version") or "shadow_v0"),
            "phase_timings_ms": telemetry.get("phase_timings_ms") or {},
            "solver_budget": telemetry.get("solver_budget") or {},
            "budget_exceeded_stage": telemetry.get("budget_exceeded_stage"),
            "pricing_diagnostics": telemetry.get("pricing_diagnostics") or {},
        },
        uid=uid,
        request_id=job_id,
        policy_version=policy_version,
    )
    _emit_planner_timing_log(
        request_id=job_id,
        policy_version=policy_version,
        runtime_ms=runtime_ms,
        telemetry=telemetry,
    )

    if result:
        response = GeneratePlanResponse(
            weekLabel=f"PCOSINA {req.days}-Day Plan",
            days=result,
            status="success",
            message=msg,
            explanation=explanation,
            requestId=job_id,
            planId=uuid.uuid4().hex,
            groceryOutput=telemetry.get("grocery_output"),
            policyVersion=policy_version,
            diagnosticsSummary={
                "reasonCodes": [],
                "summary": "success",
                "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
                "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
                "budgetDiagnostics": telemetry.get("budget_diagnostics") or {},
                **(telemetry.get("budget_diagnostics") or {}),
                "groceryBudgetAuthority": (explanation or {}).get("groceryBudgetAuthority") if isinstance(explanation, dict) else None,
            },
            solverMetadata={
                "solverName": "OR-Tools CP-SAT",
                "authorityStage": "stage2",
                "authoritative": True,
                "runtimeMs": runtime_ms,
            },
            timestamps={"requestedAtMs": started_ms, "completedAtMs": completed_ms},
        )
        database.update_plan_job(
            job_id,
            status="done",
            result_json=json.dumps(response.model_dump()),
            worker_id=WORKER_ID,
        )
        _emit_planner_event(
            "async_solver_completed",
            {
                "requestId": job_id,
                "status": "success",
                "runtimeMs": runtime_ms,
                "policyVersion": policy_version,
                "reasonCodes": [],
                "candidateCountPre": _optional_int(telemetry.get("candidate_count_pre")),
                "candidateCountPost": (explanation or {}).get("candidatePoolSize"),
                "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
                "solverBudget": telemetry.get("solver_budget") or {},
                "budgetExceededStage": telemetry.get("budget_exceeded_stage"),
                "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
            },
            uid=uid,
            policy_version=policy_version,
        )
        _emit_ml_event(
            event_name="plan_generated",
            payload={
                "status": "success",
                "plan_id": response.planId,
                "slot_count": int(req.days or 7) * int(req.mealsPerDay or 3),
            },
            uid=uid,
            request_id=job_id,
            policy_version=policy_version,
        )
        _inc_diag("queue_worker_success_total")
        return True

    no_safe = build_no_safe_plan_response(
        request=req,
        request_id=job_id,
        message=msg,
        policy_version=policy_version,
        started_ms=started_ms,
        completed_ms=completed_ms,
        telemetry=telemetry,
    )
    no_safe.solverMetadata = dict(no_safe.solverMetadata or {})
    no_safe.solverMetadata["runtimeMs"] = runtime_ms
    database.update_plan_job(
        job_id,
        status="done",
        result_json=json.dumps(no_safe.model_dump()),
        worker_id=WORKER_ID,
    )
    _emit_planner_event(
        "async_solver_completed",
        {
            "requestId": job_id,
            "status": "no-safe-plan",
            "runtimeMs": runtime_ms,
            "policyVersion": policy_version,
            "reasonCodes": no_safe.machineReasonCodes,
            "candidateCountPre": _optional_int(telemetry.get("candidate_count_pre")),
            "candidateCountPost": _optional_int(telemetry.get("candidate_count_post")),
            "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
            "solverBudget": telemetry.get("solver_budget") or {},
            "budgetExceededStage": telemetry.get("budget_exceeded_stage"),
            "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
        },
        uid=uid,
        policy_version=policy_version,
    )
    _emit_ml_event(
        event_name="no_safe_plan_encountered",
        payload={"reason_codes": no_safe.machineReasonCodes},
        uid=uid,
        request_id=job_id,
        policy_version=policy_version,
    )
    _inc_diag("queue_worker_no_safe_total")
    return True


def main() -> int:
    if _bootstrap_database_on_startup():
        database.init_db()
        database.seed_recipes()
        if _seed_reviewed_price_rules_on_startup():
            database.seed_reviewed_price_rules()
            invalidate_price_rule_cache()
        policy_store.init_policy_store()
        policy_store.ensure_default_policy(actor="worker-bootstrap")
    else:
        print("Database bootstrap skipped; expecting the deploy bootstrap command to have completed.", flush=True)

    run_forever = os.getenv("PCOSINA_WORKER_RUN_FOREVER", "true").strip().lower() in ("1", "true", "yes", "on")
    if not run_forever:
        run_once()
        return 0

    while True:
        processed = run_once()
        if not processed:
            time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    raise SystemExit(main())
