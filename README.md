# PCOSINA

PCOSINA is an offline-first meal planning system for Filipino individuals with PCOS.
It is a wellness decision-support tool, not a diagnosis engine or medical device.

The repository contains:
- an Android client built with Kotlin and Jetpack Compose
- a Python FastAPI backend used as the authoritative planner/control plane
- deterministic planning logic with a rule-filter stage followed by OR-Tools constraint optimization
- optional ML used only for assistive ranking and personalization, never for hard-constraint authority

## Current Architecture

- Android app: `app/`
  - UI and navigation under `app/src/main/java/com/pcosina/app/ui`
  - local persistence and repositories under `app/src/main/java/com/pcosina/app/data`
  - lightweight domain use cases under `app/src/main/java/com/pcosina/app/domain`
- Backend: `backend/`
  - FastAPI entrypoint in `backend/main.py`
  - planner, ML, and sync services in `backend/services`
  - database and schema handling in `backend/database.py`
- ML and offline training: `ml/`
- rollout, SRE, and release evidence: `docs/`, `benchmarks/`, `.github/workflows/`

More detail: [ARCHITECTURE.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\ARCHITECTURE.md)

## Local-First Contract

- The Android app must remain usable offline for core local flows.
- Optional sync is supplementary and must not be required for core profile, pantry, plan viewing, grocery guidance, or reflection storage.
- The backend planner remains deterministic and explainable.
- ML can only assist ranking or personalization and must always degrade safely.

## Prerequisites

- Android Studio with JDK 17-compatible runtime
- Android SDK / emulator tooling for mobile builds and instrumentation tests
- Python 3.12+ for backend, scripts, and ML tooling
- Optional Firebase config for release packaging and distribution
- Optional Postgres and Redis for production-like backend runs

## Local Development

### Backend

```powershell
cd backend
python -m pip install --upgrade pip
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Notes:
- Dev and test can use local SQLite.
- Production is expected to fail closed without Postgres and required security controls.

### Android

Debug builds expect a backend base URL ending with `/`.
By default, debug now targets the hosted HTTPS backend so emulator installs work without a local server.
If you want the emulator to hit a backend running on your laptop instead, explicitly override the debug base URL.

```powershell
. .\scripts\android-env.ps1
.\gradlew :app:assembleDebug
```

Local-backend override example:

```powershell
. .\scripts\android-env.ps1
$env:DEBUG_BASE_URL="http://10.0.2.2:8000/"
.\gradlew :app:assembleDebug
```

Release packaging needs:
- readable `google-services.json`
- managed release signing credentials, or an explicit local-only insecure fallback override

Helpful repo-local wrappers:

```powershell
.\scripts\run_connected_android_tests.ps1 -AttemptAdbFix
.\scripts\release.ps1 -AllowInsecureLocalSigning -SkipFirebaseDistribution
```

## Validation

Representative backend and ML validation commands:

```powershell
python -m pytest backend/tests/test_runtime_readiness.py backend/tests/test_admin_feedback_security.py backend/tests/test_admin_operator_auth_policy.py backend/tests/test_async_job_ownership.py backend/tests/test_ops_operator_access_api.py -q
python -m pytest backend/tests/test_ml_ranker.py backend/tests/test_meal_planner.py backend/tests/test_lightgbm_training.py backend/tests/test_ml_dataset_builder.py backend/tests/test_reason_feedback_dataset_builder.py backend/tests/test_ml_readiness_script.py -q
python scripts/check_ml_readiness.py --mode shadow --reason-feedback-summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json --max-dataset-artifact-age-hours 720 --max-dataset-source-age-hours 720 --max-reason-feedback-artifact-age-hours 720 --max-reason-feedback-source-age-hours 720
python scripts/check_ml_readiness.py --mode canary --reason-feedback-summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json --max-dataset-artifact-age-hours 720 --max-dataset-source-age-hours 720 --max-reason-feedback-artifact-age-hours 720 --max-reason-feedback-source-age-hours 720
```

Representative Android validation commands:

```powershell
. .\scripts\android-env.ps1
.\gradlew :app:testDebugUnitTest
.\scripts\run_connected_android_tests.ps1
```

In this sandboxed session, Android dependency resolution is currently blocked by restricted outbound network access.

## Key Documents

- [ARCHITECTURE.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\ARCHITECTURE.md)
- [TEST_PLAN.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\TEST_PLAN.md)
- [ML_GUARDRAILS.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\ML_GUARDRAILS.md)
- [PRIVACY_AND_SECURITY.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\PRIVACY_AND_SECURITY.md)
- [RISK_REGISTER.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\RISK_REGISTER.md)
- [RELEASE_CHECKLIST.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\RELEASE_CHECKLIST.md)
- [docs/roadmap/progress_ledger.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\docs\roadmap\progress_ledger.md)
- [docs/roadmap/ml_progress_ledger.md](C:\Users\salva\AndroidStudioProjects\PCOSINA2\docs\roadmap\ml_progress_ledger.md)
