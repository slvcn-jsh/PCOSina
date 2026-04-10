# ML Telemetry Pipeline (Implemented Foundation)

## Event contracts
- Schema: `shared-contracts/event_schemas/ml_event_taxonomy.v1.json`
- Validator/runtime contract: `backend/ml_events.py`
- Ingestion endpoint: `POST /ml/events`

## Backend-emitted planner events
- `plan_generation_requested`
- `stage1_candidates_scored`
- `solver_completed`
- `async_solver_completed`
- `plan_generated`
- `no_safe_plan_encountered`

## Persistence
- `ml_events` table stores normalized event payloads with dedupe keys.
- `ml_stage1_candidate_features` stores candidate feature snapshots and `selected_by_solver` labels.
- Replace/skip reason events are normalized into controlled tags at ingestion (`backend/services/reason_normalizer.py`).

## Offline training flow
1. Build dataset: `ml/offline_training/build_training_dataset_v1.py`
2. Train model: `ml/offline_training/train_lightgbm_v1.py`
3. Save artifacts: model, metrics, feature importance, config snapshot
4. Optional feedback artifacts: `ml/offline_training/build_reason_feedback_dataset_v1.py`
5. Optional synthetic reason seeding (staging): `ml/offline_training/generate_reason_telemetry_v1.py`

## Runtime scoring flow
- Stage 1 uses `backend/services/ml_ranker.py` to load model best-effort.
- If model is unavailable, scoring safely falls back to heuristic shadow score.
- Canary controls whether ML score affects ranking.

## Safety boundary
ML is support only. Stage 2 CP-SAT remains authoritative for final plan feasibility and safety.
