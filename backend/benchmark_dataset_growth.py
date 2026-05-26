import argparse
import csv
import json
import os
import random
import time
from dataclasses import dataclass
from pathlib import Path
from statistics import median
from typing import Any, Dict, Iterable, List, Optional, Tuple

from domain.models import GeneratePlanRequest, UserProfile
from services.meal_planner import MEAL_LABELS, shortlist_candidates, solve_meal_plan


@dataclass(frozen=True)
class Scenario:
    name: str
    profile: UserProfile


DEFAULT_SCENARIOS: List[Scenario] = [
    Scenario("Base", UserProfile(displayName="Base")),
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
            pantryItems=["chicken", "egg", "garlic", "tomato"],
        ),
    ),
    Scenario("AllergyPeanut", UserProfile(displayName="AllergyPeanut", allergies=["peanut"])),
    Scenario("AllergyDairy", UserProfile(displayName="AllergyDairy", allergies=["dairy"])),
]


def _float_or_none(value: Any) -> Optional[float]:
    if value is None:
        return None
    text = str(value).strip()
    if text == "" or text.lower() in ("none", "null"):
        return None
    return float(text)


def _median(values: List[int]) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    mid = len(ordered) // 2
    if len(ordered) % 2 == 1:
        return int(ordered[mid])
    return int((ordered[mid - 1] + ordered[mid]) / 2)


def _compute_nutrition_medians(raw_recipes: List[Dict[str, Any]]) -> Dict[str, int]:
    calories: List[int] = []
    protein: List[int] = []
    carbs: List[int] = []
    fats: List[int] = []
    fiber: List[int] = []
    for recipe in raw_recipes:
        nutrition = recipe.get("nutrition", {}) or {}
        for key, bucket in (
            ("calories", calories),
            ("protein_g", protein),
            ("carbs_g", carbs),
            ("fat_g", fats),
            ("fiber_g", fiber),
        ):
            raw = nutrition.get(key)
            if raw in (None, "", 0):
                continue
            try:
                bucket.append(int(raw))
            except Exception:
                continue
    return {
        "calories": _median(calories) or 500,
        "protein_g": _median(protein) or 25,
        "carbs_g": _median(carbs) or 45,
        "fat_g": _median(fats) or 15,
        "fiber_g": _median(fiber) or 6,
    }


def _pick_nutrition(nutrition: Dict[str, Any], key: str, default: int) -> int:
    raw = nutrition.get(key)
    if raw in (None, "", 0):
        return default
    try:
        return max(0, int(raw))
    except Exception:
        return default


def _to_solver_recipe(raw: Dict[str, Any], medians: Dict[str, int]) -> Dict[str, Any]:
    nutrition = raw.get("nutrition", {}) or {}
    return {
        "id": raw.get("id"),
        "title": raw.get("name") or raw.get("title") or "Unnamed",
        "mealType": raw.get("mealType", "Universal"),
        "calories": _pick_nutrition(nutrition, "calories", medians["calories"]),
        "proteinGrams": _pick_nutrition(nutrition, "protein_g", medians["protein_g"]),
        "carbsGrams": _pick_nutrition(nutrition, "carbs_g", medians["carbs_g"]),
        "fatsGrams": _pick_nutrition(nutrition, "fat_g", medians["fat_g"]),
        "fiberGrams": _pick_nutrition(nutrition, "fiber_g", medians["fiber_g"]),
        "tags": raw.get("tags", []) or [],
        "minutes": int(raw.get("minutes") or 25),
        "ingredients": raw.get("ingredients", []) or [],
        "steps": raw.get("instructions", []) or raw.get("steps", []) or [],
    }


def _load_recipes(path: Path) -> List[Dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError(f"Expected recipe list in {path}")
    medians = _compute_nutrition_medians(data)
    return [_to_solver_recipe(raw, medians) for raw in data]


def _shuffle_recipes(recipes: List[Dict[str, Any]], seed: int) -> List[Dict[str, Any]]:
    ordered = list(recipes)
    rng = random.Random(seed)
    rng.shuffle(ordered)
    return ordered


def _iter_sizes(start: int, step: int, max_size: int) -> Iterable[int]:
    current = max(1, start)
    while current <= max_size:
        yield current
        current += step
    if max_size not in set(range(max(1, start), max_size + 1, step)):
        yield max_size


def _compute_p95(values: List[float]) -> Optional[float]:
    if not values:
        return None
    ordered = sorted(values)
    idx = max(0, int(0.95 * len(ordered)) - 1)
    return ordered[idx]


def _profile_from_dict(raw: Dict[str, Any]) -> UserProfile:
    profile = UserProfile(
        displayName=str(raw.get("displayName") or raw.get("name") or "Scenario"),
        age=int(raw.get("age", 25) or 25),
        heightCm=int(raw.get("heightCm", 160) or 160),
        weightKg=int(raw.get("weightKg", 65) or 65),
        activityLevel=str(raw.get("activityLevel", "Lightly Active")),
        goal=str(raw.get("goal", "General Health")),
        dietaryRestrictions=list(raw.get("dietaryRestrictions", []) or []),
        allergies=list(raw.get("allergies", []) or []),
        weeklyBudgetPhp=(int(raw["weeklyBudgetPhp"]) if raw.get("weeklyBudgetPhp") is not None else None),
        budgetWeekly=_float_or_none(raw.get("budgetWeekly")),
        budgetMonthly=_float_or_none(raw.get("budgetMonthly")),
        maxCookingTimeMinutes=int(raw.get("maxCookingTimeMinutes", 45) or 45),
        varietyPreference=str(raw.get("varietyPreference", "Balanced")),
        planningPriority=str(raw.get("planningPriority", "Balanced")),
        pantryItems=list(raw.get("pantryItems", []) or []),
    )
    return profile


def _load_scenarios(path: Optional[Path]) -> List[Scenario]:
    if path is None:
        return DEFAULT_SCENARIOS
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("Scenario file must be a JSON array.")
    scenarios: List[Scenario] = []
    for i, raw in enumerate(data):
        if not isinstance(raw, dict):
            raise ValueError(f"Scenario at index {i} must be an object.")
        profile = _profile_from_dict(raw)
        name = str(raw.get("name") or profile.displayName or f"Scenario{i+1}")
        scenarios.append(Scenario(name=name, profile=profile))
    if not scenarios:
        raise ValueError("Scenario file is empty.")
    return scenarios


def _coverage_for_profile(profile: UserProfile, recipes: List[Dict[str, Any]]) -> Tuple[int, Dict[str, int]]:
    buckets = shortlist_candidates(profile, recipes)
    candidate_by_id: Dict[str, Dict[str, Any]] = {}
    for bucket_name in ("Breakfast", "Lunch", "Dinner", "Universal"):
        for recipe in buckets.get(bucket_name, []):
            recipe_id = str(recipe.get("id", ""))
            if recipe_id:
                candidate_by_id[recipe_id] = recipe

    per_meal = {label: 0 for label in MEAL_LABELS}
    for recipe in candidate_by_id.values():
        allowed = set(recipe.get("_allowed_meals") or MEAL_LABELS)
        for label in MEAL_LABELS:
            if label in allowed:
                per_meal[label] += 1
    return len(candidate_by_id), per_meal


def _write_csv(path: Path, rows: List[Dict[str, Any]], fieldnames: List[str]) -> None:
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def _apply_solver_overrides(args: argparse.Namespace) -> None:
    if args.allow_fallback:
        os.environ["PCOSINA_ALLOW_FALLBACK"] = "true"
    if args.solver_time_seconds is not None:
        os.environ["PCOSINA_SOLVER_TIME_SECONDS"] = str(args.solver_time_seconds)
    if args.solver_max_seconds is not None:
        os.environ["PCOSINA_SOLVER_MAX_SECONDS"] = str(args.solver_max_seconds)
    if args.total_solver_seconds is not None:
        os.environ["PCOSINA_TOTAL_SOLVER_SECONDS"] = str(args.total_solver_seconds)
    if args.solver_workers is not None:
        os.environ["PCOSINA_SOLVER_WORKERS"] = str(args.solver_workers)


def run(args: argparse.Namespace) -> int:
    _apply_solver_overrides(args)
    recipes_path = Path(args.recipes_path)
    scenarios_path = Path(args.scenarios_path) if args.scenarios_path else None
    output_prefix = args.output_prefix.strip() or "growth_benchmark"

    recipes = _load_recipes(recipes_path)
    scenarios = _load_scenarios(scenarios_path)
    shuffled = _shuffle_recipes(recipes, seed=args.seed)
    max_size = min(args.max_size or len(shuffled), len(shuffled))

    trial_rows: List[Dict[str, Any]] = []
    coverage_rows: List[Dict[str, Any]] = []
    gap_rows: List[Dict[str, Any]] = []
    summary_rows: List[Dict[str, Any]] = []
    target_row: Optional[Dict[str, Any]] = None

    sizes = list(_iter_sizes(args.start, args.step, max_size))
    total_runs = len(sizes) * len(scenarios) * args.trials
    done_runs = 0

    print(
        f"Running growth benchmark: sizes={sizes[0]}..{sizes[-1]} step={args.step}, "
        f"scenarios={len(scenarios)}, trials={args.trials}, totalRuns={total_runs}"
    )

    for size in sizes:
        subset = shuffled[:size]
        size_times: List[float] = []
        size_feasible = 0
        profile_stats: Dict[str, Dict[str, Any]] = {}

        for scenario in scenarios:
            scenario_times: List[float] = []
            scenario_feasible = 0
            safe_total, per_meal = _coverage_for_profile(scenario.profile, subset)
            needed_total = max(0, args.min_safe_total - safe_total)
            needed_breakfast = max(0, args.min_safe_per_meal - per_meal["Breakfast"])
            needed_lunch = max(0, args.min_safe_per_meal - per_meal["Lunch"])
            needed_dinner = max(0, args.min_safe_per_meal - per_meal["Dinner"])
            meal_gaps = [
                label
                for label, count in per_meal.items()
                if count < args.min_safe_per_meal
            ]
            coverage_ok = safe_total >= args.min_safe_total and not meal_gaps
            needed_if_universal = max(
                needed_total,
                needed_breakfast,
                needed_lunch,
                needed_dinner,
            )
            needed_if_meal_specific = max(
                needed_total,
                needed_breakfast + needed_lunch + needed_dinner,
            )
            coverage_rows.append(
                {
                    "size": size,
                    "scenario": scenario.name,
                    "safe_total_candidates": safe_total,
                    "safe_breakfast": per_meal["Breakfast"],
                    "safe_lunch": per_meal["Lunch"],
                    "safe_dinner": per_meal["Dinner"],
                    "coverage_ok": coverage_ok,
                    "missing_meals": ";".join(meal_gaps),
                    "needed_total_candidates": needed_total,
                    "needed_breakfast": needed_breakfast,
                    "needed_lunch": needed_lunch,
                    "needed_dinner": needed_dinner,
                    "needed_if_universal": needed_if_universal,
                    "needed_if_meal_specific": needed_if_meal_specific,
                    "restrictions": "|".join(scenario.profile.dietaryRestrictions or []),
                    "allergies": "|".join(scenario.profile.allergies or []),
                }
            )
            if not coverage_ok:
                gap_rows.append(
                    {
                        "size": size,
                        "scenario": scenario.name,
                        "missing_meals": ";".join(meal_gaps),
                        "needed_total_candidates": needed_total,
                        "needed_breakfast": needed_breakfast,
                        "needed_lunch": needed_lunch,
                        "needed_dinner": needed_dinner,
                        "needed_if_universal": needed_if_universal,
                        "needed_if_meal_specific": needed_if_meal_specific,
                        "restrictions": "|".join(scenario.profile.dietaryRestrictions or []),
                        "allergies": "|".join(scenario.profile.allergies or []),
                    }
                )

            for trial in range(args.trials):
                req = GeneratePlanRequest(profile=scenario.profile, days=args.days, mealsPerDay=3)
                started = time.perf_counter()
                result, msg, explanation = solve_meal_plan(req, subset)
                elapsed = time.perf_counter() - started
                feasible = bool(result)
                done_runs += 1

                if feasible:
                    scenario_feasible += 1
                    size_feasible += 1
                scenario_times.append(elapsed)
                size_times.append(elapsed)
                trial_rows.append(
                    {
                        "size": size,
                        "scenario": scenario.name,
                        "trial": trial + 1,
                        "feasible": feasible,
                        "seconds": round(elapsed, 4),
                        "message": msg,
                        "fallback_used": bool((explanation or {}).get("fallbackUsed", False)),
                        "restriction_count": len(scenario.profile.dietaryRestrictions or []),
                        "allergy_count": len(scenario.profile.allergies or []),
                    }
                )

            profile_stats[scenario.name] = {
                "feasible_rate": (100.0 * scenario_feasible / max(1, args.trials)),
                "median_seconds": median(scenario_times) if scenario_times else 0.0,
                "p95_seconds": _compute_p95(scenario_times) or 0.0,
                "coverage_ok": coverage_ok,
            }

        runs_for_size = len(scenarios) * args.trials
        feasible_rate = 100.0 * size_feasible / max(1, runs_for_size)
        median_seconds = median(size_times) if size_times else 0.0
        p95_seconds = _compute_p95(size_times) or 0.0
        max_seconds = max(size_times) if size_times else 0.0

        min_profile_rate = min(v["feasible_rate"] for v in profile_stats.values()) if profile_stats else 0.0
        all_coverage_ok = all(v["coverage_ok"] for v in profile_stats.values()) if profile_stats else False

        row = {
            "size": size,
            "runs": runs_for_size,
            "feasible_rate": round(feasible_rate, 2),
            "min_profile_feasible_rate": round(min_profile_rate, 2),
            "median_seconds": round(median_seconds, 4),
            "p95_seconds": round(p95_seconds, 4),
            "max_seconds": round(max_seconds, 4),
            "all_coverage_ok": all_coverage_ok,
            "target_pass": (
                feasible_rate >= args.target_feasible_rate
                and min_profile_rate >= args.target_min_profile_feasible_rate
                and p95_seconds <= args.target_p95_seconds
                and all_coverage_ok
            ),
            "progress_runs_done": done_runs,
            "progress_total_runs": total_runs,
        }
        summary_rows.append(row)

        if target_row is None and row["target_pass"]:
            target_row = row

        print(
            f"size={size} feasible={feasible_rate:.1f}% "
            f"minProfile={min_profile_rate:.1f}% median={median_seconds:.2f}s "
            f"p95={p95_seconds:.2f}s coverage={all_coverage_ok}"
        )

    trials_csv = Path(f"{output_prefix}_trials.csv")
    summary_csv = Path(f"{output_prefix}_summary.csv")
    coverage_csv = Path(f"{output_prefix}_coverage.csv")
    gaps_csv = Path(f"{output_prefix}_gaps.csv")
    report_json = Path(f"{output_prefix}_report.json")

    _write_csv(
        trials_csv,
        trial_rows,
        [
            "size",
            "scenario",
            "trial",
            "feasible",
            "seconds",
            "message",
            "fallback_used",
            "restriction_count",
            "allergy_count",
        ],
    )
    _write_csv(
        summary_csv,
        summary_rows,
        [
            "size",
            "runs",
            "feasible_rate",
            "min_profile_feasible_rate",
            "median_seconds",
            "p95_seconds",
            "max_seconds",
            "all_coverage_ok",
            "target_pass",
            "progress_runs_done",
            "progress_total_runs",
        ],
    )
    _write_csv(
        coverage_csv,
        coverage_rows,
        [
            "size",
            "scenario",
            "safe_total_candidates",
            "safe_breakfast",
            "safe_lunch",
            "safe_dinner",
            "coverage_ok",
            "missing_meals",
            "needed_total_candidates",
            "needed_breakfast",
            "needed_lunch",
            "needed_dinner",
            "needed_if_universal",
            "needed_if_meal_specific",
            "restrictions",
            "allergies",
        ],
    )
    _write_csv(
        gaps_csv,
        gap_rows,
        [
            "size",
            "scenario",
            "missing_meals",
            "needed_total_candidates",
            "needed_breakfast",
            "needed_lunch",
            "needed_dinner",
            "needed_if_universal",
            "needed_if_meal_specific",
            "restrictions",
            "allergies",
        ],
    )

    report = {
        "recipes_path": str(recipes_path),
        "total_recipes": len(recipes),
        "seed": args.seed,
        "sizes_tested": sizes,
        "days": args.days,
        "trials": args.trials,
        "targets": {
            "feasible_rate": args.target_feasible_rate,
            "min_profile_feasible_rate": args.target_min_profile_feasible_rate,
            "p95_seconds": args.target_p95_seconds,
            "min_safe_total": args.min_safe_total,
            "min_safe_per_meal": args.min_safe_per_meal,
        },
        "recommended_min_size": (target_row or {}).get("size"),
        "recommended_metrics": target_row or {},
        "outputs": {
            "trials_csv": str(trials_csv),
            "summary_csv": str(summary_csv),
            "coverage_csv": str(coverage_csv),
            "gaps_csv": str(gaps_csv),
        },
        "open_coverage_gaps": len(gap_rows),
        "top_gaps": gap_rows[:30],
    }
    report_json.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(f"Saved {trials_csv}")
    print(f"Saved {summary_csv}")
    print(f"Saved {coverage_csv}")
    print(f"Saved {gaps_csv}")
    print(f"Saved {report_json}")
    if target_row:
        print(
            f"Recommended minimum recipe count: {target_row['size']} "
            f"(feasible={target_row['feasible_rate']}%, p95={target_row['p95_seconds']}s)"
        )
        return 0
    print("No tested size met all targets. Increase max size, relax constraints, or adjust targets.")
    return 2


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Benchmark solver feasibility/performance while increasing recipe pool size in fixed steps. "
            "Also audits safe candidate coverage for restriction/allergy scenarios."
        )
    )
    parser.add_argument("--recipes-path", default="recipes.json", help="Path to recipes JSON.")
    parser.add_argument("--scenarios-path", default=None, help="Optional JSON file for custom scenarios.")
    parser.add_argument("--output-prefix", default="growth_benchmark", help="Output file prefix.")
    parser.add_argument(
        "--start",
        type=int,
        default=300,
        help="Start pool size (project baseline: 300).",
    )
    parser.add_argument("--step", type=int, default=100, help="Increment per run.")
    parser.add_argument("--max-size", type=int, default=None, help="Max pool size (default: all recipes).")
    parser.add_argument("--days", type=int, default=7, help="Plan days for each run.")
    parser.add_argument("--trials", type=int, default=3, help="Trials per scenario per size.")
    parser.add_argument("--seed", type=int, default=2026, help="Shuffle seed for incremental subsets.")
    parser.add_argument(
        "--target-feasible-rate",
        type=float,
        default=95.0,
        help="Target overall feasibility rate for a size to pass.",
    )
    parser.add_argument(
        "--target-min-profile-feasible-rate",
        type=float,
        default=90.0,
        help="Target minimum per-scenario feasibility rate for a size to pass.",
    )
    parser.add_argument(
        "--target-p95-seconds",
        type=float,
        default=12.0,
        help="Target p95 solve time threshold in seconds.",
    )
    parser.add_argument(
        "--min-safe-total",
        type=int,
        default=10,
        help="Minimum safe candidate count required by coverage gate.",
    )
    parser.add_argument(
        "--min-safe-per-meal",
        type=int,
        default=6,
        help="Minimum safe candidate count per meal label (Breakfast/Lunch/Dinner).",
    )
    parser.add_argument("--allow-fallback", action="store_true", help="Allow heuristic fallback in solver.")
    parser.add_argument("--solver-time-seconds", type=float, default=None, help="Override solver base time limit.")
    parser.add_argument("--solver-max-seconds", type=float, default=None, help="Override solver max time limit.")
    parser.add_argument("--total-solver-seconds", type=float, default=None, help="Override total solver time limit.")
    parser.add_argument("--solver-workers", type=int, default=None, help="Override solver worker count.")
    return parser


if __name__ == "__main__":
    parser = build_parser()
    exit_code = run(parser.parse_args())
    raise SystemExit(exit_code)
