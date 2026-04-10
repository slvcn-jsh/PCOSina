# Model Card: LightGBM V1 (Stage 1 Ranking Support)

## Model identity
- Name: `lightgbm_stage1_ranker_v1`
- Family: Gradient Boosted Decision Trees (LightGBM)
- Role: Stage 1 candidate ranking support only
- Authority: Non-authoritative (Stage 2 CP-SAT remains final authority)

## Intended use
- Reorder feasible Stage 1 candidates to improve relevance before CP-SAT solve.
- Improve acceptance probability, replacement reduction, and user preference fit.

## Non-intended use
- Cannot be used as final planner.
- Cannot override hard constraints (allergy, exclusions, meal-slot validity).
- Cannot be used to generate meal plans without CP-SAT verification.

## Training data summary
- Source table: `ml_stage1_candidate_features`
- Label: `selected_by_solver` (1 if selected by authoritative Stage 2 output)
- Split: request-level deterministic hash split (train/val/test)
- Leakage prevention: same `request_id` never appears across splits
- Current training snapshot:
  - Rows: `13,630`
  - Unique requests: `60`
  - Unique users: `20`
  - Positive rate: `5.63%`

## Feature groups
- Profile context: restriction/allergy counts, budget normalization, cook-time bounds
- Recipe profile: calories/protein/carbs/fats/fiber, minutes, estimated cost
- Pantry/context fit: pantry overlap and slot eligibility
- Shadow metadata: heuristic score/model score columns for baseline comparison

## Evaluation metrics
- Binary: AUC, logloss
- Ranking: NDCG@5, NDCG@10, MAP@5, MAP@10
- Baseline comparison: heuristic score ranking vs model score ranking
- Cohort reporting: automated cold-start, sparse-pantry, and budget-constrained slices
- Request-level diagnostics: `request_confusion.csv` and `cohort_regression_request_confusion.csv`
- Latest artifact metrics (`training_metrics.json`):
  - Validation AUC: `0.9586`
  - Test AUC: `0.9492`
  - Test NDCG@10: `0.9530` vs heuristic `0.9016`
  - Test MAP@10: `0.7538` vs heuristic `0.6868`
  - Test cohort uplift:
    - Cold start: `+0.0514` NDCG@10, `+0.0670` MAP@10
    - Sparse pantry: `+0.0574` NDCG@10, `+0.0733` MAP@10
    - Budget constrained: `+0.0492` NDCG@10, `+0.0648` MAP@10

## Risk and limitations
- Cold-start users may have sparse behavioral signals.
- Budget-constrained validation slice is still noisy because the current offline snapshot only has `2` validation requests in that cohort.
- Request-level diagnostics now isolate the budget-constrained validation regression to two exported requests, but the cohort is still too small to treat as stable.
- Dataset quality depends on event instrumentation completeness.
- Model can drift with recipe catalog updates and policy changes.

## Safety and fallback behavior
- If model missing/stale/unloadable, fallback to deterministic heuristic scoring.
- If inference fails, no crash path; Stage 1 still executes safely.
- Stage 2 CP-SAT always enforces hard constraints.

## Rollout constraints
- Shadow mode first: scoring logged, no ranking authority transfer.
- Canary only with explicit config enable and cohort percentage.
- Immediate rollback if safety/quality gates regress.
