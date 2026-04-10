# LightGBM V1 Evaluation Report

Status: Updated from real training artifacts (`ml/offline_training/artifacts/model_v1/training_metrics.json`)

## 1. Dataset summary
- Dataset version: `ml-dataset-v1-1774061562`
- Generated at (ms): `1774061562465`
- Total rows: `13,630`
- Unique requests: `60`
- Unique users: `20`
- Positive label rate: `5.6273%`
- Source telemetry max `generated_at_ms`: `1773132481151`
- Source reason-feedback max `event_time_ms`: `1773135555643`

## 2. Split summary
- Train rows: `10,998`
- Validation rows: `1,463`
- Test rows: `1,169`
- Leakage check result: request-level deterministic split (`hash(request_id) mod 10`), fallback not used

## 3. Binary metrics
- Validation AUC: `0.9586`
- Validation LogLoss: `0.1292`
- Test AUC: `0.9492`
- Test LogLoss: `0.1604`

## 4. Ranking metrics
- Validation NDCG@5 / @10: `0.9791 / 0.9004`
- Validation MAP@5 / @10: `0.3762 / 0.6524`
- Test NDCG@5 / @10: `1.0000 / 0.9530`
- Test MAP@5 / @10: `0.4075 / 0.7538`

## 5. Baseline comparison
- Baseline ranking feature: `heuristic_score`
- Validation uplift vs baseline:
  - NDCG@10: `+0.0175` (`0.9004 - 0.8829`)
  - MAP@10: `+0.0244` (`0.6524 - 0.6281`)
- Test uplift vs baseline:
  - NDCG@10: `+0.0514` (`0.9530 - 0.9016`)
  - MAP@10: `+0.0670` (`0.7538 - 0.6868`)

## 6. Cohort analysis
- Cohort slicing is now automated in `training_metrics.json` and `cohort_metrics.csv`.
- Request-level regression evidence is now exported in `request_confusion.csv` and `cohort_regression_request_confusion.csv`.
- Cold-start cohort:
  - Definition: `reason_events_total == 0`
  - Validation slice: `4` requests / `607` rows, uplift `+0.0021` NDCG@10 and `+0.0003` MAP@10
  - Test slice: `7` requests / `1,169` rows, uplift `+0.0514` NDCG@10 and `+0.0670` MAP@10
- Sparse pantry cohort:
  - Definition: request-level `pantry_overlap_count` mean `<= 1.0`
  - Validation slice: `4` requests / `1,048` rows, uplift `+0.0039` NDCG@10 and `+0.0042` MAP@10
  - Test slice: `4` requests / `822` rows, uplift `+0.0574` NDCG@10 and `+0.0733` MAP@10
- Budget-constrained cohort:
  - Definition: `budget_weekly_norm <= 0.1571` (bottom third of request-level budget in the snapshot)
  - Validation slice: `2` requests / `431` rows, uplift `-0.0494` NDCG@10 and `-0.0762` MAP@10
  - Test slice: `4` requests / `511` rows, uplift `+0.0492` NDCG@10 and `+0.0648` MAP@10

## 7. Error analysis
- Strongest residual risk remains class imbalance (positives are still minority).
- The budget-constrained validation slice is still small (`2` requests) and currently unstable despite positive test uplift.
- `request_confusion.csv` now exports per-request top-10 TP/FP/FN, first-positive rank, and request-level NDCG@10 / MAP@10 uplift for every validation and test request.
- `cohort_regression_request_confusion.csv` now isolates only requests that belong to regressed cohorts. In the current artifact, the budget-constrained validation regression is concentrated in `2` requests, and the worst request drops from `9` to `8` top-10 true positives vs baseline (`-0.0987` NDCG@10, `-0.1523` MAP@10).
- Feature missingness is handled by zero-fill at train/inference time.

## 8. Safety and rollout recommendation
- Hard-rule authority retained by Stage 2: **Yes**
- Shadow readiness: **Yes**
- Canary readiness: **Yes** (small cohort only, continue monitoring budget-constrained uplift)
- CI freshness gate: **Yes** (`720`-hour max age on dataset and reason-feedback artifacts plus source windows)
- Final recommendation: **`canary ready`** with strict rollback guardrails, automated cohort checks, and CP-SAT final authority unchanged.
