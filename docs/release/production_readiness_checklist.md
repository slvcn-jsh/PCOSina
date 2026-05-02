# PCOSINA Production Readiness Checklist

Date: 2026-03-21

> Status note (2026-03-21): this checklist is synchronized to the current repository state. It separates repo-implemented controls from external-environment evidence that still blocks an honest production-ready claim.

## Repo-Implemented Controls

- [x] Remove admin tokens from public/admin HTML and reject legacy shared admin-token access.
- [x] Replace token-only privileged access with named operator auth, role checks, and per-operator audit attribution.
- [x] Escape admin feedback content and keep CSRF/session-based admin flows.
- [x] Add `owner_uid` to plan jobs and enforce owner-scoped `/plan-jobs/{job_id}` reads.
- [x] Fail closed in production without Postgres, Firebase credentials, App Check, and required admin-session controls.
- [x] Enforce production-safe rate limiting by rejecting `RATE_LIMIT_BACKEND=memory` in production readiness checks.
- [x] Declare a real production deployment shape in `render.yaml` with managed Postgres, Redis, web, and worker services.
- [x] Harden Android release posture with `allowBackup="false"`, `usesCleartextTraffic="false"`, release network security config, and explicit backup/data-extraction exclusion rules.
- [x] Guard release packaging on readable Firebase config and explicit release-signing credentials or a local-only insecure override.
- [x] Move sensitive reflection and plan/grocery artifacts toward encrypted/local-only storage.
- [x] Add tracked schema migration tooling, readiness surfacing, and migration gate scripts.
- [x] Enforce Firebase App Check on Android release requests and backend mobile-facing endpoints.
- [x] Run backend pytest in CI, publish backend JUnit/coverage artifacts, run ML readiness gates with artifact freshness checks, and archive Android build/test artifacts from CI jobs.
- [x] Preserve owner-scoped planner telemetry across sync, in-process async, and queued worker execution, including terminal success, no-safe-plan, and error events for diagnostics and ML evidence.
- [x] Validate Android `:app:testDebugUnitTest` and local `:app:assembleRelease` on 2026-03-21 using Android Studio JBR; release compile proof used the guarded local insecure-signing override only and does not replace real secret-backed signing evidence.
- [x] Standardize Android helper scripts around repo-local tool homes via `scripts/android-env.ps1`, `scripts/check_adb_access.ps1`, and `scripts/release.ps1`, and remove conflicting AGP preference-root environment variables.
- [x] Provide browser-based operator tooling for `/admin/content`, `/admin/ops`, and `/admin/policy`, with shared cross-console navigation across feedback/content/ops/policy surfaces for multi-role admins.
- [x] Align the user-facing methodology/trust screens with the actual local-first planning contract, and keep those help surfaces reachable before the first generated plan.

## Remaining Blockers Before Public Production

- [ ] Run a real secret-backed release-signing build and archive the pass evidence.
- [ ] Run connected Android unit/instrumentation verification in an unrestricted environment and archive the first green pass evidence. A local emulator booted on 2026-03-21, and the repo helper scripts now route AGP into repo-local writable homes, but this sandbox still forces `adb` to `C:\Users\CodexSandboxOffline\.android` and blocks Maven fetches needed for fresh Android test reruns.
- [ ] Provision live webhook endpoints and archive the first successful canary drill plus alert-routing receipts.
- [ ] Run staged multi-device sync and reinstall recovery validation with real environment evidence.
- [ ] Archive production Redis/load evidence from the rollout-readiness batch.
- [ ] Verify mobile crash-report ingestion and backend alert routing against live destinations.

## Remaining Maturity Work Before External Pilot

- [ ] Standardize Firebase credential provisioning across staging and production environments.
- [x] Build or wire the intended operator back-office UX for content, operations, and policy administration via the browser-based `/admin/content`, `/admin/ops`, and `/admin/policy` consoles.
- [ ] Decide and document the release TLS hardening posture end to end.
- [ ] Keep ML in shadow/canary until live dashboard ingestion and rollback alerting are proven.

## Post-Launch Maturity Work

- [ ] Run formal production-like load tests on the declared deployment shape.
- [ ] Run chaos and recovery drills and archive evidence.
- [ ] Automate secrets-rotation and credential-expiry checks.
- [ ] Review telemetry and ML payloads for ongoing PII minimization.
- [ ] Run abuse-path testing on admin, planner, queue, and sync surfaces.

## Evidence Anchors

- `backend/tests/test_admin_feedback_security.py`
- `backend/tests/test_admin_content_console_ui.py`
- `backend/tests/test_admin_ops_console_ui.py`
- `backend/tests/test_admin_policy_console_ui.py`
- `backend/tests/test_android_methodology_copy_policy.py`
- `backend/tests/test_android_navigation_help_access_policy.py`
- `backend/tests/test_admin_operator_auth_policy.py`
- `backend/tests/test_async_job_ownership.py`
- `backend/tests/test_worker_plan_jobs.py`
- `backend/tests/test_no_safe_plan_contract.py`
- `backend/tests/test_runtime_readiness.py`
- `backend/tests/test_rate_limit_backend.py`
- `backend/tests/test_app_check_enforcement.py`
- `backend/tests/test_schema_migrations.py`
- `backend/tests/test_android_tooling_scripts.py`
- `app/src/test/java/com/pcosina/app/ManifestSecurityPolicyTest.kt`
- `app/src/test/java/com/pcosina/app/ReleaseSigningPolicyTest.kt`
- `app/src/test/java/com/pcosina/app/AndroidEnvPolicyTest.kt`
- `app/src/test/java/com/pcosina/app/AppCheckPolicyTest.kt`
- `scripts/android-env.ps1`
- `scripts/check_adb_access.ps1`
- `scripts/release.ps1`
- `backend/main.py`
- `app/src/main/java/com/pcosina/app/ui/navigation/Routes.kt`
- `app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt`
- `app/src/main/java/com/pcosina/app/ui/screens/DashboardRefinedScreen.kt`
- `app/src/main/java/com/pcosina/app/ui/screens/MoreToolsScreen.kt`
- `app/src/main/java/com/pcosina/app/ui/screens/IpoVisualizationScreen.kt`
- `.github/workflows/ci.yml`
- `docs/roadmap/progress_ledger.md`
