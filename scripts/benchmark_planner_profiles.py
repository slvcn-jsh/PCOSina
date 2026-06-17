#!/usr/bin/env python
from __future__ import annotations

import argparse
from collections import Counter
import csv
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional


ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402
from domain.models import GeneratePlanRequest, UserProfile  # noqa: E402
from policy_config import default_policy, load_policy  # noqa: E402
from services import meal_planner  # noqa: E402


DEFAULT_FIXTURE = ROOT / "benchmarks" / "canonical_scenarios" / "planner_realistic_profiles_20.json"
DEFAULT_OUTPUT_DIR = ROOT / "benchmarks" / "reports"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run the PCOSina realistic planner profile latency benchmark."
    )
    parser.add_argument("--fixture", default=str(DEFAULT_FIXTURE), help="Planner profile fixture JSON.")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR), help="Directory for JSON/CSV reports.")
    parser.add_argument("--runs", type=int, default=1, help="Runs per profile case.")
    parser.add_argument("--environment", default="production", help="Policy environment to resolve.")
    parser.add_argument("--policy-json", default="", help="Optional policy payload JSON file.")
    canonical_group = parser.add_mutually_exclusive_group()
    canonical_group.add_argument(
        "--canonical-features",
        action="store_true",
        default=None,
        help="Enable canonical Stage 1 features for local parity benchmarking.",
    )
    canonical_group.add_argument(
        "--no-canonical-features",
        dest="canonical_features",
        action="store_false",
        help="Disable canonical Stage 1 features for legacy comparison.",
    )
    parser.add_argument("--report-prefix", default="planner_realistic_profiles.local", help="Report filename prefix.")
    parser.add_argument("--max-runtime-ms", type=int, default=15000, help="Per-run max runtime threshold.")
    parser.add_argument("--p95-runtime-ms", type=int, default=12000, help="Suite P95 runtime threshold.")
    parser.add_argument("--max-failures", type=int, default=0, help="Allowed failed runs.")
    parser.add_argument("--fail-on-regression", action="store_true", help="Return non-zero on threshold failures.")
    parser.add_argument("--skip-seed", action="store_true", help="Skip database schema init and recipe seeding.")
    parser.add_argument(
        "--require-ml-ready",
        action="store_true",
        help="Fail local benchmarks unless the Stage 1 ML ranker loads a real model.",
    )
    parser.add_argument(
        "--live-base-url",
        default="",
        help="Optional deployed backend base URL. When set, the runner uses /generate-plan-async.",
    )
    parser.add_argument(
        "--auth-token",
        default="",
        help="Firebase ID token. Defaults to PCOSINA_BENCHMARK_AUTH_TOKEN.",
    )
    parser.add_argument(
        "--app-check-token",
        default="",
        help="Firebase App Check token. Defaults to PCOSINA_BENCHMARK_APP_CHECK_TOKEN.",
    )
    parser.add_argument("--poll-timeout-ms", type=int, default=120000, help="Live job polling timeout.")
    parser.add_argument("--poll-interval-ms", type=int, default=1000, help="Live job polling interval.")
    return parser


def _load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _percentile(values: List[float], percentile: float) -> Optional[float]:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    index = (len(ordered) - 1) * percentile
    lower = int(index)
    upper = min(lower + 1, len(ordered) - 1)
    fraction = index - lower
    return ordered[lower] + ((ordered[upper] - ordered[lower]) * fraction)


def _slot_count(plan: Any) -> int:
    if not plan:
        return 0
    if isinstance(plan, list):
        total = 0
        for day in plan:
            meals = day.get("meals", []) if isinstance(day, dict) else getattr(day, "meals", []) or []
            total += len(meals or [])
        return total
    if isinstance(plan, dict):
        return _slot_count(plan.get("days") or [])
    if hasattr(plan, "days"):
        return sum(len(getattr(day, "meals", []) or []) for day in (getattr(plan, "days", []) or []))
    return 0


def _selected_recipe_ids(plan: Any) -> List[str]:
    ids: List[str] = []
    for _day_index, _meal_label, recipe_id in _iter_plan_meals(plan):
        if recipe_id:
            ids.append(recipe_id)
    return ids


def _as_float(value: Any) -> Optional[float]:
    try:
        if value is None:
            return None
        return float(value)
    except Exception:
        return None


def _iter_plan_meals(plan: Any) -> Iterable[tuple[int, str, str]]:
    days: Iterable[Any]
    if isinstance(plan, list):
        days = plan
    elif isinstance(plan, dict):
        days = plan.get("days") or []
    else:
        days = getattr(plan, "days", []) or []
    for day_index, day in enumerate(days):
        meals = day.get("meals", []) if isinstance(day, dict) else getattr(day, "meals", []) or []
        for meal in meals or []:
            if isinstance(meal, dict):
                meal_label = str(meal.get("mealLabel", "") or "").strip()
                recipe_id = str(meal.get("recipeId", "") or "").strip()
            else:
                meal_label = str(getattr(meal, "mealLabel", "") or "").strip()
                recipe_id = str(getattr(meal, "recipeId", "") or "").strip()
            yield day_index, meal_label, recipe_id


def _recipe_tags_and_tokens(recipe: Dict[str, Any]) -> tuple[List[str], List[str]]:
    ing_tokens = list(recipe.get("_ing_tokens") or meal_planner.normalize_ingredients(recipe.get("ingredients", [])))
    tags = list(recipe.get("_tags") or meal_planner.infer_tags(recipe))
    return tags, ing_tokens


def _nutrition_bounds(policy: Dict[str, Any], explanation: Dict[str, Any]) -> Dict[str, tuple[int, int]]:
    tolerance = float(explanation.get("toleranceUsed") or 0.2)
    protein_min = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.protein_min", "protein_min"], 45))
    protein_max = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.protein_max", "protein_max"], 220))
    carb_min = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.carb_min", "carb_min"], 120))
    carb_max = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.carb_max", "carb_max"], 420))
    fat_min = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.fat_min", "fat_min"], 35))
    fat_max = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.fat_max", "fat_max"], 140))
    target_protein = int(explanation.get("targetProtein") or protein_min)
    target_carbs = int(explanation.get("targetCarbs") or carb_min)
    target_fats = int(explanation.get("targetFats") or fat_min)
    return {
        "proteinGrams": (
            max(protein_min, int(target_protein * (1 - tolerance))),
            min(protein_max, int(target_protein * (1 + tolerance))),
        ),
        "carbsGrams": (
            max(carb_min, int(target_carbs * (1 - tolerance))),
            min(carb_max, int(target_carbs * (1 + tolerance))),
        ),
        "fatsGrams": (
            max(fat_min, int(target_fats * (1 - tolerance))),
            min(fat_max, int(target_fats * (1 + tolerance))),
        ),
    }


def _validate_plan_constraints(
    plan: Any,
    *,
    profile: UserProfile,
    recipe_by_id: Dict[str, Dict[str, Any]],
    policy: Dict[str, Any],
    explanation: Dict[str, Any],
    expected_slots: int,
) -> Dict[str, Any]:
    violations: List[str] = []
    advisory_warnings: List[str] = []
    daily_nutrition: List[Dict[str, int]] = []
    selected: List[Dict[str, Any]] = []
    selected_ids: List[str] = []
    previous_recipe_id = ""

    max_cook = int(profile.maxCookingTimeMinutes or 0)
    for day_index, meal_label, recipe_id in _iter_plan_meals(plan):
        if not recipe_id:
            violations.append(f"day_{day_index + 1}:{meal_label}:missing_recipe_id")
            continue
        recipe = recipe_by_id.get(recipe_id)
        if recipe is None:
            violations.append(f"day_{day_index + 1}:{meal_label}:unknown_recipe:{recipe_id}")
            continue
        selected.append(recipe)
        selected_ids.append(recipe_id)

        allowed_meals = meal_planner.infer_allowed_meals(recipe.get("mealType"))
        if meal_label and meal_label not in allowed_meals:
            violations.append(f"{recipe_id}:meal_type:{meal_label}_not_allowed")

        minutes = int(recipe.get("minutes") or 0)
        if max_cook > 0 and minutes > max_cook:
            violations.append(f"{recipe_id}:prep_time:{minutes}_gt_{max_cook}")

        tags, ing_tokens = _recipe_tags_and_tokens(recipe)
        failures = meal_planner.restriction_failure_reasons(profile, tags, ing_tokens)
        violations.extend(f"{recipe_id}:{reason}" for reason in failures)

        if previous_recipe_id and previous_recipe_id == recipe_id:
            violations.append(f"{recipe_id}:adjacent_duplicate")
        previous_recipe_id = recipe_id

    if len(selected_ids) != expected_slots:
        violations.append(f"slot_count:{len(selected_ids)}_ne_{expected_slots}")

    max_per_week = int(explanation.get("maxPerWeek") or expected_slots)
    for recipe_id, count in Counter(selected_ids).items():
        if count > max_per_week:
            violations.append(f"{recipe_id}:repeat_count:{count}_gt_{max_per_week}")

    budget_weekly = meal_planner.resolve_budget_weekly(profile)
    estimated_cost = _as_float(
        explanation.get("estimatedWeeklyCost")
        if explanation.get("estimatedWeeklyCost") is not None
        else explanation.get("estimatedWeeklyCostPhp")
    )
    if budget_weekly and estimated_cost is not None and estimated_cost > budget_weekly + 0.5:
        violations.append(f"budget:{int(round(estimated_cost))}_gt_{int(round(budget_weekly))}")

    calorie_min = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.calorie_min", "calorie_min"], 1200))
    calorie_max = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.calorie_max", "calorie_max"], 3200))
    fiber_min = int(explanation.get("fiberMinTarget") or meal_planner._policy_get_legacy_aware(
        policy,
        ["nutrition.fiber_min", "fiber_min"],
        20,
    ))
    sugar_max = int(explanation.get("sugarMaxTarget") or meal_planner._policy_get_legacy_aware(
        policy,
        ["nutrition.sugar_max", "sugar_max"],
        50,
    ))
    sodium_max = int(meal_planner._policy_get_legacy_aware(policy, ["nutrition.sodium_max", "sodium_max"], 2300))
    macro_bounds = _nutrition_bounds(policy, explanation)

    meals_by_day: Dict[int, List[Dict[str, Any]]] = {}
    for day_index, _meal_label, recipe_id in _iter_plan_meals(plan):
        recipe = recipe_by_id.get(recipe_id)
        if recipe is not None:
            meals_by_day.setdefault(day_index, []).append(recipe)

    for day_index in sorted(meals_by_day):
        meals = meals_by_day[day_index]
        totals = {
            "calories": sum(int(recipe.get("calories") or 0) for recipe in meals),
            "proteinGrams": sum(int(recipe.get("proteinGrams") or 0) for recipe in meals),
            "carbsGrams": sum(int(recipe.get("carbsGrams") or 0) for recipe in meals),
            "fatsGrams": sum(int(recipe.get("fatsGrams") or 0) for recipe in meals),
            "fiberGrams": sum(int(recipe.get("fiberGrams") or 0) for recipe in meals),
            "sodiumMg": sum(int(recipe.get("sodiumMg") or 0) for recipe in meals),
            "sugarGrams": sum(int(recipe.get("sugarGrams") or 0) for recipe in meals),
        }
        daily_nutrition.append({"day": day_index + 1, **totals})
        if totals["calories"] < calorie_min or totals["calories"] > calorie_max:
            violations.append(f"day_{day_index + 1}:calories:{totals['calories']}_outside_{calorie_min}_{calorie_max}")
        for field, (lower, upper) in macro_bounds.items():
            value = totals[field]
            if value < lower or value > upper:
                violations.append(f"day_{day_index + 1}:{field}:{value}_outside_{lower}_{upper}")
        if totals["fiberGrams"] < fiber_min:
            violations.append(f"day_{day_index + 1}:fiber:{totals['fiberGrams']}_lt_{fiber_min}")
        if totals["sodiumMg"] > sodium_max:
            advisory_warnings.append(f"day_{day_index + 1}:sodium:{totals['sodiumMg']}_gt_{sodium_max}")
        if totals["sugarGrams"] > sugar_max:
            advisory_warnings.append(f"day_{day_index + 1}:sugar:{totals['sugarGrams']}_gt_{sugar_max}")

    return {
        "ok": not violations,
        "violations": sorted(set(violations)),
        "advisoryWarnings": sorted(set(advisory_warnings)),
        "dailyNutrition": daily_nutrition,
        "validatedHardRules": [
            "slot_count",
            "recipe_exists",
            "meal_type_compatibility",
            "max_cooking_time",
            "allergy_and_restriction_filters",
            "adjacent_duplicate_prevention",
            "repeat_limit",
            "weekly_budget_cap",
            "daily_calorie_bounds",
            "daily_macro_bounds",
            "daily_fiber_minimum",
        ],
        "advisoryRules": ["daily_sodium_max", "daily_sugar_max"],
    }


def _policy_payload(args: argparse.Namespace) -> Dict[str, Any]:
    if args.policy_json:
        policy = load_policy(_load_json(Path(args.policy_json))).to_runtime_dict(environment=args.environment)
    else:
        policy = default_policy().to_runtime_dict(environment=args.environment)
    if args.canonical_features is not None:
        stage1 = dict(policy.get("stage1") or {})
        stage1["canonical_features_enabled"] = bool(args.canonical_features)
        policy["stage1"] = stage1
    return policy


def _live_headers(args: argparse.Namespace) -> Dict[str, str]:
    auth_token = str(args.auth_token or "").strip() or str(
        os.getenv("PCOSINA_BENCHMARK_AUTH_TOKEN", "")
    ).strip()
    app_check_token = str(args.app_check_token or "").strip() or str(
        os.getenv("PCOSINA_BENCHMARK_APP_CHECK_TOKEN", "")
    ).strip()
    missing = []
    if not auth_token:
        missing.append("PCOSINA_BENCHMARK_AUTH_TOKEN")
    if not app_check_token:
        missing.append("PCOSINA_BENCHMARK_APP_CHECK_TOKEN")
    if missing:
        raise RuntimeError(
            "Live benchmark requires production mobile auth tokens: "
            + ", ".join(missing)
        )
    authorization = auth_token if auth_token.lower().startswith("bearer ") else f"Bearer {auth_token}"
    return {
        "Authorization": authorization,
        "X-Firebase-AppCheck": app_check_token,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }


def _join_url(base_url: str, path: str) -> str:
    return f"{base_url.rstrip('/')}/{path.lstrip('/')}"


def _http_json(
    method: str,
    url: str,
    *,
    headers: Dict[str, str],
    payload: Optional[Dict[str, Any]] = None,
    timeout_seconds: float = 30.0,
) -> Dict[str, Any]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} failed: HTTP {exc.code} {detail[:300]}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"{method} {url} failed: {exc}") from exc


def _fetch_live_catalog(base_url: str, headers: Dict[str, str]) -> List[Dict[str, Any]]:
    payload = _http_json(
        "GET",
        _join_url(base_url, "/recipes/catalog?limit=5000"),
        headers=headers,
        timeout_seconds=60,
    )
    if not isinstance(payload, list):
        raise RuntimeError("Live catalog response was not a list")
    return [dict(item) for item in payload if isinstance(item, dict)]


def _run_case(
    case: Dict[str, Any],
    *,
    run_index: int,
    recipes: List[Dict[str, Any]],
    recipe_by_id: Dict[str, Dict[str, Any]],
    policy: Dict[str, Any],
    days: int,
    meals_per_day: int,
) -> Dict[str, Any]:
    profile_payload = dict(case.get("profile") or {})
    profile_payload["displayName"] = f"{case.get('id', 'case')}-run-{run_index + 1}"
    request = GeneratePlanRequest(
        profile=UserProfile.model_validate(profile_payload),
        days=days,
        mealsPerDay=meals_per_day,
    )
    telemetry: Dict[str, Any] = {}
    started = time.perf_counter()
    plan, message, explanation = meal_planner.solve_meal_plan(
        request,
        recipes,
        policy=policy,
        telemetry_out=telemetry,
    )
    runtime_ms = int(round((time.perf_counter() - started) * 1000))
    slot_count = _slot_count(plan)
    expected_slots = days * meals_per_day
    pair_statuses = [
        str(pair.get("status") or "")
        for pair in (telemetry.get("solve_pair_diagnostics") or [])
        if isinstance(pair, dict)
    ]
    terminal_solver_status = next(
        (status for status in reversed(pair_statuses) if status in {"OPTIMAL", "FEASIBLE"}),
        pair_statuses[-1] if pair_statuses else "",
    )
    stage1_diag = telemetry.get("stage1_diag") if isinstance(telemetry.get("stage1_diag"), dict) else {}
    nutrition_feasibility = (
        stage1_diag.get("nutrition_feasibility")
        if isinstance(stage1_diag.get("nutrition_feasibility"), dict)
        else {}
    )
    explanation_payload = explanation if isinstance(explanation, dict) else {}
    constraint_validation = _validate_plan_constraints(
        plan,
        profile=request.profile,
        recipe_by_id=recipe_by_id,
        policy=policy,
        explanation=explanation_payload,
        expected_slots=expected_slots,
    ) if plan else {
        "ok": False,
        "violations": ["missing_plan"],
        "advisoryWarnings": [],
        "dailyNutrition": [],
        "validatedHardRules": [],
        "advisoryRules": [],
    }
    success = bool(plan) and message == "Success" and slot_count == expected_slots and terminal_solver_status in {
        "OPTIMAL",
        "FEASIBLE",
    } and bool(constraint_validation.get("ok"))
    estimated_cost = None
    if explanation_payload:
        estimated_cost = (
            explanation_payload.get("estimatedWeeklyCost")
            if explanation_payload.get("estimatedWeeklyCost") is not None
            else explanation_payload.get("estimatedWeeklyCostPhp")
        )
    return {
        "caseId": str(case.get("id") or ""),
        "userType": str(case.get("userType") or ""),
        "runIndex": run_index + 1,
        "status": "pass" if success else "fail",
        "success": success,
        "message": message,
        "runtimeMs": runtime_ms,
        "slotCount": slot_count,
        "expectedSlotCount": expected_slots,
        "candidateCountPre": telemetry.get("candidate_count_pre"),
        "candidateCountPost": telemetry.get("candidate_count_post"),
        "solverStatuses": pair_statuses,
        "terminalSolverStatus": terminal_solver_status,
        "reasonCodes": telemetry.get("reason_codes") or [],
        "budgetExceededStage": telemetry.get("budget_exceeded_stage"),
        "mlModelVersion": telemetry.get("ml_model_version"),
        "mlScoreEnabled": telemetry.get("ml_score_enabled"),
        "rankingStrategy": telemetry.get("ranking_strategy"),
        "nutritionFeasibilityOk": nutrition_feasibility.get("ok"),
        "nutritionGaps": nutrition_feasibility.get("gaps") or [],
        "advisoryNutritionGaps": nutrition_feasibility.get("advisoryGaps") or [],
        "estimatedWeeklyCostPhp": estimated_cost,
        "selectedRecipeIds": _selected_recipe_ids(plan),
        "constraintValidationOk": constraint_validation.get("ok"),
        "constraintViolations": constraint_validation.get("violations") or [],
        "advisoryConstraintWarnings": constraint_validation.get("advisoryWarnings") or [],
        "hardConstraintViolationCount": len(constraint_validation.get("violations") or []),
        "advisoryConstraintWarningCount": len(constraint_validation.get("advisoryWarnings") or []),
        "dailyNutrition": constraint_validation.get("dailyNutrition") or [],
    }


def _run_live_case(
    case: Dict[str, Any],
    *,
    run_index: int,
    base_url: str,
    headers: Dict[str, str],
    recipe_by_id: Dict[str, Dict[str, Any]],
    policy: Dict[str, Any],
    days: int,
    meals_per_day: int,
    poll_timeout_ms: int,
    poll_interval_ms: int,
) -> Dict[str, Any]:
    profile_payload = dict(case.get("profile") or {})
    profile_payload["displayName"] = f"{case.get('id', 'case')}-live-run-{run_index + 1}"
    request = GeneratePlanRequest(
        profile=UserProfile.model_validate(profile_payload),
        days=days,
        mealsPerDay=meals_per_day,
    )
    request_payload = request.model_dump()
    expected_slots = days * meals_per_day
    run_headers = dict(headers)
    run_headers["Idempotency-Key"] = (
        f"planner-bench-{case.get('id', 'case')}-{run_index + 1}-{int(time.time() * 1000)}"
    )
    started = time.perf_counter()
    job = _http_json(
        "POST",
        _join_url(base_url, "/generate-plan-async"),
        headers=run_headers,
        payload=request_payload,
        timeout_seconds=45,
    )
    job_id = str(job.get("jobId") or "").strip()
    if not job_id:
        runtime_ms = int(round((time.perf_counter() - started) * 1000))
        return {
            "caseId": str(case.get("id") or ""),
            "userType": str(case.get("userType") or ""),
            "runIndex": run_index + 1,
            "status": "fail",
            "success": False,
            "message": f"Missing live jobId: {job}",
            "runtimeMs": runtime_ms,
            "slotCount": 0,
            "expectedSlotCount": expected_slots,
            "candidateCountPre": None,
            "candidateCountPost": None,
            "solverStatuses": [],
            "terminalSolverStatus": "",
            "reasonCodes": ["LIVE_JOB_ID_MISSING"],
            "budgetExceededStage": None,
            "mlModelVersion": None,
            "mlScoreEnabled": None,
            "rankingStrategy": None,
            "nutritionFeasibilityOk": None,
            "nutritionGaps": [],
            "advisoryNutritionGaps": [],
            "estimatedWeeklyCostPhp": None,
            "selectedRecipeIds": [],
            "constraintValidationOk": False,
            "constraintViolations": ["missing_live_job_id"],
            "advisoryConstraintWarnings": [],
            "hardConstraintViolationCount": 1,
            "advisoryConstraintWarningCount": 0,
            "dailyNutrition": [],
        }

    deadline = time.perf_counter() + (max(1, int(poll_timeout_ms)) / 1000.0)
    poll_interval_seconds = max(0.1, int(poll_interval_ms) / 1000.0)
    last_job: Dict[str, Any] = job
    while time.perf_counter() < deadline:
        time.sleep(poll_interval_seconds)
        last_job = _http_json(
            "GET",
            _join_url(base_url, f"/plan-jobs/{job_id}"),
            headers=headers,
            timeout_seconds=30,
        )
        if str(last_job.get("status") or "").lower() in {
            "done",
            "failed",
            "dead-letter",
            "cancelled",
        }:
            break

    runtime_ms = int(round((time.perf_counter() - started) * 1000))
    result = last_job.get("result") if isinstance(last_job.get("result"), dict) else {}
    explanation_payload = result.get("explanation") if isinstance(result.get("explanation"), dict) else {}
    plan_days = result.get("days") if isinstance(result.get("days"), list) else []
    slot_count = _slot_count(plan_days)
    reason_codes = []
    diagnostics = result.get("diagnosticsSummary") if isinstance(result.get("diagnosticsSummary"), dict) else {}
    if isinstance(diagnostics.get("reasonCodes"), list):
        reason_codes = diagnostics.get("reasonCodes") or []
    constraint_validation = _validate_plan_constraints(
        plan_days,
        profile=request.profile,
        recipe_by_id=recipe_by_id,
        policy=policy,
        explanation=explanation_payload,
        expected_slots=expected_slots,
    ) if plan_days else {
        "ok": False,
        "violations": ["missing_plan"],
        "advisoryWarnings": [],
        "dailyNutrition": [],
        "validatedHardRules": [],
        "advisoryRules": [],
    }
    live_status = str(result.get("status") or last_job.get("status") or "").lower()
    success = (
        live_status == "success"
        and slot_count == expected_slots
        and bool(constraint_validation.get("ok"))
    )
    estimated_cost = (
        explanation_payload.get("estimatedWeeklyCost")
        if explanation_payload.get("estimatedWeeklyCost") is not None
        else explanation_payload.get("estimatedWeeklyCostPhp")
    )
    return {
        "caseId": str(case.get("id") or ""),
        "userType": str(case.get("userType") or ""),
        "runIndex": run_index + 1,
        "status": "pass" if success else "fail",
        "success": success,
        "message": str(result.get("message") or last_job.get("error") or live_status),
        "runtimeMs": runtime_ms,
        "slotCount": slot_count,
        "expectedSlotCount": expected_slots,
        "candidateCountPre": None,
        "candidateCountPost": (
            explanation_payload.get("candidatePoolSize")
            if isinstance(explanation_payload, dict)
            else None
        ),
        "solverStatuses": [str(live_status or "unknown").upper()],
        "terminalSolverStatus": str(live_status or "unknown").upper(),
        "reasonCodes": reason_codes,
        "budgetExceededStage": None,
        "mlModelVersion": None,
        "mlScoreEnabled": None,
        "rankingStrategy": None,
        "nutritionFeasibilityOk": None,
        "nutritionGaps": [],
        "advisoryNutritionGaps": [],
        "estimatedWeeklyCostPhp": estimated_cost,
        "selectedRecipeIds": _selected_recipe_ids(plan_days),
        "constraintValidationOk": constraint_validation.get("ok"),
        "constraintViolations": constraint_validation.get("violations") or [],
        "advisoryConstraintWarnings": constraint_validation.get("advisoryWarnings") or [],
        "hardConstraintViolationCount": len(constraint_validation.get("violations") or []),
        "advisoryConstraintWarningCount": len(constraint_validation.get("advisoryWarnings") or []),
        "dailyNutrition": constraint_validation.get("dailyNutrition") or [],
        "liveJobId": job_id,
        "liveJobStatus": last_job.get("status"),
    }


def _case_summary(case_id: str, user_type: str, rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    runtimes = [float(row["runtimeMs"]) for row in rows]
    passed = sum(1 for row in rows if row.get("success"))
    failed = len(rows) - passed
    return {
        "caseId": case_id,
        "userType": user_type,
        "runs": len(rows),
        "passed": passed,
        "failed": failed,
        "successRate": passed / len(rows) if rows else 0.0,
        "minRuntimeMs": min(runtimes) if runtimes else None,
        "avgRuntimeMs": statistics.fmean(runtimes) if runtimes else None,
        "p95RuntimeMs": _percentile(runtimes, 0.95),
        "maxRuntimeMs": max(runtimes) if runtimes else None,
        "slowestRunIndex": max(rows, key=lambda item: item.get("runtimeMs", 0)).get("runIndex") if rows else None,
        "terminalSolverStatuses": sorted({str(row.get("terminalSolverStatus") or "") for row in rows}),
    }


def _write_csv(path: Path, rows: List[Dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "caseId",
        "userType",
        "runIndex",
        "status",
        "runtimeMs",
        "slotCount",
        "candidateCountPre",
        "candidateCountPost",
        "terminalSolverStatus",
        "reasonCodes",
        "budgetExceededStage",
        "mlModelVersion",
        "mlScoreEnabled",
        "rankingStrategy",
        "nutritionFeasibilityOk",
        "estimatedWeeklyCostPhp",
        "constraintValidationOk",
        "hardConstraintViolationCount",
        "advisoryConstraintWarningCount",
    ]
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    key: (
                        ",".join(str(v) for v in row.get(key, []))
                        if key == "reasonCodes"
                        else row.get(key)
                    )
                    for key in fieldnames
                }
            )


def main() -> int:
    args = build_parser().parse_args()
    fixture_path = Path(args.fixture)
    output_dir = Path(args.output_dir)
    fixture = _load_json(fixture_path)
    cases = fixture.get("cases") if isinstance(fixture.get("cases"), list) else []
    if not cases:
        print(f"No benchmark cases found in {fixture_path}")
        return 2

    request_config = fixture.get("request") if isinstance(fixture.get("request"), dict) else {}
    days = int(request_config.get("days") or 7)
    meals_per_day = int(request_config.get("mealsPerDay") or 3)
    runs = max(1, int(args.runs or 1))

    live_base_url = str(args.live_base_url or "").strip()
    live_headers: Optional[Dict[str, str]] = None
    if live_base_url:
        live_headers = _live_headers(args)
        recipes = _fetch_live_catalog(live_base_url, live_headers)
    else:
        if not args.skip_seed:
            database.init_db()
            database.seed_recipes()
        recipes = database.get_all_recipes()
    recipe_by_id = {str(recipe.get("id") or ""): recipe for recipe in recipes}
    policy = _policy_payload(args)
    ml_ranker_state = None
    if not live_base_url:
        ml_ranker_state = meal_planner.get_stage1_ranker().state()
        if args.require_ml_ready and not ml_ranker_state.ready:
            print(f"Stage 1 ML ranker is not ready: {ml_ranker_state.error}")
            return 2

    started_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    run_rows: List[Dict[str, Any]] = []
    print(
        "case|run|status|runtime_ms|slots|terminal_solver|candidates|nutrition_ok"
    )
    for case in cases:
        for run_index in range(runs):
            if live_base_url:
                row = _run_live_case(
                    case,
                    run_index=run_index,
                    base_url=live_base_url,
                    headers=live_headers or {},
                    recipe_by_id=recipe_by_id,
                    policy=policy,
                    days=days,
                    meals_per_day=meals_per_day,
                    poll_timeout_ms=int(args.poll_timeout_ms),
                    poll_interval_ms=int(args.poll_interval_ms),
                )
            else:
                row = _run_case(
                    case,
                    run_index=run_index,
                    recipes=recipes,
                    recipe_by_id=recipe_by_id,
                    policy=policy,
                    days=days,
                    meals_per_day=meals_per_day,
                )
            run_rows.append(row)
            print(
                f"{row['caseId']}|{row['runIndex']}|{row['status']}|{row['runtimeMs']}|"
                f"{row['slotCount']}|{row['terminalSolverStatus']}|"
                f"{row['candidateCountPre']}->{row['candidateCountPost']}|"
                f"{row['nutritionFeasibilityOk']}"
            )

    grouped: Dict[str, List[Dict[str, Any]]] = {}
    for row in run_rows:
        grouped.setdefault(str(row.get("caseId") or ""), []).append(row)
    case_summaries = [
        _case_summary(case_id, rows[0].get("userType", ""), rows)
        for case_id, rows in sorted(grouped.items())
    ]
    runtimes = [float(row["runtimeMs"]) for row in run_rows]
    failures = [row for row in run_rows if not row.get("success")]
    slow_runs = [row for row in run_rows if int(row.get("runtimeMs") or 0) > int(args.max_runtime_ms)]
    suite_p95 = _percentile(runtimes, 0.95)
    suite = {
        "startedAt": started_at,
        "fixturePath": str(fixture_path),
        "environment": args.environment,
        "mode": "live" if live_base_url else "local",
        "liveBaseUrl": live_base_url or None,
        "mlRankerReady": None if ml_ranker_state is None else bool(ml_ranker_state.ready),
        "mlModelVersion": None if ml_ranker_state is None else ml_ranker_state.model_version,
        "mlRankerError": None if ml_ranker_state is None else ml_ranker_state.error,
        "recipeCount": len(recipes),
        "caseCount": len(cases),
        "runsPerCase": runs,
        "totalRuns": len(run_rows),
        "passedRuns": len(run_rows) - len(failures),
        "failedRuns": len(failures),
        "successRate": (len(run_rows) - len(failures)) / len(run_rows) if run_rows else 0.0,
        "minRuntimeMs": min(runtimes) if runtimes else None,
        "avgRuntimeMs": statistics.fmean(runtimes) if runtimes else None,
        "p95RuntimeMs": suite_p95,
        "maxRuntimeMs": max(runtimes) if runtimes else None,
        "maxRuntimeThresholdMs": int(args.max_runtime_ms),
        "p95RuntimeThresholdMs": int(args.p95_runtime_ms),
        "maxFailures": int(args.max_failures),
        "slowRunCount": len(slow_runs),
    }
    threshold_failures: List[str] = []
    if len(failures) > int(args.max_failures):
        threshold_failures.append(f"failed_runs_{len(failures)}_exceeds_{int(args.max_failures)}")
    if slow_runs:
        threshold_failures.append(f"slow_runs_{len(slow_runs)}_exceed_{int(args.max_runtime_ms)}ms")
    if suite_p95 is not None and suite_p95 > int(args.p95_runtime_ms):
        threshold_failures.append(f"p95_{int(round(suite_p95))}ms_exceeds_{int(args.p95_runtime_ms)}ms")
    suite["thresholdStatus"] = "pass" if not threshold_failures else "fail"
    suite["thresholdFailures"] = threshold_failures

    report = {
        "suite": suite,
        "caseSummaries": case_summaries,
        "runs": run_rows,
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / f"{args.report_prefix}.json"
    csv_path = output_dir / f"{args.report_prefix}.csv"
    json_path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    _write_csv(csv_path, run_rows)

    print(f"Wrote JSON report: {json_path}")
    print(f"Wrote CSV report: {csv_path}")
    print(
        "SUMMARY "
        f"runs={suite['totalRuns']} pass={suite['passedRuns']} fail={suite['failedRuns']} "
        f"avg_ms={suite['avgRuntimeMs']:.0f} p95_ms={suite['p95RuntimeMs']:.0f} "
        f"max_ms={suite['maxRuntimeMs']:.0f} threshold={suite['thresholdStatus']}"
    )
    if threshold_failures:
        print("THRESHOLD FAILURES")
        for item in threshold_failures:
            print(f"- {item}")
    return 1 if args.fail_on_regression and threshold_failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
