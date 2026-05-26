# Benchmarks

## Realistic Planner Profile Runner

Run from repository root:

```powershell
python scripts/benchmark_planner_profiles.py --runs 1
```

For a stronger local evidence batch:

```powershell
python scripts/benchmark_planner_profiles.py --runs 5 --fail-on-regression
```

To prove the local benchmark is using the LightGBM Stage 1 ranker instead of
the deterministic shadow fallback, set the model artifact paths before running:

```powershell
$env:PCOSINA_ML_MODEL_PATH="$PWD\ml\offline_training\artifacts\model_v1\lightgbm_v1_model.txt"
$env:PCOSINA_ML_METRICS_PATH="$PWD\ml\offline_training\artifacts\model_v1\training_metrics.json"
python scripts\benchmark_planner_profiles.py --runs 5 --require-ml-ready --fail-on-regression --report-prefix planner_realistic_profiles.local.lightgbm
```

The runner validates every generated plan against the selected hard rules:
slot count, recipe existence, meal-type compatibility, cooking-time cap,
allergies/restrictions, adjacent duplicate prevention, repeat limit, weekly
budget cap, daily calorie bounds, daily macro bounds, and daily fiber minimum.
Sodium and sugar are reported as advisory warnings because the current solver
penalizes overshoot instead of rejecting a plan.

Current local production-shaped evidence for the command above:
100/100 runs passed, average 257 ms, P95 313 ms, max 357 ms,
zero hard-constraint violations, `mlRankerReady=true`, and
`mlModelVersion=lightgbm_stage1_ranker_v1`.

To benchmark the deployed backend, provide the same mobile auth material the
Android app uses. Do not commit these tokens.

```powershell
$env:PCOSINA_BENCHMARK_AUTH_TOKEN="<firebase-id-token>"
$env:PCOSINA_BENCHMARK_APP_CHECK_TOKEN="<firebase-app-check-token>"
python scripts\benchmark_planner_profiles.py --live-base-url https://pcosina-backend.onrender.com --runs 1 --fail-on-regression --report-prefix planner_realistic_profiles.render
```

Outputs:
- `benchmarks/reports/planner_realistic_profiles.local.json`
- `benchmarks/reports/planner_realistic_profiles.local.csv`
- `benchmarks/reports/planner_realistic_profiles.local.lightgbm.json`
- `benchmarks/reports/planner_realistic_profiles.local.lightgbm.csv`

The frozen profile fixture is:
- `benchmarks/canonical_scenarios/planner_realistic_profiles_20.json`

## Comment #5 Runner (Phase-1 Skeleton)

Run from repository root:

```powershell
python benchmarks/runner/run_comment5_benchmark.py --use-existing-artifacts
```

Outputs:
- `benchmarks/reports/comment5_benchmark_manifest.json`
- copied Comment #5 artifacts (optional)

This skeleton locks deterministic seed/scenarios and artifact contract. Full run orchestration (raw trial generation for all compared algorithms) is scheduled for phase-2.
