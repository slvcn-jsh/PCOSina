# PCOSINA Architecture

## System Intent

PCOSINA is designed as a local-first meal planning system for Filipino users with PCOS.
Core user flows must remain available in low-connectivity conditions.
Cloud capabilities are supplementary and must never become the sole source of truth for core app state.

## Real Repository Shape

### Android client

Primary module: `app/`

Key areas:
- `app/src/main/java/com/pcosina/app/ui`
  Compose screens, navigation, UX state, and user-facing feedback
- `app/src/main/java/com/pcosina/app/data/repository`
  API access, local preference/artifact storage, optional cloud sync bridges
- `app/src/main/java/com/pcosina/app/domain`
  lightweight domain use cases and calculation helpers
- `app/src/main/java/com/pcosina/app/notifications`
  reminder and sync-notification plumbing

Local persistence today is primarily preference and artifact oriented rather than Room-based:
- `UserPreferencesRepository.kt` uses DataStore-backed preferences and optional cloud reconciliation
- `AuthRepository.kt` uses DataStore for session persistence
- `ReflectionStore.kt` uses encrypted shared preferences for on-device reflection content

Android does not currently use Room or SQLite as its mobile database. For thesis and defense wording, describe the mobile side as an offline-first local persistence layer: Jetpack DataStore, encrypted shared preferences, and local artifacts.

### Backend authority

Primary module: `backend/`

Key areas:
- `backend/main.py`
  FastAPI app, health/readiness endpoints, admin/ops APIs, request validation
- `backend/services/meal_planner.py`
  deterministic filtering plus final constraint optimization authority
- `backend/services/ml_ranker.py`
  optional file-backed LightGBM assistive scorer
- `backend/services/reason_normalizer.py`
  normalized reason-feedback handling
- `backend/services/sync_recovery.py`
  sync recovery and replay helpers
- `backend/database.py`
  schema, migrations, data access, and environment-specific database mode
- `backend/worker_plan_jobs.py`
  async planning worker path

Production backend persistence is PostgreSQL. The Render blueprint provisions a managed Postgres database and injects `DATABASE_URL` into both the web service and worker. SQLite remains only a local development and automated testing fallback; production startup/readiness fails closed without a valid Postgres connection string.

### ML and training

Primary module: `ml/`

Key areas:
- offline training dataset build
- reason-feedback dataset build
- LightGBM training artifacts
- evaluation and readiness evidence

## Planning Contract

### Stage 1: deterministic candidate shaping

The first stage remains deterministic and rule-aware.
It is responsible for:
- allergy and exclusion screening
- dietary restriction filtering
- pantry-aware candidate scoring and pruning
- time, macro, and budget-aware heuristic shaping
- bounded shortlist generation to reduce solver load

### Stage 2: deterministic optimization authority

The final authority remains the deterministic solver path in `backend/services/meal_planner.py`.
Current implementation uses OR-Tools CP-SAT style constraint solving as the authoritative selection layer.

The solver is responsible for:
- slot assignment validity
- calorie and macro constraint enforcement
- budget ceilings
- repetition limits and variety controls
- infeasibility signaling with structured diagnostics

## ML Guardrail Position

ML is assistive only.
It may:
- score candidates
- rank shortlist items
- personalize ordering
- help cold-start heuristics

It may not override:
- allergies
- hard exclusions
- pantry feasibility
- nutrition bounds
- budget bounds
- repetition limits
- explicit hard user preferences
- offline-first guarantees

If ML is unavailable or stale, the system must fall back to deterministic heuristic behavior.

## Offline-First and Sync

Primary source of truth:
- local app storage

Supplementary systems:
- Firebase authentication and optional cloud profile/artifact sync
- backend planning and telemetry services

Operational rule:
- sync failures must degrade gracefully
- offline state must remain readable and actionable
- cached plans and grocery guidance should remain available offline when previously generated

## Deployment Shape

Current production-target blueprint is defined in `render.yaml`:
- managed Postgres
- managed Redis
- FastAPI web service
- dedicated worker service

This supports the async queue architecture without making the mobile client dependent on constant connectivity.

## Supporting References

- `docs/architecture/production_contract.md`
- `docs/architecture/optimizer_queue_worker.md`
- `docs/architecture/ml_telemetry_pipeline.md`
- `docs/backend-config.md`
