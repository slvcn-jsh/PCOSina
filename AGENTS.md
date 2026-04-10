# AGENTS.md — PCOSina

## Product identity
PCOSina is an offline-first Filipino-PCOS meal planning system.
It is a wellness decision-support application.
It is not a diagnosis engine or medical device.

## Core planning contract
The meal planning engine must remain:
1. deterministic preference-aware filtering
2. deterministic MILP optimization

ML is optional and assistive only.
ML may rank or personalize candidates.
ML must never override hard constraints.

## Hard constraints that ML cannot override
- excluded ingredients
- allergies
- pantry feasibility
- cost/budget ceilings
- nutritional minima/maxima
- repetition/variety constraints
- explicit user preferences marked as hard
- local-first/offline-first behavior

## Architecture priorities
- local database is the source of truth
- sync is optional
- clean separation between UI, domain logic, data access, solver, and ML
- no tight coupling between Compose UI and solver internals
- deterministic fallback for every ML-assisted path

## Quality priorities
- build must stay green
- critical flows must be test-covered
- numerical outputs must be validated
- infeasibility must be handled gracefully
- logging must be privacy-safe
- docs must match reality

## Development style
- prefer small, test-backed changes
- fix correctness before adding features
- fix offline reliability before remote features
- fix solver input quality before changing solver complexity
- document risks and tradeoffs explicitly

## Repo guidance
- Android app code lives under `app/src/main/java/com/pcosina/app`
- Backend authority and tooling live under `backend/`, `scripts/`, and `ml/`
- Current production status is tracked in `docs/roadmap/progress_ledger.md`
- ML rollout status is tracked in `docs/roadmap/ml_progress_ledger.md`
