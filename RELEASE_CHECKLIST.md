# PCOSINA Release Checklist

## Purpose

This is the root-level release entrypoint.
Use it together with:
- `docs/release/go_live_checklist.md`
- `docs/release/production_readiness_checklist.md`
- `docs/roadmap/progress_ledger.md`

## Repo-Side Checks Before Release

- backend runtime/security tests pass
- ML readiness passes in shadow and canary modes
- Android unit tests pass in the intended environment
- release signing credentials are configured
- release Firebase config is present and readable
- top-level docs and risk register are current

## Required Commands

```powershell
python -m pytest backend/tests/test_runtime_readiness.py backend/tests/test_admin_feedback_security.py backend/tests/test_admin_operator_auth_policy.py backend/tests/test_async_job_ownership.py backend/tests/test_ops_operator_access_api.py -q
python -m pytest backend/tests/test_ml_ranker.py backend/tests/test_meal_planner.py backend/tests/test_lightgbm_training.py backend/tests/test_ml_dataset_builder.py backend/tests/test_reason_feedback_dataset_builder.py backend/tests/test_ml_readiness_script.py -q
python scripts/check_ml_readiness.py --mode shadow --reason-feedback-summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json --max-dataset-artifact-age-hours 720 --max-dataset-source-age-hours 720 --max-reason-feedback-artifact-age-hours 720 --max-reason-feedback-source-age-hours 720
python scripts/check_ml_readiness.py --mode canary --reason-feedback-summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json --max-dataset-artifact-age-hours 720 --max-dataset-source-age-hours 720 --max-reason-feedback-artifact-age-hours 720 --max-reason-feedback-source-age-hours 720
```

Android checks in a network-enabled environment:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_USER_HOME="$PWD\\.gradle-user-home"
$env:ANDROID_USER_HOME="$PWD\\.android-user"
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

## External Evidence Still Required

- live canary drill and webhook delivery evidence
- production rollout batch evidence
- connected Android/emulator pass evidence
- staged multi-device sync replay evidence
- production Redis/load evidence

## Release Decision Rule

Do not call the system production-ready until the external evidence above is archived and the open risks in `RISK_REGISTER.md` are reduced to minor, non-blocking items.
