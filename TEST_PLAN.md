# PCOSINA Test Plan

## Goal

Validate that PCOSINA remains safe, deterministic, local-first, and production-credible for its intended wellness scope.

## Critical Validation Areas

### Build and environment
- Android unit and instrumentation builds
- backend dependency installation and pytest execution
- ML readiness and artifact freshness gates
- runtime readiness checks for production-only controls

### Core planner correctness
- ingredient normalization
- allergy and restriction filtering
- pantry-to-recipe matching
- shortlist sizing and deterministic fallback behavior
- solver input validation
- solver output validation
- infeasibility reporting and actionable guidance

### Nutrition and grocery correctness
- calorie, macro, and budget aggregation
- grocery deficit and duplicate-ingredient aggregation
- missing nutrient fallback behavior
- variety and repetition enforcement

### Offline-first behavior
- local startup without network
- cached plan continuity
- empty pantry and sparse recipe pool handling
- sync failure tolerance without corrupting local state

### ML guardrails
- stale or missing model fallback
- file-backed model load behavior
- evaluation artifact consistency
- cohort regression evidence export
- freshness gating for dataset and reason-feedback artifacts

### Security and privacy
- operator authentication and session controls
- async job ownership enforcement
- App Check enforcement
- manifest security posture
- production runtime fail-closed checks

## Repo-Available Commands

### Backend runtime/security slices

```powershell
python -m pytest backend/tests/test_runtime_readiness.py backend/tests/test_admin_feedback_security.py backend/tests/test_admin_operator_auth_policy.py backend/tests/test_async_job_ownership.py backend/tests/test_ops_operator_access_api.py -q
```

### Planner and ML slices

```powershell
python -m pytest backend/tests/test_ml_ranker.py backend/tests/test_meal_planner.py backend/tests/test_lightgbm_training.py backend/tests/test_ml_dataset_builder.py backend/tests/test_reason_feedback_dataset_builder.py backend/tests/test_ml_readiness_script.py -q
```

### ML readiness gates

```powershell
python scripts/check_ml_readiness.py --mode shadow --reason-feedback-summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json --max-dataset-artifact-age-hours 720 --max-dataset-source-age-hours 720 --max-reason-feedback-artifact-age-hours 720 --max-reason-feedback-source-age-hours 720
python scripts/check_ml_readiness.py --mode canary --reason-feedback-summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json --max-dataset-artifact-age-hours 720 --max-dataset-source-age-hours 720 --max-reason-feedback-artifact-age-hours 720 --max-reason-feedback-source-age-hours 720
```

### Android policy/unit checks

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_USER_HOME="$PWD\\.gradle-user-home"
$env:ANDROID_USER_HOME="$PWD\\.android-user"
./gradlew :app:testDebugUnitTest
```

### Android connected/instrumentation checks

```powershell
./gradlew :app:connectedDebugAndroidTest
```

## Known Validation Limits

- Android dependency resolution requires outbound Maven access; this sandbox cannot provide it.
- Live webhook routing, production Redis throughput, and live canary drills require external secrets and reachable environments.
- Cross-device sync validation requires staged devices or emulators plus a network-enabled environment.

## Exit Criteria

- Critical backend runtime/security tests pass
- ML readiness passes in shadow and canary modes
- Android unit and connected checks pass in the intended environment
- Remaining failures, if any, are documented as explicit blockers with owner and scope
