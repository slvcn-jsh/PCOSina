from __future__ import annotations

import uuid

from domain.models import GeneratePlanRequest, GeneratePlanResponse


def _reason_codes_from_message(msg: str) -> list[str]:
    text = (msg or "").lower()
    codes: list[str] = []
    if "no safe recipes found" in text:
        codes.append("NO_SAFE_CANDIDATES")
    if "conflicting restrictions" in text:
        codes.append("CONFLICTING_RESTRICTIONS")
    if "only mealsperday" in text:
        codes.append("UNSUPPORTED_MEAL_SLOTS")
    if "infeasible" in text:
        codes.append("MODEL_INFEASIBLE")
    if not codes:
        codes.append("UNKNOWN_INFEASIBILITY")
    return codes


def _guidance_from_profile(request: GeneratePlanRequest, reason_codes: list[str]) -> tuple[list[str], list[str]]:
    profile = request.profile
    guidance: list[str] = []
    relaxations: list[str] = []

    if "CONFLICTING_RESTRICTIONS" in reason_codes:
        guidance.append("Your current restriction combination conflicts. Remove one conflicting restriction and retry.")
    if len(profile.dietaryRestrictions or []) >= 3:
        guidance.append("Too many active restrictions can remove all candidates. Temporarily relax one non-safety preference.")
        relaxations.append("Reduce non-safety dietary preferences by one level.")
    if profile.weeklyBudgetPhp and profile.weeklyBudgetPhp < 900:
        guidance.append("Current weekly budget is very tight for 21 meals. Consider increasing budget slightly.")
        relaxations.append("Increase weekly budget by at least 10%.")
    if profile.maxCookingTimeMinutes and profile.maxCookingTimeMinutes < 20:
        guidance.append("Very strict cooking-time limits can prevent feasible planning.")
        relaxations.append("Increase max cooking time by 10-15 minutes.")
    if not guidance:
        guidance.append("No safe plan was found with the current hard constraints.")
        guidance.append("Update non-safety preferences and retry. Safety and allergy rules remain strict.")
        relaxations.append("Adjust variety preference or budget while preserving allergy and restriction safety.")
    return guidance, relaxations


def build_no_safe_plan_response(
    request: GeneratePlanRequest,
    request_id: str,
    message: str,
    policy_version: str,
    started_ms: int,
    completed_ms: int,
    diagnostics_ref: str | None = None,
) -> GeneratePlanResponse:
    reason_codes = _reason_codes_from_message(message)
    guidance, relaxations = _guidance_from_profile(request, reason_codes)
    return GeneratePlanResponse(
        weekLabel=f"PCOSINA {request.days}-Day Plan",
        days=[],
        status="no-safe-plan",
        message=message,
        requestId=request_id,
        planId=None,
        policyVersion=policy_version,
        machineReasonCodes=reason_codes,
        humanGuidance=guidance,
        suggestedRelaxations=relaxations,
        diagnosticsReference=diagnostics_ref or request_id,
        diagnosticsSummary={
            "reasonCodes": reason_codes,
            "summary": message,
        },
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
