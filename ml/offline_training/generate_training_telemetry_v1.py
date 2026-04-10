#!/usr/bin/env python
"""Generate diverse Stage1/Stage2 telemetry rows for ML training (deterministic)."""

from __future__ import annotations

import argparse
import json
import random
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, List

ROOT = Path(__file__).resolve().parents[2]
BACKEND_DIR = ROOT / "backend"
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

import database  # type: ignore
from domain.models import GeneratePlanRequest, UserProfile  # type: ignore
from ml_events import uid_hash  # type: ignore
from services.meal_planner import solve_meal_plan  # type: ignore


@dataclass(frozen=True)
class Scenario:
    name: str
    profile: UserProfile


DEFAULT_SCENARIOS: List[Scenario] = [
    Scenario("Base", UserProfile(displayName="Base", goal="General Health")),
    Scenario("Vegetarian", UserProfile(displayName="Vegetarian", dietaryRestrictions=["Vegetarian"])),
    Scenario("Pescatarian", UserProfile(displayName="Pescatarian", dietaryRestrictions=["Pescatarian"])),
    Scenario("NoPork", UserProfile(displayName="NoPork", dietaryRestrictions=["No Pork"])),
    Scenario("NoBeef", UserProfile(displayName="NoBeef", dietaryRestrictions=["No Beef"])),
    Scenario(
        "VegLactose",
        UserProfile(displayName="VegLactose", dietaryRestrictions=["Vegetarian", "Lactose Intolerant"]),
    ),
    Scenario(
        "BudgetNoPork",
        UserProfile(displayName="BudgetNoPork", dietaryRestrictions=["No Pork"], weeklyBudgetPhp=1000),
    ),
    Scenario(
        "PantryConstrained",
        UserProfile(
            displayName="PantryConstrained",
            dietaryRestrictions=["No Beef"],
            pantryItems=["egg", "garlic", "onion", "tomato", "chicken", "rice"],
        ),
    ),
    Scenario("AllergyPeanut", UserProfile(displayName="AllergyPeanut", allergies=["peanut"])),
    Scenario("AllergyDairy", UserProfile(displayName="AllergyDairy", allergies=["dairy"])),
]


def _parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Generate ML training telemetry rows for Stage1 candidates")
    p.add_argument("--sizes", default="300,700,1100", help="Comma-separated candidate subset sizes")
    p.add_argument("--trials-per-scenario", type=int, default=2)
    p.add_argument("--days", type=int, default=7)
    p.add_argument("--meals-per-day", type=int, default=3)
    p.add_argument("--seed", type=int, default=2026)
    p.add_argument("--reset-existing", action="store_true", help="Delete existing stage1 feature rows first")
    p.add_argument(
        "--request-prefix",
        default="mlseed_v1_",
        help="Request id prefix for generated rows (also useful with clear by prefix)",
    )
    p.add_argument(
        "--summary-path",
        default="ml/offline_training/artifacts/dataset_v1/telemetry_generation_summary.json",
        help="Where to write generation summary JSON",
    )
    return p


def _parse_sizes(value: str, max_size: int) -> List[int]:
    out: List[int] = []
    for part in (value or "").split(","):
        text = part.strip()
        if not text:
            continue
        try:
            n = int(text)
        except Exception:
            continue
        if n <= 0:
            continue
        out.append(min(n, max_size))
    unique = sorted(set(out))
    return unique or [min(300, max_size)]


def _clone_profile(profile: UserProfile) -> UserProfile:
    if hasattr(profile, "model_copy"):
        return profile.model_copy(deep=True)  # pydantic v2
    return profile.copy(deep=True)  # pragma: no cover


def _mutate_profile(base: UserProfile, rng: random.Random) -> UserProfile:
    p = _clone_profile(base)
    p.age = max(18, min(45, int(getattr(p, "age", 25) or 25) + rng.choice([-1, 0, 1, 2])))
    p.weightKg = max(45, min(120, int(getattr(p, "weightKg", 65) or 65) + rng.choice([-2, -1, 0, 1, 2])))
    p.heightCm = max(145, min(185, int(getattr(p, "heightCm", 160) or 160) + rng.choice([-1, 0, 1])))
    p.maxCookingTimeMinutes = max(
        20, min(90, int(getattr(p, "maxCookingTimeMinutes", 45) or 45) + rng.choice([-10, 0, 10]))
    )

    existing_budget = getattr(p, "weeklyBudgetPhp", None)
    if existing_budget is None:
        p.weeklyBudgetPhp = int(rng.choice([900, 1100, 1300, 1500, 1800]))
    else:
        p.weeklyBudgetPhp = max(700, min(3000, int(existing_budget) + rng.randint(-150, 200)))

    p.varietyPreference = rng.choice(["Balanced", "High", "Low"])
    p.planningPriority = rng.choice(["Balanced", "Cost", "Nutrition", "Pantry"])
    if not p.pantryItems:
        p.pantryItems = rng.sample(
            ["egg", "garlic", "onion", "tomato", "rice", "chicken", "tofu", "malunggay"],
            k=rng.randint(3, 6),
        )
    return p


def _extract_selected_recipe_ids(plan: Iterable[Dict[str, Any]] | None) -> List[str]:
    selected: List[str] = []
    for day in list(plan or []):
        meals = day.get("meals") if isinstance(day, dict) else []
        for meal in list(meals or []):
            rid = str(meal.get("recipeId") or "")
            if rid:
                selected.append(rid)
    return sorted(set(selected))


def main() -> int:
    args = _parser().parse_args()
    rng = random.Random(int(args.seed))

    database.init_db()
    database.seed_recipes()
    recipes = list(database.get_all_recipes() or [])
    if not recipes:
        raise RuntimeError("No recipes available from database.")

    sizes = _parse_sizes(args.sizes, max_size=len(recipes))
    if args.reset_existing:
        database.clear_stage1_candidate_features()

    started_ms = int(time.time() * 1000)
    runs_total = 0
    runs_success = 0
    rows_written = 0
    no_safe_runs = 0
    scenario_counts: Dict[str, int] = {}

    for size in sizes:
        pool = list(recipes)
        rng.shuffle(pool)
        subset = pool[:size]
        for scenario in DEFAULT_SCENARIOS:
            for trial in range(max(1, int(args.trials_per_scenario))):
                profile = _mutate_profile(scenario.profile, rng)
                request = GeneratePlanRequest(profile=profile, days=int(args.days), mealsPerDay=int(args.meals_per_day))
                telemetry: Dict[str, Any] = {}
                runs_total += 1
                plan, _msg, _explanation = solve_meal_plan(request, subset, telemetry_out=telemetry)

                candidates = telemetry.get("stage1_candidates") or []
                if not candidates:
                    continue

                selected_ids = telemetry.get("selected_recipe_ids") or _extract_selected_recipe_ids(plan)
                if not selected_ids:
                    no_safe_runs += 1
                else:
                    runs_success += 1

                request_id = f"{args.request_prefix}{size}_{scenario.name}_{trial}_{uuid.uuid4().hex[:10]}"
                uid = f"mlseed-{scenario.name}-{trial}"
                inserted = database.record_stage1_candidate_features(
                    request_id=request_id,
                    uid_hash=uid_hash(uid),
                    candidates=candidates,
                    selected_recipe_ids=selected_ids,
                    ranking_strategy=str(telemetry.get("ranking_strategy") or "stage1_heuristic_with_ml_shadow"),
                    model_version=str(telemetry.get("ml_model_version") or "shadow_v0"),
                    generated_at_ms=int(time.time() * 1000),
                )
                rows_written += int(inserted)
                scenario_counts[scenario.name] = scenario_counts.get(scenario.name, 0) + 1

    completed_ms = int(time.time() * 1000)
    stats = database.get_stage1_candidate_stats()
    summary = {
        "seed": int(args.seed),
        "sizes": sizes,
        "trialsPerScenario": int(args.trials_per_scenario),
        "days": int(args.days),
        "mealsPerDay": int(args.meals_per_day),
        "resetExisting": bool(args.reset_existing),
        "requestPrefix": str(args.request_prefix),
        "runsTotal": runs_total,
        "runsWithSelectedRecipes": runs_success,
        "runsWithoutSelectedRecipes": no_safe_runs,
        "rowsWrittenThisRun": rows_written,
        "scenarioRunCounts": scenario_counts,
        "finalStage1Stats": stats,
        "startedAtMs": started_ms,
        "completedAtMs": completed_ms,
    }

    out_path = Path(args.summary_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
