# Recipe Growth Benchmark

Use this to find the minimum recipe count that keeps plan generation feasible and fast while covering restriction/allergy scenarios.
Current project baseline: start evaluation at `300` recipes.

## Run

From `backend/`:

```powershell
python benchmark_dataset_growth.py --start 300 --step 100 --trials 3 --target-feasible-rate 95 --target-min-profile-feasible-rate 90 --target-p95-seconds 12
```

This produces:

- `growth_benchmark_trials.csv`
- `growth_benchmark_summary.csv`
- `growth_benchmark_coverage.csv`
- `growth_benchmark_gaps.csv`
- `growth_benchmark_report.json`

`growth_benchmark_report.json` includes `recommended_min_size` when a size passes all targets.
`growth_benchmark_gaps.csv` is the actionable recipe-gap file:

- `needed_breakfast`, `needed_lunch`, `needed_dinner`: deficits per meal label
- `needed_if_universal`: lower bound if added recipes are universal/flexible
- `needed_if_meal_specific`: lower bound if added recipes are meal-type specific

## Suggested Fast Smoke Run

```powershell
python benchmark_dataset_growth.py --start 300 --step 100 --max-size 500 --trials 1 --solver-time-seconds 2 --solver-max-seconds 4 --total-solver-seconds 8
```

## Custom Restriction/Allergy Scenarios

Pass `--scenarios-path scenarios.json`.
Starter file: `backend/benchmark_scenarios.example.json`

Example `scenarios.json`:

```json
[
  {
    "name": "VegetarianDairyAllergy",
    "displayName": "VegetarianDairyAllergy",
    "dietaryRestrictions": ["Vegetarian"],
    "allergies": ["dairy"],
    "maxCookingTimeMinutes": 45,
    "weeklyBudgetPhp": 1200
  },
  {
    "name": "NoPorkNoBeef",
    "displayName": "NoPorkNoBeef",
    "dietaryRestrictions": ["No Pork", "No Beef"],
    "allergies": []
  }
]
```

## Coverage Gates

The benchmark marks coverage as passing only when both are true:

- Safe candidate pool for a scenario is `>= min_safe_total` (default `10`)
- Safe candidates per meal label are `>= min_safe_per_meal` (default `6`)

Tune with:

```powershell
python benchmark_dataset_growth.py --min-safe-total 12 --min-safe-per-meal 8
```
