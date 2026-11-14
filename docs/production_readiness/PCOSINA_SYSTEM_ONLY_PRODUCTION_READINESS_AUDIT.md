# PCOSina System-Only Production Readiness Audit

Audit date: 2026-05-17  
Scope: working software only. Thesis chapters, defense notes, survey forms, academic claims, and non-operational documents were intentionally excluded.

## 1. Executive Summary

Overall production readiness score: 72%

Overall verdict: PCOSina is a serious beta-grade system with substantial production architecture in place, but it is not 100% production-ready and should not be opened to unsupervised public real-user use yet.

Ready for real users: No for broad public use. The current implementation has blocking CI telemetry coverage, planner-contract, validation, and release-state risks.

Ready for pilot/beta use: Yes, for a controlled pilot with known limits, operator monitoring, Firebase/App Check enabled, Redis/PostgreSQL configured, and rollback procedures available.

Top 10 blockers preventing 100%:

1. The working tree is dirty and includes untracked implementation/test/report files, so the audited state is not a clean release candidate.
2. `python scripts/check_mobile_ml_event_coverage.py` fails. Missing Android-side events include `plan_generated`, pantry events, skip/reason events, and grocery completion.
3. `backend/services/meal_planner.py` enforces budget/repetition/meal-slot rules hard, but nutrition minima/maxima use slack/deviation variables in the CP-SAT objective, so they are not true hard constraints.
4. Admin recipe, price-rule, and nutrition-correction request models in `backend/domain/models.py` lack strong bounds/max lengths on many content fields.
5. Connected Android instrumentation/device tests were not run in this audit; only JVM unit tests were verified.
6. Offline continuity is incomplete during generation warmup failure: `MealPlanViewModel.generateMealPlan()` captures a continuity plan but returns an error before showing it if backend warmup fails.
7. `UserPreferencesRepository.savePlanHistoryJson()` writes secure plan history but does not update the Plan artifact sync timestamp like plan/grocery/pantry saves do.
8. Backend dependencies in `backend/requirements.txt` use broad minimums or unpinned packages such as `ortools` and `lightgbm`.
9. PostgreSQL access uses direct `psycopg.connect(DATABASE_URL)` per operation, with no explicit connection pool, backup/export flow, or rollback tooling verified.
10. ML readiness depends on model paths, metrics freshness, mobile telemetry, and policy flags; model fallback is safe, but the end-to-end production feedback loop is not fully green.

## 2. Readiness Scorecard

| Area | Score | Verdict | Evidence | Main Risks | Required Action |
|---|---:|---|---|---|---|
| Android frontend | 73% | Beta-ready, not production-complete | `app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt`, `Routes.kt`, `MealPlanViewModel.kt`; `:app:testDebugUnitTest` passed | Missing mobile ML events, route encoding edge case, no connected test run, warmup failure drops continuity UI | Add missing emitters, encode recipe route IDs, run connected tests, improve offline failure continuity |
| Backend API | 82% | Strong but not fully hardened | `backend/main.py`; `backend/tests` 284 passed | Admin input validation gaps, Sunday-only production generation guard, generic 500s on some catalog failures | Add stricter schemas, clarify generation policy, harden error taxonomy |
| PostgreSQL/database | 76% | Functional, needs production hardening | `backend/database.py`, `backend/db_url.py`, `backend/migrate_sqlite_to_postgres.py` | No pool, weak DB-level constraints, outdated SQLite migration script, no verified backup/rollback | Add pooling, DB constraints, backup/restore runbook/tooling, retire/update legacy migration |
| Recipe catalog/admin management | 78% | DB-backed and tested, but validation-sensitive | `/admin/recipes`, `/admin/recipes/status`, `/admin/recipes/seed`, `seed_recipes()`; admin tests passed | Admin edits can persist invalid numeric/content data; force reseed overwrites seed IDs | Add schema bounds, DB checks, admin preview/validation, reseed safeguards |
| Planner/CP-SAT optimization | 75% | Substantial deterministic planner, not contract-perfect | `shortlist_candidates()`, `solve_meal_plan()`, CP-SAT constraints in `meal_planner.py` | Nutrition and pantry feasibility are partly soft; timeouts/relaxation need more production evidence | Convert required nutrition bounds to hard or explicitly reclassify them; add invariant tests |
| LightGBM/ML ranking | 68% | Assistive and bounded, but telemetry gate fails | `ml_ranker.py`, `ml_features.py`, `test_ml_ranker.py`; mobile coverage gate failed | Missing Android events, model freshness not verified in this audit, shadow scoring affects ranking by default | Fix mobile events, run ML readiness gates, make shadow/canary semantics explicit |
| Security | 70% | Good controls exist, not enough for public launch | Firebase Auth, App Check, TrustedHost, admin MFA/recent auth, CSRF, rate limits | Admin input bounds, production env dependence, no live App Check/Firebase verification, limited abuse controls | Verify prod env, tighten admin inputs, add security regression tests/rate policies |
| Offline-first behavior | 74% | Saved-data offline behavior exists, generation still backend-bound | DataStore, `ReflectionStore`, local grocery/progress repos, recipe snapshot store | New plan generation requires backend; warmup failure loses continuity view; sync timestamp gap | Fix continuity and plan-history sync; add offline UI tests |
| DevOps/deployment | 69% | Render blueprint exists, but release reproducibility needs work | `render.yaml`, `backend/requirements.txt`, `.github/workflows/*` | Unpinned deps, live Redis/Postgres not verified, go-live gate fails | Pin dependencies, run full gates, verify `/health/ready` in deployed env |
| Testing/QA | 72% | Broad tests, one release gate failing | Backend: 284 passed. Android unit: build successful. Mobile gate: failed | Connected tests not run locally; mobile ML coverage gate failing | Fix gate, run connected instrumentation, add constraint/validation tests |
| Overall system | 72% | Controlled beta only | Combined evidence above | Safety/telemetry/release-state gaps | Resolve blockers before public production |

## 3. Critical Blockers

1. Dirty release state: `git status --short --branch` showed modified tracked files in Android and backend code plus untracked `RecipeSnapshotStore.kt`, `RecipeSnapshotCachePolicyTest.kt`, and `docs/production_readiness/`. This is a release governance risk because the audited system is not a clean commit.
2. Mobile ML event coverage gate fails: `scripts/check_mobile_ml_event_coverage.py` reported missing `grocery_completed`, `manual_override_attempted`, `meal_skipped`, `pantry_item_added`, `pantry_item_expired`, `pantry_item_removed`, `plan_generated`, `why_replaced_submitted`, and `why_skipped_submitted`.
3. Nutrition hard-constraint mismatch: `backend/services/meal_planner.py` uses deviation/slack variables for protein, carbs, fats, fiber, sodium, and sugar at lines around the CP-SAT nutrition block, then minimizes those deviations. This can still produce out-of-range nutrition if the solver prefers a penalty rather than declaring infeasibility.
4. Admin catalog validation is too weak for production data integrity: `AdminRecipeUpsertRequest`, `AdminPriceRuleUpsertRequest`, and `AdminNutritionCorrectionUpsertRequest` in `backend/domain/models.py` lack bounds/max lengths for many fields, while planner output depends on those values.
5. Real device/runtime stability is not fully verified in this audit: connected instrumentation exists under `app/src/androidTest`, but no emulator/device run was completed here.

## 4. High-Priority Issues

1. Add Android-side emitters for every required ML event scanned by `scripts/check_mobile_ml_event_coverage.py`.
2. Decide whether nutrition minima/maxima are truly hard. If yes, replace CP-SAT slack-only modeling with hard bounds or post-solve rejection.
3. Add strict Pydantic constraints for admin recipe, price-rule, and nutrition-correction write models.
4. Fix `Routes.recipeDetailsRoute()` to URL-encode `recipeId`, not only `mealLabel`.
5. In `MealPlanViewModel.generateMealPlan()`, preserve/show the continuity plan when backend warmup fails and a saved plan is available.
6. Update `savePlanHistoryJson()` to participate in the Plan artifact timestamp/sync path.
7. Pin backend dependencies and add a reproducible lock/constraints process for Python.
8. Run full connected Android instrumentation and visual regression suites before production release.
9. Verify Render production `/health/ready` with actual secrets, PostgreSQL, Redis, Firebase credentials, App Check, and Sentry configured.

## 5. Medium-Priority Improvements

1. Add a connection pool for PostgreSQL instead of opening a fresh connection in each database helper.
2. Add DB-level `CHECK` constraints for recipe nutrition, minutes, active flags, price ranges, and admin content sizes.
3. Replace or retire `backend/migrate_sqlite_to_postgres.py`; it does not represent the current schema surface.
4. Add tests proving nutrition hard constraints, pantry feasibility behavior, and no-safe-plan behavior under impossible nutrition constraints.
5. Add explicit model freshness checks to the normal deployment gate, not only ML-specific workflows.
6. Reduce `HttpLoggingInterceptor.Level.BODY` risk in debug builds if debug builds are distributed outside trusted testers.
7. Add rate-limit coverage for high-cost planner endpoints beyond global per-IP limits.
8. Add backup/restore and rollback verification for Postgres and recipe/admin content.

## 6. Low-Priority Polish

1. Enable release minification/shrinking once Crashlytics mapping upload and QA are confirmed.
2. Improve operator/admin copy around force reseed so maintainers understand seed IDs can be overwritten.
3. Add stale-cache messaging for recipe catalog snapshots.
4. Add clearer empty states for catalog unavailable/offline recipe detail fallback.

## 7. Frontend Audit

Strong points:

- Navigation is centralized in `app/src/main/java/com/pcosina/app/ui/navigation/Routes.kt` and enforced through `AppNavHost.kt`.
- `AppNavHost.kt` gates profile setup, goal setup, plan-required tabs, and operator routes.
- Saved plan, plan history, grocery data, pantry entries, feedback queue, and progress/reflection artifacts are locally persisted through DataStore plus `ReflectionStore`.
- `ReflectionStore` uses AndroidX Security `MasterKey` and `EncryptedSharedPreferences`.
- `RecipeSnapshotStore.kt` adds a local recipe catalog snapshot fallback for recipe details/summaries.
- Android JVM unit tests passed with `. .\scripts\android-env.ps1; .\gradlew.bat :app:testDebugUnitTest --no-daemon`.

Weak points and risks:

- `Routes.recipeDetailsRoute(recipeId, mealLabel)` encodes `mealLabel` but returns `"$RecipeDetails/$recipeId"` without encoding `recipeId`. Admin-created IDs containing route separators can break navigation.
- `MealPlanViewModel.generateMealPlan()` captures `continuityPlanSnapshot()` but on backend warmup failure sets `MealPlanUiState.Error` and returns before presenting continuity.
- `MealPlanViewModel` emits `plan_viewed`, `no_safe_plan_encountered`, `recipe_opened`, and `meal_replaced`, while `RecipeDetailsScreen.kt` emits `meal_accepted` and `cook_completed`; the required coverage gate still misses nine events.
- `MealPlanRepository.kt` no longer shows the earlier blocking `Thread.sleep` issue, but it still waits up to 15 seconds for Firebase ID tokens inside an OkHttp interceptor with `CountDownLatch.await()`. This is not main-thread UI blocking, but it can tie up request threads.
- `app/build.gradle.kts` sets `isMinifyEnabled = false` for release, so release hardening is incomplete.
- Connected Android tests and actual device UI/runtime stability were not verified in this audit.

Missing or incomplete states:

- New plan generation has an offline/backend-unreachable error state, but it does not consistently preserve the last usable plan in that branch.
- Recipe snapshot fallback exists, but stale snapshot UX was not verified.
- App Check/Firebase failures are mapped to messages in `MealPlanRepository.kt`, but live production token behavior was not verified.

Exact files affected:

- `app/src/main/java/com/pcosina/app/ui/navigation/Routes.kt`
- `app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt`
- `app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt`
- `app/src/main/java/com/pcosina/app/ui/screens/RecipeDetailsScreen.kt`
- `app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt`
- `app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt`
- `app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt`
- `app/src/main/java/com/pcosina/app/data/repository/RecipeSnapshotStore.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

## 8. Backend Audit

Endpoint readiness:

- FastAPI initialization is in `backend/main.py` with lifespan startup, schema initialization, recipe seeding, and policy bootstrapping.
- Authenticated planner endpoints exist: `/generate-plan`, `/generate-plan-async`, and `/plan-jobs/{job_id}`.
- Recipe endpoints exist: `/recipe/{recipe_id}`, `/recipes/summary`, `/recipes/catalog`, and `/recipes/swap-options`.
- Admin content endpoints exist: `/admin/recipes`, `/admin/recipes/status`, `/admin/recipes/seed`, `/admin/recipes/{recipe_id}`, price-rule endpoints, and nutrition-correction endpoints.
- Feedback and support/ops endpoints exist, with admin pages and API tests.
- Health endpoints exist: `/health` and `/health/ready`.

Validation:

- `GeneratePlanRequest` and `UserProfile` in `backend/domain/models.py` have meaningful bounds for age, weight, height, list lengths, budget, days, and meals per day.
- `FeedbackRequest` bounds message length.
- Admin content write models are underconstrained. `AdminRecipeUpsertRequest` and related admin models accept unbounded strings/lists and unconstrained nutrition integers.

Error handling:

- Planner success/no-safe/error paths are structured in `backend/main.py`; no-safe responses are built through `services/plan_response_builder.py`.
- Some catalog endpoints return raw exception text in HTTP 500 details, for example recipe summaries/catalog/swap options.
- `/generate-plan` has a production Sunday-only guard that may be correct product policy, but it is a production behavior risk if the Android app does not make it clear.

Admin safety:

- Admin access supports Firebase bearer principals and signed admin sessions.
- Production readiness checks require App Check, verified operator email, MFA, recent auth, a session secret, Postgres, Redis-backed rate limiting, and non-default UID hash salt.
- Browser mutations use CSRF checks.

Exact files affected:

- `backend/main.py`
- `backend/domain/models.py`
- `backend/services/plan_response_builder.py`
- `backend/tests/test_admin_recipe_api.py`
- `backend/tests/test_admin_content_console_ui.py`
- `backend/tests/test_no_safe_plan_contract.py`

## 9. Database Audit

PostgreSQL readiness:

- `backend/db_url.py` recognizes only Postgres URL schemes for production.
- `backend/database.py` disables SQLite fallback in production when `DATABASE_URL` is not Postgres.
- `init_db()` runs registered schema migrations and uses a Postgres advisory lock around schema bootstrap.
- Render injects `DATABASE_URL` from `pcosina-postgres` in `render.yaml`.

Migration safety:

- `schema_migrations` exists with scoped migration IDs.
- Registered migrations cover recipes, feedback constraints, plan jobs, ML event tables, candidate feature tables, price rules, nutrition corrections, support cases, admin sessions, operator overrides, and market seasonality rules.
- No rollback/backup procedure was verified.
- `backend/migrate_sqlite_to_postgres.py` is a legacy point-in-time migration helper and does not represent the current full schema.

Schema and data integrity:

- Recipe, price-rule, nutrition-correction, feedback, admin-session, plan-job, support-case, ML event, and policy-related tables exist.
- Plan job reads are owner-scoped via `database.get_plan_job(job_id, owner_uid=...)`; tests verify cross-user job reads return 404.
- Postgres job claiming uses `FOR UPDATE SKIP LOCKED`.
- SQL calls are mostly parameterized. The few f-string ORDER BY uses normalize the direction first.
- DB-level nutrition and content constraints are weak; bad admin values can reach planner data if not blocked at the API layer.
- Duplicate function definitions for `save_feedback()` and `get_recent_feedback()` appear in `backend/database.py`, creating maintainability risk.

Recipe catalog status:

- `seed_recipes()` loads `backend/recipes.json`, preserves existing seed IDs by default, and supports force reseed through API/env.
- Force reseed overwrites existing seed IDs, which can overwrite admin edits for those IDs.
- `get_recipe_catalog_status()` reports seed/source counts, missing seed IDs, and extra database records.

Exact files affected:

- `backend/database.py`
- `backend/db_url.py`
- `backend/migrate_sqlite_to_postgres.py`
- `backend/recipes.json`

## 10. Planner and ML Audit

Stage 1:

- `shortlist_candidates()` in `backend/services/meal_planner.py` performs deterministic filtering for allergies, restrictions, prep time, pantry threshold when configured, pricing, and candidate pruning.
- Allergy normalization and exposure detection exist through `normalize_allergy_constraints()` and `restriction_failure_reasons()`.
- Price estimates are integrated through `backend/price_catalog.py`, including DB-backed overrides and market seasonality multipliers.
- Stage 1 records diagnostics such as candidate counts, exclusion summaries, pricing diagnostics, and ML score timing.

LightGBM:

- `backend/services/ml_ranker.py` loads `PCOSINA_ML_MODEL_PATH` and optional `PCOSINA_ML_METRICS_PATH`.
- Missing model path results in `ready=False` and `score()` returns `None`.
- Predictions are checked for finite numeric values.
- Feature vectors are completed through `complete_stage1_feature_vector()`.
- Tests in `backend/tests/test_ml_ranker.py` cover missing model fallback, real artifact loading when available, and trained feature-column alignment.
- Risk: `Stage1Policy` has `ML_shadow_enabled=True`, and `_stage1_ml_applies_to_ranking()` applies scoring to ranking when shadow is enabled. This is assistive and after hard filters, but the naming can mislead operators into thinking shadow mode is observe-only.

CP-SAT:

- `solve_meal_plan()` builds an OR-Tools CP-SAT model.
- Hard constraints include one recipe per slot, meal-type compatibility, disallowed assignment zeroing, adjacent duplicate prevention, per-recipe repeat limits, and weekly budget cap when a budget exists.
- Pantry is rewarded and softly minimum-constrained with slack; pantry feasibility is not universally hard unless Stage 1 pantry threshold is configured above zero.
- Nutrition targets for calories/macros/fiber/sodium/sugar are modeled as deviations/slacks in the objective. This improves feasibility but means nutrition min/max are not strict hard constraints.
- Timeout and budget checks exist through `_check_planner_budget()` and solver policy time limits.
- `_greedy_fallback_plan()` still exists, but current `solve_meal_plan()` evidence shows CP-SAT is the authoritative return path rather than a greedy production fallback.

No-safe-plan handling:

- Backend no-safe responses use structured status `no-safe-plan`, machine reason codes, human guidance, suggested relaxations, diagnostics summary, and timestamps.
- Android `presentNoSafePlan()` preserves a continuity plan if the solver returns no-safe and continuity exists.
- The warmup-failure branch still needs continuity handling.

Exact files affected:

- `backend/services/meal_planner.py`
- `backend/services/ml_ranker.py`
- `backend/services/ml_features.py`
- `backend/services/plan_response_builder.py`
- `backend/price_catalog.py`
- `backend/tests/test_meal_planner.py`
- `backend/tests/test_ml_ranker.py`
- `backend/tests/test_no_safe_plan_contract.py`

## 11. Security Audit

Auth/admin:

- Mobile planner, recipe, job, and ML endpoints require Firebase Auth and App Check.
- Admin principal construction supports claims/allowlists with production checks for verified email, MFA, and recent auth.
- Admin browser sessions are signed and persisted in `admin_sessions`.
- CSRF validation is present for browser admin mutations.
- Operator override/blocking tables exist.

CORS/secrets:

- `TrustedHostMiddleware` is configured; production disallows wildcard hosts.
- No CORS middleware was found. This is acceptable for native Android plus same-origin admin pages, but must be revisited if browser clients are added.
- Render secrets use generated or `sync: false` env vars for session secret, Firebase service account, Sentry DSN, UID hash salt, and webhook key.
- `google-services.json` exists for Firebase app config. It is not a server secret, but release governance should still control it.

SQL safety:

- Most SQL uses `%s` or `?` parameters.
- Dynamic ORDER BY in feedback reads normalizes the direction to ASC/DESC first.
- No direct secret printing was observed in the audited paths, but production logs should be reviewed continuously.

Privacy:

- ML events use `uid_hash()` instead of raw UIDs.
- Production readiness rejects default/short UID hash salt.
- Android local plan/grocery/pantry/reflection artifacts use encrypted preferences where routed through `ReflectionStore`.

Rate limiting and abuse controls:

- Request size middleware defaults to 512 KB.
- Global rate limiting exists and production readiness rejects memory rate limiting.
- Feedback has an additional rate limit.
- Planner-specific high-cost rate limiting is not deeply specialized beyond the global/request/queue controls.

Exact files affected:

- `backend/main.py`
- `backend/database.py`
- `backend/ml_events.py`
- `render.yaml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt`
- `app/src/main/java/com/pcosina/app/data/repository/AuthRepository.kt`
- `app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt`

## 12. Offline-First Audit

What works offline:

- Saved meal plan reads are local through `UserPreferencesRepository.getSavedPlanJson()` and plan history.
- Grocery data is local through `GroceryLocalRepository`, `GroceryViewModel`, and secure grocery artifact methods.
- Pantry entries are local through `getPantryEntries()` and `savePantryEntries()`.
- Progress/reflection data is local through `ReflectionStore` and local repositories.
- Recipe details and summaries can fall back to `RecipeSnapshotStore` after a successful catalog sync.
- Cloud sync failures in profile/artifact flows are generally logged and do not block local persistence.

What does not work offline:

- New plan generation requires backend/Firebase/App Check access.
- Recipe snapshot fallback requires a previous successful catalog sync.
- Admin/operator workflows require backend access.
- Firebase-authenticated backend calls cannot proceed without a valid local auth state/token.

Saved-data behavior:

- Last plan/grocery/pantry artifacts are stored locally, with encrypted artifacts used for sensitive data.
- `savePlanHistoryJson()` does not update the Plan artifact sync timestamp, unlike `savePlanJson()` and `saveActivePlanId()`.

Network failure behavior:

- `MealPlanRepository.kt` maps DNS/connect/timeouts/SSL/App Check/HTTP auth/schema errors to user-facing messages.
- Backend warmup failure during generation currently produces an error instead of keeping the continuity plan on screen.

## 13. DevOps Audit

Render:

- `render.yaml` provisions `pcosina-postgres`, `pcosina-redis`, a Python web service, and a Python worker.
- Web service uses Gunicorn with Uvicorn workers, two workers, 120-second timeout, and `/health/ready`.
- Worker runs `python worker_plan_jobs.py`.

Postgres:

- `DATABASE_URL` is provided from Render database connection string.
- Startup runs database migrations and recipe seeding.
- Live SSL behavior is delegated to the connection string/provider; no explicit SSL verification code was audited.

Redis/worker:

- `PCOSINA_QUEUE_BACKEND=redis` and `PCOSINA_RATE_LIMIT_BACKEND=redis` are set in the web service.
- Worker uses Redis queue signaling and falls back to DB polling.
- Worker handles retry, backoff, dead-letter, success, no-safe, and diagnostic events.

Environment variables:

- Production env sets `PCOSINA_ENV=production`, `PCOSINA_ENFORCE_APP_CHECK=true`, `PCOSINA_ASYNC_MODE=queued`, and non-synced secret placeholders.
- `SENTRY_DSN`, `FIREBASE_SERVICE_ACCOUNT_JSON`, and `PCOSINA_UID_HASH_SALT` are required but not verified live in this audit.

Health checks:

- `/health/ready` includes runtime readiness, broker health, rate-limit health, and schema migration readiness.
- Go-live workflow exists, but its first mobile ML event coverage step currently fails locally.

Deployment risks:

- `backend/requirements.txt` is not fully pinned.
- Production release builds have minification disabled.
- No live Render deployment or rollback test was executed in this audit.

## 14. Testing Audit

Tests found:

- Backend tests under `backend/tests`.
- Android unit tests under `app/src/test`.
- Android connected/instrumentation tests and visual baselines under `app/src/androidTest`.
- GitHub workflows for CI, connected instrumentation, ML checks, and go-live gates.

Tests run:

| Command | Result |
|---|---|
| `python scripts/check_mobile_ml_event_coverage.py` | Failed. Missing 9 required Android ML events. |
| `python -m pytest backend/tests -q -p no:cacheprovider` | Passed: 284 passed, 1 Pytest config warning, about 33 seconds. |
| `.\\gradlew.bat :app:testDebugUnitTest --no-daemon` | Failed before test execution because `JAVA_HOME`/`java` was not configured in the shell. |
| `. .\\scripts\\android-env.ps1; .\\gradlew.bat :app:testDebugUnitTest --no-daemon` | Passed: BUILD SUCCESSFUL in about 25 seconds. |

Pass/fail results:

- Backend unit/integration-style test confidence is strong.
- Android JVM test confidence is good after sourcing the repo environment script.
- Mobile go-live telemetry coverage is failing.
- Connected Android instrumentation was not run in this audit because it requires emulator/device availability and is slower/hardware-dependent.

Important missing tests:

- CP-SAT hard nutrition invariant tests.
- Admin request validation rejection tests for negative/oversized recipe and price-rule fields.
- Offline warmup failure continuity UI tests.
- Plan history artifact sync timestamp tests.
- Connected instrumentation run evidence for current dirty tree.
- Live Render `/health/ready` verification with production secrets.

Commands to rerun:

```powershell
python scripts/check_mobile_ml_event_coverage.py
python -m pytest backend/tests -q -p no:cacheprovider
. .\scripts\android-env.ps1; .\gradlew.bat :app:testDebugUnitTest --no-daemon
. .\scripts\android-env.ps1; .\scripts\run_connected_android_tests.ps1 -AttemptAdbFix
```

## 15. Final Action Plan

| Priority | Task | Files likely affected | Verification command | Expected result | Risk if ignored |
|---|---|---|---|---|---|
| Critical | Fix missing Android ML events | `MealPlanViewModel.kt`, `GroceryViewModel.kt`, pantry/progress/recipe screens | `python scripts/check_mobile_ml_event_coverage.py` | Coverage passed | Go-live gate remains red; ML feedback loop incomplete |
| Critical | Make nutrition hard constraints or update contract | `backend/services/meal_planner.py`, planner tests | `python -m pytest backend/tests/test_meal_planner.py backend/tests/test_planner_input_hardening.py -q` | Tests prove hard nutrition behavior or documented no-safe response | Unsafe mismatch between product contract and solver behavior |
| Critical | Add admin content bounds | `backend/domain/models.py`, `backend/database.py`, admin tests | `python -m pytest backend/tests/test_admin_recipe_api.py backend/tests/test_admin_price_rule_api.py backend/tests/test_admin_nutrition_correction_api.py -q` | Invalid admin data rejected | Bad catalog data can poison planner and grocery guidance |
| High | Fix recipe route ID encoding | `Routes.kt`, recipe navigation tests | `. .\scripts\android-env.ps1; .\gradlew.bat :app:testDebugUnitTest --no-daemon` | Route tests pass | Admin IDs can crash/misroute recipe details |
| High | Preserve continuity on backend warmup failure | `MealPlanViewModel.kt`, UI/unit tests | Android unit + no-safe/offline tests | Saved plan remains visible with warning | Offline-first UX fails during backend outage |
| High | Sync plan history timestamp | `UserPreferencesRepository.kt`, `ArtifactSyncPolicyTest.kt` | Android unit tests | Plan history updates sync metadata | Plan history can fail to sync reliably |
| High | Pin backend deps | `backend/requirements.txt`, optional constraints file | `pip install -r backend/requirements.txt; python -m pytest backend/tests -q` | Reproducible installs | Upstream dependency drift can break production |
| High | Run connected instrumentation | `app/src/androidTest`, CI/emulator config | `. .\scripts\android-env.ps1; .\scripts\run_connected_android_tests.ps1 -AttemptAdbFix` | Connected tests pass | UI/runtime bugs escape unit tests |
| Medium | Add Postgres pooling and backup/restore verification | `backend/database.py`, scripts, Render docs/config | Backend tests plus staging readiness check | Stable DB load and recovery evidence | Connection churn and data loss risk |
| Medium | Verify deployed `/health/ready` | `render.yaml`, environment secrets | `curl https://pcosina-backend.onrender.com/health/ready` | `ok: true` with healthy Redis/schema/rate limit | Deployment can look configured but fail at runtime |

## 16. Final Verdict

Is the frontend 100% production-ready? No. It is functionally broad and unit-tested, but mobile ML event coverage fails, connected tests were not run, route encoding has an edge case, and offline generation failure handling is incomplete.

Is the backend 100% production-ready? No. It is strong and backend tests pass, but admin validation, some error responses, and deployment/live readiness remain incomplete.

Is PostgreSQL/database 100% production-ready? No. Migrations and tables are substantial, but pooling, DB-level constraints, rollback/backup verification, and legacy migration cleanup are still needed.

Is planner/optimization 100% production-ready? No. CP-SAT is implemented and deterministic hard filters exist, but nutrition min/max behavior is not strictly hard.

Is ML integration 100% production-ready? No. Model fallback is safe, but mobile telemetry coverage fails and model freshness/live canary readiness was not fully verified here.

Is security 100% production-ready? No. Good controls exist, but production security depends on live env correctness and admin input hardening.

Is offline-first behavior 100% production-ready? No. Saved data works offline, but new generation is backend-bound and continuity handling has gaps.

Is deployment 100% production-ready? No. Render config exists, but dependency pinning, live readiness verification, and go-live gate success are missing.

Is testing 100% production-ready? No. Backend and Android unit tests pass, but the mobile ML gate fails and connected tests were not run.

Is the overall system 100% production-ready? No. The system is suitable for controlled beta/pilot work, not a full public production release.
