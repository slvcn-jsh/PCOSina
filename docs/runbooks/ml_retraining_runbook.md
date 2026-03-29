# ML Retraining Runbook (LightGBM V1)

## Preconditions
1. `ml_stage1_candidate_features` has fresh labeled rows.
2. Runtime dependencies installed: `lightgbm`, `scikit-learn`, `pandas`.
3. Working directory is repo root.

## Step 1: Refresh telemetry coverage (recommended)
```powershell
python ml/offline_training/generate_training_telemetry_v1.py `
  --reset-existing `
  --sizes 300,700,1100 `
  --trials-per-scenario 2 `
  --seed 2026
```

## Step 2: Build dataset
```powershell
python ml/offline_training/build_training_dataset_v1.py `
  --db-path pcosina.db `
  --output-dir ml/offline_training/artifacts/dataset_v1
```

## Step 3: Train model
```powershell
python ml/offline_training/train_lightgbm_v1.py `
  --dataset-dir ml/offline_training/artifacts/dataset_v1 `
  --output-dir ml/offline_training/artifacts/model_v1 `
  --seed 2026
```

## Step 4: Validate artifacts
Required files in output dir:
- `lightgbm_v1_model.txt`
- `training_metrics.json`
- `training_config_snapshot.json`
- `feature_importance.csv`
- `cohort_metrics.csv`
- `request_confusion.csv`
- `cohort_regression_request_confusion.csv`

Run readiness check:
```powershell
python scripts/check_ml_readiness.py `
  --mode shadow `
  --reason-feedback-summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json `
  --max-dataset-artifact-age-hours 720 `
  --max-dataset-source-age-hours 720 `
  --max-reason-feedback-artifact-age-hours 720 `
  --max-reason-feedback-source-age-hours 720
python scripts/check_ml_readiness.py `
  --mode canary `
  --reason-feedback-summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json `
  --max-dataset-artifact-age-hours 720 `
  --max-dataset-source-age-hours 720 `
  --max-reason-feedback-artifact-age-hours 720 `
  --max-reason-feedback-source-age-hours 720
```

Review note:
- `check_ml_readiness.py --mode canary` now validates automated test-cohort uplift for `cold_start`, `sparse_pantry`, and `budget_constrained`.
- Dataset freshness now checks both artifact generation time and the max source telemetry / reason-event timestamps recorded in the artifacts.
- If validation and test disagree on a small cohort, inspect `cohort_metrics.csv`, `request_confusion.csv`, and `cohort_regression_request_confusion.csv` before widening canary traffic.

## Step 5: Shadow deployment
Prepare runtime env exports and rollout summary:
```powershell
python scripts/prepare_ml_rollout_env.py `
  --phase shadow `
  --output benchmarks/reports/ml_rollout_prep.shadow.json
```

Set environment from the printed shell output:
- `PCOSINA_ML_MODEL_PATH`
- `PCOSINA_ML_METRICS_PATH`

Apply policy:
- `stage1.ML_shadow_enabled=true`
- `stage1.ML_canary_enabled=false`

## Step 6: Canary deployment
Prepare canary rollout exports and verify cohort uplifts again:
```powershell
python scripts/prepare_ml_rollout_env.py `
  --phase canary `
  --canary-percent 5 `
  --output benchmarks/reports/ml_rollout_prep.canary.json
```

Enable:
- `stage1.ML_canary_enabled=true`
- `sre.canary_cohort_percent` (start small, e.g., 5)

Production note:
- Current startup policy bootstrapping will seed the active production policy with:
  - `stage1.ML_shadow_enabled=true`
  - `stage1.ML_canary_enabled=true`
  - `sre.canary_cohort_percent=5.0`
- This avoids needing an admin-console bootstrap just to seed the initial 5% canary when those values are absent from the active policy store.

Run canary guard monitor (fails fast on threshold breach):
```powershell
python scripts/monitor_canary_guardrails.py `
  --metrics benchmarks/reports/go_live_metrics.json `
  --output benchmarks/reports/canary_guard_report.json
```

Optional alert routing:
- Set `PCOSINA_ALERT_WEBHOOK_URL` or pass `--alert-webhook-url`.

For automated rollback:
```powershell
python scripts/monitor_canary_guardrails.py `
  --metrics benchmarks/reports/go_live_metrics.json `
  --output benchmarks/reports/canary_guard_report.json `
  --auto-rollback `
  --actor canary_guard
```

Rollback drill (staging):
```powershell
python scripts/simulate_canary_breach_metrics.py `
  --input benchmarks/reports/go_live_metrics.example.json `
  --output benchmarks/reports/go_live_metrics.breach.json `
  --scenario all

python scripts/monitor_canary_guardrails.py `
  --metrics benchmarks/reports/go_live_metrics.breach.json `
  --output benchmarks/reports/canary_guard_report.breach.json `
  --auto-rollback `
  --actor rollback_drill
```

Rollback immediately by setting:
- `stage1.ML_canary_enabled=false`

Live webhook drill evidence before widening canary:
```powershell
python scripts/check_canary_webhook_secrets.py `
  --environment staging `
  --output benchmarks/reports/canary_webhook_secrets_check.staging.local.json
python scripts/run_canary_drill.py `
  --environment staging `
  --metrics benchmarks/reports/go_live_metrics.json `
  --require-webhooks `
  --alert-on-ok `
  --output benchmarks/reports/canary_drill_receipt.staging.live.json
python scripts/check_canary_drill_receipt.py `
  --receipt benchmarks/reports/canary_drill_receipt.staging.live.json `
  --environment staging `
  --require-webhook-delivery `
  --output benchmarks/reports/canary_drill_receipt_check.staging.live.json

python scripts/check_canary_webhook_secrets.py `
  --environment production `
  --output benchmarks/reports/canary_webhook_secrets_check.production.local.json
python scripts/run_canary_drill.py `
  --environment production `
  --metrics benchmarks/reports/go_live_metrics.json `
  --require-webhooks `
  --alert-on-ok `
  --output benchmarks/reports/canary_drill_receipt.production.live.json
python scripts/check_canary_drill_receipt.py `
  --receipt benchmarks/reports/canary_drill_receipt.production.live.json `
  --environment production `
  --require-webhook-delivery `
  --output benchmarks/reports/canary_drill_receipt_check.production.live.json
```

## Operational notes
- Never bypass CP-SAT authority.
- If model load fails, heuristic ranking remains active.
- `scripts/prepare_ml_rollout_env.py` validates the candidate artifact directory and prints the exact runtime env exports for PowerShell or Bash.
