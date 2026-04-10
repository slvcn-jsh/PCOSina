# PCOSINA ML Guardrails

## Purpose

ML in PCOSINA is assistive only.
It must improve ranking or personalization without becoming the final authority on meal-plan feasibility.

## Non-Negotiable Rule

Deterministic filtering and deterministic optimization remain authoritative.
ML may not override:
- allergies
- forbidden ingredients
- pantry feasibility
- calorie and macro constraints
- budget ceilings
- repetition and variety limits
- hard user exclusions
- offline-first guarantees

## Current ML Surface

Primary files:
- `backend/services/ml_ranker.py`
- `backend/services/meal_planner.py`
- `ml/offline_training/train_lightgbm_v1.py`
- `scripts/check_ml_readiness.py`

Current role:
- score or rank stage-1 candidates
- support shadow and canary evaluation
- preserve deterministic fallback whenever the model is missing, stale, or unloadable

## Required Behavior for Any ML Path

- deterministic fallback exists
- input/output contract is explicit
- offline-safe failure mode exists
- evaluation metrics are recorded
- cohort regressions are inspectable
- privacy-sensitive logging is avoided

## Current Guardrails in Repo

- file-backed ranker has safe `None` fallback
- planner continues with deterministic heuristic scoring if model load or scoring fails
- training artifacts include evaluation metrics and cohort metrics
- request-level regression evidence is exported:
  - `request_confusion.csv`
  - `cohort_regression_request_confusion.csv`
- CI enforces readiness checks for:
  - dataset diversity
  - dependency availability
  - canary cohort uplift
  - dataset/model consistency
  - dataset and reason-feedback freshness thresholds

## Allowed ML Use Cases

- candidate ranking
- personalization ordering
- adherence-risk scoring for non-authoritative UX hints
- replacement ordering
- cold-start assistive heuristics

## Disallowed ML Use Cases

- final meal-plan authority
- bypassing hard nutrition constraints
- bypassing allergies or exclusions
- bypassing pantry feasibility
- forcing cloud-only behavior
- replacing deterministic infeasibility explanations

## Operational Expectations

- keep ML in shadow/canary until live operational evidence is complete
- refresh artifacts before freshness thresholds expire
- inspect request-level regression exports before widening canary traffic
- document any new ML path in the model card and readiness ledgers
