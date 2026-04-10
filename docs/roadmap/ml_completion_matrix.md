# PCOSINA ML Completion Matrix

Updated: 2026-03-10

| Component | Exists | Implemented Correctly | Tested | Reproducible | Production-Ready | Notes |
|---|---|---|---|---|---|---|
| Event taxonomy schema (`shared-contracts/event_schemas/ml_event_taxonomy.v1.json`) | Yes | Yes | Yes | Yes | Partial | Covers required behavior events plus planner events. |
| Backend event validation (`backend/ml_events.py`) | Yes | Yes | Yes | Yes | Partial | Enforces common + event-specific required fields. |
| Event ingestion API (`POST /ml/events`) | Yes | Yes | Yes | Yes | Partial | Android now emits all required behavior events: `plan_generated`, `plan_viewed`, `meal_accepted`, `meal_replaced`, `meal_skipped`, `recipe_opened`, `grocery_completed`, `pantry_item_added`, `pantry_item_removed`, `pantry_item_expired`, `cook_completed`, `no_safe_plan_encountered`, `manual_override_attempted`, `why_replaced_submitted`, `why_skipped_submitted`. |
| Reason normalization (`backend/services/reason_normalizer.py`) | Yes | Yes | Yes | Yes | Partial | Optional free text is normalized into controlled tags and hashed metadata; raw free text is not persisted. |
| Event persistence table (`ml_events`) | Yes | Yes | Yes | Yes | Partial | DB-backed, dedupe key supported. |
| Stage1 candidate feature persistence (`ml_stage1_candidate_features`) | Yes | Yes | Yes | Yes | Partial | Populated from authoritative generation path and seeded via multi-scenario telemetry generator. |
| Dataset builder (`ml/offline_training/build_training_dataset_v1.py`) | Yes | Yes | Yes | Yes | Partial | Leakage-safe split by request hash; latest build uses 13,630 rows with request-level split (no fallback). |
| Reason-feedback dataset builder (`ml/offline_training/build_reason_feedback_dataset_v1.py`) | Yes | Yes | Yes | Yes | Partial | Produces normalized reason-event and per-user feature artifacts for retraining inputs. |
| Reason telemetry seeder (`ml/offline_training/generate_reason_telemetry_v1.py`) | Yes | Yes | Yes | Yes | Partial | Deterministic staging/offline generator for replace/skip reason events. |
| Label definition (`selected_by_solver`) | Yes | Yes | Yes | Yes | Partial | Derived from Stage2 authoritative selected recipes. |
| LightGBM trainer (`ml/offline_training/train_lightgbm_v1.py`) | Yes | Yes | Yes | Yes | Partial | End-to-end training run completed on diversified telemetry (60 requests / 20 users). |
| Artifact contract (model + metrics + config + feature importance) | Yes | Yes | Yes | Yes | Partial | Generated under `ml/offline_training/artifacts/model_v1/`. |
| Baseline comparison (heuristic vs model ranking metrics) | Yes | Yes | Yes | Yes | Partial | Computed from training metrics; latest test ranking exceeds heuristic baseline (`NDCG@10`, `MAP@10`). |
| Shadow path in Stage1 scoring | Yes | Yes | Partial | Yes | Partial | Shadow scores computed; canary controls ranking effect. |
| Canary gating + rollback toggle | Yes | Yes | Yes | Yes | Partial | Config keys, guard script, and breach simulation drill tooling now exist (`scripts/monitor_canary_guardrails.py`, `scripts/simulate_canary_breach_metrics.py`); still needs live dashboard/alert integration. |
| Model loading fallback behavior | Yes | Yes | Partial | Yes | Partial | Missing model or deps safely falls back to heuristic shadow score. |
| Model card | Yes | Yes | N/A | Yes | Partial | Updated with latest artifact metrics and dataset profile. |
| Retraining runbook | Yes | Yes | N/A | Yes | Partial | Operational flow validated after dependency install. |
| Readiness gate script (`scripts/check_ml_readiness.py`) | Yes | Yes | Yes | Yes | Partial | Enforces dataset minimum rows/positive-rate and dependency gates; canary requires model metrics artifact. |

## Current quality risk
- Dataset diversity and quality improved (`unique_requests=60`, `unique_users=20`, `positive_rate=5.63%`), but rollout should remain shadow/canary until live canary monitoring dashboards and alert routing are fully operational.
