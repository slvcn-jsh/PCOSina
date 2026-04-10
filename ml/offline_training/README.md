# Offline Training (Shadow/Canary Preparation)

## 1) Generate diverse stage1 telemetry (recommended before training)

```powershell
python ml/offline_training/generate_training_telemetry_v1.py `
  --reset-existing `
  --sizes 300,700,1100 `
  --trials-per-scenario 2 `
  --seed 2026
```

This writes a generation summary and refreshes `ml_stage1_candidate_features` with diversified request/user coverage.

## 2) Build dataset from stage1 telemetry

```powershell
python ml/offline_training/build_training_dataset_v1.py `
  --db-path pcosina.db `
  --output-dir ml/offline_training/artifacts/dataset_v1
```

Outputs:
- `dataset_full.csv`
- `train.csv`
- `val.csv`
- `test.csv`
- `dataset_manifest.json`
- `dataset_quality_report.json`

Dataset manifest notes:
- `generatedAtMs` records when the dataset artifact was built.
- `sourceTelemetryWindow` records the min/max `generated_at_ms` from `ml_stage1_candidate_features`.
- `reasonFeedbackWindow` records the min/max reason-event times folded into the dataset features.

Note:
- If `ml_events` contains `why_replaced_submitted` / `why_skipped_submitted`,
  the dataset builder auto-injects `replace_reason_tag_hist_*` and `skip_reason_tag_hist_*`
  features per user.

## 2.5) Optional: generate and audit reason-feedback telemetry

Generate deterministic reason events (for staging/offline validation):

```powershell
python ml/offline_training/generate_reason_telemetry_v1.py `
  --db-path pcosina.db `
  --events 240 `
  --seed 2026
```

Build reason-feedback artifacts:

```powershell
python ml/offline_training/build_reason_feedback_dataset_v1.py `
  --db-path pcosina.db `
  --output-dir ml/offline_training/artifacts/reason_feedback_v1
```

Outputs:
- `reason_feedback_events.csv`
- `reason_feedback_user_features.csv`
- `reason_feedback_summary.json`

## 3) Train LightGBM V1

```powershell
python ml/offline_training/train_lightgbm_v1.py `
  --dataset-dir ml/offline_training/artifacts/dataset_v1 `
  --output-dir ml/offline_training/artifacts/model_v1 `
  --seed 2026
```

Requirements:
- `lightgbm`
- `scikit-learn`
- `pandas`

Install:
```powershell
python -m pip install -r ml/offline_training/requirements.txt
```

Outputs:
- `lightgbm_v1_model.txt`
- `training_metrics.json`
- `training_config_snapshot.json`
- `feature_importance.csv`
- `cohort_metrics.csv`
- `request_confusion.csv`
- `cohort_regression_request_confusion.csv`

## 4) Run readiness gates

```powershell
python scripts/check_ml_readiness.py --mode shadow
python scripts/check_ml_readiness.py --mode canary
```

Safety note: this model is Stage-1 ranking support only. Final authority remains Stage 2 CP-SAT.
Canary readiness also checks test-split uplift on the automated `cold_start`, `sparse_pantry`, and `budget_constrained` cohorts.
If validation and test disagree on a cohort, inspect `request_confusion.csv` and `cohort_regression_request_confusion.csv` before widening canary traffic.
Main CI also enforces freshness gates on dataset and reason-feedback artifacts using `check_ml_readiness.py` with a `720`-hour maximum age for both artifact timestamps and source telemetry windows.
