# PCOSina

PCOSina is an offline-first meal planning system for Filipino individuals with PCOS.
It is a wellness decision-support application, not a diagnosis engine, medical device, or treatment provider.

## Overview

PCOSina focuses on practical daily planning: profile-aware meal suggestions, pantry-aware filtering, and explainable planning outputs.

The repository combines:
- **Android client** (primary product surface): Kotlin + Jetpack Compose in `app/`
- **Backend services** (planning authority and APIs): FastAPI in `backend/`
- **Deterministic planning core**: rule-based filtering plus OR-Tools optimization
- **Optional ML support**: assistive ranking/personalization only, never hard-constraint authority

## Key capabilities

- Offline-first Android experience for core planning and tracking flows
- Deterministic planner pipeline with explainable constraints
- Preference-aware meal planning with budget/pantry/nutrition guardrails
- Optional sync and backend-assisted workflows
- ML-assisted ranking with deterministic fallback

## Android and mobile stack

- Kotlin Android app with Jetpack Compose UI
- Android architecture layers under:
  - UI/navigation: `app/src/main/java/com/pcosina/app/ui`
  - Data and repositories: `app/src/main/java/com/pcosina/app/data`
  - Domain use cases: `app/src/main/java/com/pcosina/app/domain`
- Android testing includes unit and connected instrumentation flows

Broader mobile-related support systems (backend APIs, optimization, and ML artifacts) are included in this repository but are distinct from the Android client implementation.

## High-level architecture

- **Android app (`app/`)**: local-first UX and client-side data flows
- **Backend (`backend/`)**: FastAPI entrypoint and planner/control-plane services
- **ML/training (`ml/`)**: offline artifacts and model-readiness support
- **Operational docs and evidence (`docs/`, `benchmarks/`, `.github/workflows/`)**

See [ARCHITECTURE.md](./ARCHITECTURE.md) for deeper details.

## Safety and privacy boundaries

- Wellness decision-support framing is explicit and preserved
- Deterministic hard constraints remain authoritative
- Optional ML cannot override hard safety constraints
- Privacy/security guidance is documented in [PRIVACY_AND_SECURITY.md](./PRIVACY_AND_SECURITY.md)

## Setup

### Android

```powershell
. .\scripts\android-env.ps1
.\gradlew :app:assembleDebug
```

### Backend

```powershell
cd backend
python -m pip install --upgrade pip
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

For respondent deployment/testing and environment notes, see:
- [docs/respondent-deployment.md](./docs/respondent-deployment.md)
- [docs/render-testing-env.md](./docs/render-testing-env.md)

## Validation and testing

Representative commands:

```powershell
. .\scripts\android-env.ps1
.\gradlew :app:testDebugUnitTest
.\scripts\run_connected_android_tests.ps1
python -m pytest backend/tests/test_runtime_readiness.py backend/tests/test_admin_feedback_security.py -q
python -m pytest backend/tests/test_ml_ranker.py backend/tests/test_meal_planner.py -q
```

Reference test scope and release checks:
- [TEST_PLAN.md](./TEST_PLAN.md)
- [RELEASE_CHECKLIST.md](./RELEASE_CHECKLIST.md)
- [docs/pre-release-checklist.md](./docs/pre-release-checklist.md)

## Current status

- This is an active thesis repository under continuous iteration.
- Progress tracking:
  - [docs/roadmap/progress_ledger.md](./docs/roadmap/progress_ledger.md)
  - [docs/roadmap/ml_progress_ledger.md](./docs/roadmap/ml_progress_ledger.md)

## Portfolio metadata guidance (GitHub About)

To keep this repository as the primary Android showcase:

- **Suggested description**: `Offline-first Android meal planning system for Filipino PCOS wellness decision support (thesis project).`
- **Suggested topics**: `android`, `kotlin`, `jetpack-compose`, `offline-first`, `meal-planner`, `pcos`, `fastapi`, `operations-research`
- **Suggested role in pinned repos**: Primary/flagship Android thesis project

See [docs/roadmap/portfolio_profile_preparation.md](./docs/roadmap/portfolio_profile_preparation.md) for profile presentation and cross-repository positioning notes.

## Known limitations

- Current repository state includes active in-progress work; not all gates are always green at every commit.
- Some validation flows (especially connected Android instrumentation) depend on emulator/runtime conditions.
- ML is assistive and rollout-gated; deterministic planning remains the required fallback path.

## Key documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [ML_GUARDRAILS.md](./ML_GUARDRAILS.md)
- [PRIVACY_AND_SECURITY.md](./PRIVACY_AND_SECURITY.md)
- [RISK_REGISTER.md](./RISK_REGISTER.md)
- [TEST_PLAN.md](./TEST_PLAN.md)
- [RELEASE_CHECKLIST.md](./RELEASE_CHECKLIST.md)
