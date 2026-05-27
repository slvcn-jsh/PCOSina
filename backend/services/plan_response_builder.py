from __future__ import annotations

import uuid

from typing import Any

from domain.models import GeneratePlanRequest, GeneratePlanResponse
from services.meal_planner import planner_contract_summary, profile_rule_summary, resolve_budget_weekly, validate_profile


def _reason_codes_from_message(msg: str, *, budget_exceeded_stage: str | None = None) -> list[str]:
    text = (msg or "").lower()
    codes: list[str] = []
    if budget_exceeded_stage or "timed out" in text or "time budget" in text:
        codes.append("PLANNER_TIMEOUT")
        return codes
    if "no safe recipes found" in text:
        codes.append("NO_SAFE_CANDIDATES")
    if "catalog nutrition coverage" in text or "nutrition coverage is insufficient" in text:
        codes.append("CATALOG_NUTRITION_GAP")
    if "conflicting restrictions" in text:
        codes.append("CONFLICTING_RESTRICTIONS")
    if "conflicting profile" in text:
        codes.append("CONFLICTING_PROFILE")
    if "requires a weekly budget" in text:
        codes.append("MISSING_BUDGET_INPUT")
    if "variety first priority conflicts" in text:
        codes.append("CONFLICTING_PRIORITIES")
    if "max cooking time must stay" in text:
        codes.append("INVALID_MAX_COOK_TIME")
    if "only mealsperday" in text:
        codes.append("UNSUPPORTED_MEAL_SLOTS")
    if "infeasible" in text:
        codes.append("MODEL_INFEASIBLE")
    if not codes:
        codes.append("UNKNOWN_INFEASIBILITY")
    return codes


def _optional_int(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except Exception:
        return None


def _timing_summary(phase_timings: dict[str, Any], pricing_diagnostics: dict[str, Any]) -> dict[str, int | None]:
    return {
        "stage1PreprocessMs": _optional_int(phase_timings.get("stage1_preprocess")),
        "stage1ShortlistMs": _optional_int(phase_timings.get("stage1_shortlist")),
        "costEstimationMs": _optional_int(
            pricing_diagnostics.get("priceCostEstimationMs")
            if pricing_diagnostics
            else phase_timings.get("price_cost_estimation")
        ),
        "solverMs": _optional_int(phase_timings.get("solver")),
        "totalPlannerMs": _optional_int(phase_timings.get("planner_total")),
    }


def _guidance_from_profile(
    request: GeneratePlanRequest,
    reason_codes: list[str],
    diagnostics_summary: dict | None = None,
) -> tuple[list[str], list[str]]:
    profile = request.profile
    guidance: list[str] = []
    relaxations: list[str] = []
    diagnostics_summary = diagnostics_summary or {}
    conflict_message = str(diagnostics_summary.get("profileConflict") or "").strip()
    exclusion_summary = diagnostics_summary.get("candidateExclusionSummary") or {}
    budget_exceeded_stage = str(diagnostics_summary.get("budgetExceededStage") or "").strip()

    if conflict_message:
        guidance.append(conflict_message)

    if "PLANNER_TIMEOUT" in reason_codes:
        guidance.append(
            "Planner timed out while pricing, filtering, or optimizing recipes. Please retry or relax non-safety constraints if this continues."
        )
    if "CONFLICTING_RESTRICTIONS" in reason_codes:
        guidance.append("Your current restriction combination conflicts. Remove one conflicting restriction and retry.")
    if "CATALOG_NUTRITION_GAP" in reason_codes:
        guidance.append(
            "The recipe catalog does not currently have enough source-backed meals to satisfy the nutrition bounds for this profile."
        )
        relaxations.append("Use reviewed nutrition corrections or add validated recipes before retrying this profile.")
    if int(exclusion_summary.get("allergy") or 0) > 0:
        guidance.append("Allergy rules removed some candidate meals before optimization.")
    if int(exclusion_summary.get("restriction") or 0) > 0:
        guidance.append("Restriction rules removed some candidate meals before optimization.")
    if int(exclusion_summary.get("prep_time") or 0) > 0:
        guidance.append("Cooking-time limits removed some candidate meals before optimization.")
    if int(exclusion_summary.get("pantry") or 0) > 0:
        guidance.append("Pantry matching filtered out some candidate meals before optimization.")
    if len(profile.dietaryRestrictions or []) >= 3:
        guidance.append("Too many active restrictions can remove all candidates. Temporarily relax one non-safety preference.")
        relaxations.append("Reduce non-safety dietary preferences by one level.")
    if profile.weeklyBudgetPhp and profile.weeklyBudgetPhp < 900:
        guidance.append("Current weekly budget is very tight for 21 meals. Consider increasing budget slightly.")
        relaxations.append("Increase weekly budget by at least 10%.")
    if profile.maxCookingTimeMinutes and profile.maxCookingTimeMinutes < 20:
        guidance.append("Very strict cooking-time limits can prevent feasible planning.")
        relaxations.append("Increase max cooking time by 10-15 minutes.")
    if budget_exceeded_stage and "PLANNER_TIMEOUT" not in reason_codes:
        guidance.append(f"Planner hit its time budget during {budget_exceeded_stage.replace('_', ' ')}.")
    if not guidance:
        guidance.append("No safe plan was found with the current hard constraints.")
        guidance.append("Update non-safety preferences and retry. Safety and allergy rules remain strict.")
        relaxations.append("Adjust variety preference or budget while preserving allergy and restriction safety.")
    return list(dict.fromkeys(guidance)), list(dict.fromkeys(relaxations))


def build_no_safe_plan_response(
    request: GeneratePlanRequest,
    request_id: str,
    message: str,
    policy_version: str,
    started_ms: int,
    completed_ms: int,
    diagnostics_ref: str | None = None,
    telemetry: dict | None = None,
) -> GeneratePlanResponse:
    telemetry = telemetry or {}
    stage1_diag = dict((telemetry.get("stage1_diag") or {}))
    phase_timings = dict(telemetry.get("phase_timings_ms") or {})
    pricing_diagnostics = dict(telemetry.get("pricing_diagnostics") or stage1_diag.get("pricing_diagnostics") or {})
    budget_exceeded_stage = str(telemetry.get("budget_exceeded_stage") or "").strip() or None
    reason_codes = _reason_codes_from_message(message, budget_exceeded_stage=budget_exceeded_stage)
    profile = request.profile
    budget_weekly = resolve_budget_weekly(profile)
    diagnostics_summary = {
        "reasonCodes": reason_codes,
        "summary": message,
        "profileConflict": validate_profile(profile),
        "profileRuleEffects": profile_rule_summary(profile, budget_weekly),
        "plannerContract": planner_contract_summary(profile, budget_weekly),
        "candidateExclusionSummary": dict(stage1_diag.get("exclusion_summary") or {}),
        "candidateExclusionDetailCounts": dict(
            stage1_diag.get("exclusion_detail_counts") or {}
        ),
        "nutritionFeasibility": dict(stage1_diag.get("nutrition_feasibility") or {}),
        "budgetExceededStage": budget_exceeded_stage,
        "timeoutStage": budget_exceeded_stage,
        "candidateCountPre": _optional_int(telemetry.get("candidate_count_pre")),
        "candidateCountPost": _optional_int(telemetry.get("candidate_count_post")),
        "candidateCountsComputed": {
            "pre": telemetry.get("candidate_count_pre") is not None,
            "post": telemetry.get("candidate_count_post") is not None,
        },
        "pricingDiagnostics": pricing_diagnostics,
        "timingSummary": _timing_summary(phase_timings, pricing_diagnostics),
        "solverBudget": telemetry.get("solver_budget") or {},
        "phaseTimingsMs": phase_timings,
    }
    guidance, relaxations = _guidance_from_profile(request, reason_codes, diagnostics_summary)
    return GeneratePlanResponse(
        weekLabel=f"PCOSINA {request.days}-Day Plan",
        days=[],
        status="no-safe-plan",
        message=message,
        explanation={
            "profileRuleEffects": diagnostics_summary["profileRuleEffects"],
            "plannerContract": diagnostics_summary["plannerContract"],
            "candidateExclusionSummary": diagnostics_summary["candidateExclusionSummary"],
            "budgetHardCapApplied": bool(budget_weekly),
        },
        requestId=request_id,
        planId=None,
        policyVersion=policy_version,
        machineReasonCodes=reason_codes,
        humanGuidance=guidance,
        suggestedRelaxations=relaxations,
        diagnosticsReference=diagnostics_ref or request_id,
        diagnosticsSummary=diagnostics_summary,
        solverMetadata={
            "solverName": "OR-Tools CP-SAT",
            "authorityStage": "stage2",
            "authoritative": True,
        },
        timestamps={
            "requestedAtMs": started_ms,
            "completedAtMs": completed_ms,
        },
    )


def freshen_cached_plan_response(
    cached: GeneratePlanResponse,
    *,
    request_id: str,
    started_ms: int,
    completed_ms: int,
) -> GeneratePlanResponse:
    payload = cached.model_dump() if hasattr(cached, "model_dump") else cached.dict()
    original_request_id = str(payload.get("requestId") or "").strip()
    payload["requestId"] = request_id
    if str(payload.get("status") or "").strip().lower() == "success":
        payload["planId"] = uuid.uuid4().hex
    diagnostics_ref = str(payload.get("diagnosticsReference") or "").strip()
    if diagnostics_ref in ("", original_request_id):
        payload["diagnosticsReference"] = request_id
    timestamps = dict(payload.get("timestamps") or {})
    timestamps["requestedAtMs"] = started_ms
    timestamps["completedAtMs"] = completed_ms
    payload["timestamps"] = timestamps
    return GeneratePlanResponse(**payload)
