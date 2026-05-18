# PCOSINA Full-Stack Production Readiness Audit

Audit date: 2026-05-17  
Repository: `C:\Users\salva\AndroidStudioProjects\PCOSINA2`  
Audit stance: production readiness, security, QA, thesis-defense technical validation  
Verdict language: strict; 100% is not used unless fully verified

## A. Executive Summary

PCOSina is a strong pre-production system and is credible for a thesis defense if claims are narrowed to match the current code. It is not yet ready for unrestricted real production users. The backend, PostgreSQL catalog, CP-SAT planner, admin catalog tooling, App Check controls, and Android local persistence are materially beyond prototype level. The remaining risks are mostly around release evidence, CI validity, full offline generation claims, documentation drift, migration operations, and end-to-end production verification.

Overall production readiness: **76%**

Thesis defense readiness: **Ready with corrections.** The project can be defended as an offline-first Filipino-PCOS meal-planning decision-support system using deterministic filtering, optional LightGBM-assisted candidate ranking, and a **MILP-formulated 0-1 assignment model solved using OR-Tools CP-SAT**. Defense materials must stop implying pure MILP, SQLite-primary production storage, pantry-hard-constrained planning by default, or fully offline new-plan generation.

Real production user readiness: **Not yet.** A controlled pilot is reasonable after the critical/high-priority items below are addressed and verified. Public production should wait for release-signing proof, connected Android testing, CI repair, live Redis/load evidence, staged sync/reinstall evidence, and alerting/incident procedures.

Top 10 issues blocking 100%:

1. `.github/workflows/ci.yml` appears malformed around the compact visual regression `else/fi` block; CI reliability is not currently trustworthy.
2. Full production evidence is incomplete: no verified secret-backed release build, connected instrumentation run, load test, staged sync/reinstall drill, or alert routing proof in this audit.
3. Complete offline meal plan generation is not implemented. Offline behavior currently covers saved/local data and recipe snapshot/cache behavior, not guaranteed new plan solving offline.
4. Nutrition, macro, sodium, sugar, and fiber targets are optimized with deviation penalties in `backend/services/meal_planner.py`; they are not all hard constraints.
5. Pantry behavior is pantry-aware and reward/threshold based, not strictly pantry-constrained by default.
6. Several defense documents are stale and still use SQLite-default, pure MILP, pantry-constrained, heuristic fallback, or full-offline wording.
7. Startup migrations and recipe seeding run inside app startup. This has already caused a Render deploy failure on 2026-05-16 when existing feedback data violated a new check constraint.
8. `backend/database.py` contains duplicate `save_feedback` and `get_recent_feedback` definitions; the later versions override the earlier versions.
9. Some admin/write schemas are too permissive for production content management, and debug Android builds can use BODY-level HTTP logging.
10. LightGBM canary support is present, but broad live rollout evidence is incomplete and the `ML_shadow_enabled` policy name is confusing because code can use it for live ranking.

Audit reproducibility note: the worktree was dirty during this audit. The following local changes were present and must be considered part of the audited state, not necessarily committed production baseline:

```text
 M app/src/main/java/com/pcosina/app/data/api/PcosinaApiService.kt
 M app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt
 M app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt
 M backend/database.py
 M backend/main.py
 M backend/tests/test_admin_recipe_api.py
?? app/src/main/java/com/pcosina/app/data/repository/RecipeSnapshotStore.kt
?? app/src/test/java/com/pcosina/app/RecipeSnapshotCachePolicyTest.kt
```

## B. Readiness Scorecard

| Area | Score | Verdict | Evidence | Main Risks | Required Action |
|---|---:|---|---|---|---|
| Frontend/UI readiness | 78% | Functional and broad, not fully production-verified | Compose navigation in `app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt`; route catalog in `Routes.kt`; local repositories and UI states in `MealPlanViewModel.kt`; targeted Android tests passed | No connected UI run in this audit; debug BODY logging; first-run offline planning not guaranteed; recipe snapshot code is untracked | Run connected tests and visual smoke tests; harden logging; commit and expand offline snapshot tests |
| Backend/API readiness | 82% | Strong backend foundation | FastAPI app in `backend/main.py`; auth/App Check dependencies; `/health/ready`; async plan jobs; admin/session/CSRF controls; 121 focused backend tests passed | Monolithic app; some permissive write models; startup migration coupling; no full live Render verification in audit | Split risky flows, tighten schemas, run full suite and live smoke |
| Database readiness | 79% | Good PostgreSQL baseline, operationally risky | PostgreSQL-only production enforcement in `backend/database.py`; schema migrations; owner-scoped jobs; recipe soft delete; indexes; feedback constraint repair | Startup migrations can fail deploy; no rollback/down migrations; duplicate functions; backup/restore not verified | Move migrations to release job, add migration tests for dirty data, document backup/restore |
| Recipe catalog/content readiness | 82% | Admin-editable DB catalog exists | `recipes` metadata columns, `/recipes/catalog`, admin recipe APIs, non-destructive seeding, catalog status and tests | Force reseed can overwrite edits; import validation/versioning limited; live DB row count not verified by this audit | Add content version review workflow, import validation, admin edit audit views |
| Planner/optimization readiness | 82% | Strong deterministic planner | `backend/services/meal_planner.py` uses OR-Tools CP-SAT; Stage 1 filtering; allergy/restriction gates; budget hard constraint; no-safe-plan response; planner tests passed | Nutrition targets mostly soft; pantry mostly soft; runtime/load evidence limited; stale MILP wording | Align docs and thesis claims; add performance/load fixtures; classify hard vs soft constraints in API metadata |
| ML readiness | 69% | Safe assistive path, not mature rollout | `backend/services/ml_ranker.py`; LightGBM load failure returns `None`; bounded Stage 1 weight; canary docs and tests | Stale/missing model handling is safe but not product-complete; broad canary evidence incomplete; `ML_shadow_enabled` naming misleading | Rename policy flags, add sustained canary evidence, drift/fallback dashboards |
| Security readiness | 74% | Many controls exist, but production proof incomplete | Firebase auth, App Check, operator sessions, CSRF, secure cookies, request size limit, TrustedHost, admin access controls, security-focused tests | Debug HTTP BODY logging; loose schemas; rate-limit Redis failure behavior needs production proof; no penetration/abuse test evidence | Redact debug logs, tighten Pydantic models, fail readiness on Redis ping failure in production, add abuse tests |
| Offline-first readiness | 73% | Offline access is real but incomplete | DataStore and encrypted local artifacts; saved plan/grocery/progress/reflection flows; recipe snapshot/cache code present | No complete offline new-plan generation; first-run offline cannot use server catalog; snapshot lacks TTL/schema/version policy | Define offline contract precisely, implement Android-side planner or clearly limit to cached plans/catalog |
| DevOps/deployment readiness | 66% | Render blueprint is useful, CI/release evidence weak | `render.yaml` defines web, worker, PostgreSQL, Redis, health check; production env guards in `backend/main.py`; release checklist exists | CI YAML appears broken; loose dependency pins; startup migrations; no verified live load/Redis/alert drills | Repair CI, pin/lock dependencies, separate migrations, complete release checklist |
| Testing/QA readiness | 78% | Good focused tests, incomplete full proof | 121 backend tests passed; targeted Android unit tests passed; many policy/admin/planner tests exist | Full suite not run; connected instrumentation not run; CI validity risk; no load/E2E proof | Run full backend, full Android, connected tests, CI, load, and staging drills |
| Documentation/thesis alignment | 74% | Main docs improving, defense pack still stale | `README.md`, `ARCHITECTURE.md`, `docs/thesis_validation/*` mostly reflect decision-support and CP-SAT reality | Defense docs still mention SQLite default, pantry-constrained MILP, heuristic fallback, full offline execution | Update defense and release docs before defense |
| Overall production readiness | 76% | Strong pre-production, not public production | Cross-stack evidence above | Release evidence, CI, offline wording, migration operations, docs drift | Treat next milestone as controlled pilot readiness |

## C. Critical Findings

### Critical blockers

1. **CI workflow appears broken.** In `.github/workflows/ci.yml`, the compact visual regression block places an `else`/`fi` after the `upload-artifact` `with:` section. This appears syntactically or logically invalid and means CI cannot be trusted until fixed and run successfully.
2. **Public production readiness is not verified.** This audit did not verify a secret-backed Android release build, connected instrumentation suite, live Redis behavior under load, Render worker throughput, staged sync/reinstall recovery, or alert routing.
3. **Do not claim fully offline meal plan generation.** Current evidence supports offline-first saved/local data and recipe snapshot/cache behavior. New plan generation still depends on backend solver paths unless an Android-side solver path is added and tested.
4. **Do not claim all nutrition targets are hard constraints.** `backend/services/meal_planner.py` models calories/macros/fiber/sodium/sugar through deviation variables and objective penalties. Budget and assignment constraints are hard; nutrition is largely target-optimized.

### High-priority issues

1. **Startup migration/seeding risk.** `backend/main.py` calls `database.init_db()` and `database.seed_recipes()` during lifespan startup. The Render failure on 2026-05-16 showed how a data constraint can prevent workers from booting.
2. **Defense docs are stale.** Files under `defense/` still include wording such as SQLite default storage, pantry-constrained MILP, heuristic fallback, and offline execution. These are risky in a thesis defense.
3. **Dependency reproducibility is weak.** `backend/requirements.txt` uses broad lower bounds such as `ortools`, `lightgbm>=4.0.0`, and `fastapi>=0.103.0`, so production builds can change under the same code.
4. **Duplicate database functions create maintenance risk.** `backend/database.py` defines `save_feedback` and `get_recent_feedback` twice; the later definitions override the earlier ones.
5. **Admin/content validation needs tightening.** `AdminRecipeUpsertRequest` and several write models in `backend/domain/models.py` do not consistently use `extra="forbid"` or strict bounds on names, nutrition, prices, and strings.
6. **ML rollout evidence remains incomplete.** `backend/services/ml_ranker.py` is safe on missing/stale models, but broad live canary, drift, fallback, and cohort evidence are still incomplete.

### Medium-priority improvements

1. Rename or split `ML_shadow_enabled` because `backend/services/meal_planner.py` can use it as a live Stage 1 ranking switch.
2. Make production readiness fail if Redis rate limiting or queue health cannot be proven, not merely if URLs are configured.
3. Add a recipe content versioning/review workflow so admin edits, seed changes, force reseeds, and thesis inventory exports are traceable.
4. Add snapshot version/TTL/reconciliation behavior to Android recipe snapshot storage.
5. Review exported Android receivers, especially notification reschedule behavior, for spoofing and permission exposure.
6. Add explicit API response schemas for important endpoints instead of relying on broad dictionaries.
7. Add performance budgets for CP-SAT solve time by days, slots, recipe count, and policy settings.

### Low-priority polish

1. Replace stale runtime/log labels such as "MILP Brain" with CP-SAT or planner-neutral language.
2. Reduce `backend/main.py` size by moving admin, planning, feedback, and health routes into routers.
3. Remove absolute Windows-looking links from docs if any are intended for GitHub rendering.
4. Standardize wording across README, architecture, backend config, defense, and thesis validation docs.

## D. Frontend Audit

### What is strong

The Android app has broad route coverage and stateful flows. `app/src/main/java/com/pcosina/app/ui/navigation/Routes.kt` defines core user, meal plan, grocery, progress, settings, recipe, and operator/admin routes. `AppNavHost.kt` wires profile, goal, dashboard, meal plan, grocery, progress, recipe details, operator, and admin methodology screens. Route classification and guard tests exist, including `RoutesClassificationTest` and `NavigationUsagePolicyTest`.

Local-first persistence exists. `UserPreferencesRepository.kt` uses DataStore for user profile and application artifacts. `ReflectionStore.kt` uses encrypted preferences for reflection and spending logs. Grocery, planner, progress, and profile repositories wrap local persistence rather than depending directly on UI state.

Meal plan UI state handling is present. `MealPlanViewModel.kt` has loading, success, error, no-safe-plan, recipe details, and grocery-related states. Backend failures are mapped into user-facing error categories in `MealPlanRepository.kt`.

Recipe offline snapshot support is now present in the audited worktree. `RecipeSnapshotStore.kt` stores a local `recipe_catalog_snapshot.json`, and `MealPlanRepository.kt` falls back to the snapshot for recipe summaries/details after sync.

### What is weak

No connected Android instrumentation or full UI screenshot pass was run in this audit. The frontend is not proven visually correct across device sizes, network failures, process death, and first-run offline usage.

Debug builds use an OkHttp BODY-level logging interceptor in `MealPlanRepository.kt`. Release uses `NONE`, but debug builds with real user data or respondent data can leak sensitive request/response content to logs unless headers and bodies are redacted.

The new recipe snapshot implementation is untracked in the audited worktree. It should be committed, reviewed, and expanded with schema/version tests before being treated as stable.

### Missing states/screens

The major routes exist, but production confidence is missing for:

- first-run offline with no cached recipe snapshot;
- stale recipe snapshot after admin deletion or forced reseed;
- interrupted profile sync and reinstall recovery;
- multi-device conflict behavior;
- long no-safe-plan explanation flows;
- large recipe catalog browsing/search performance;
- operator/admin mobile flows under expired sessions and denied roles.

### Offline behavior risks

Offline-first currently means the app can retain local profile/artifacts, saved plan data, grocery/progress/reflection state, and now recipe snapshots after successful sync. It does not mean the Android app can always generate a new optimized meal plan without the backend. The thesis and UI copy should avoid "complete offline generation" unless an Android-side deterministic solver path is implemented and tested.

### Recommended fixes

1. Run connected instrumentation tests on at least one emulator and one physical device profile.
2. Add recipe snapshot TTL/schema version/migration tests.
3. Redact or disable BODY-level debug network logging for builds used with real respondent data.
4. Add first-run offline UX that clearly explains what is available without internet.
5. Add visual smoke screenshots for dashboard, meal plan, no-safe-plan, grocery, progress, recipe details, and settings.

## E. Backend Audit

### Endpoint readiness

The FastAPI backend is broad and production-aware. `backend/main.py` configures docs visibility, runtime readiness, Firebase auth, App Check, admin sessions, CSRF tokens, rate limiting, request-size middleware, health endpoints, planning endpoints, recipe catalog endpoints, feedback/support endpoints, and admin/operator routes.

Important verified paths:

- `/generate-plan` requires Firebase auth, App Check, and schema compatibility before solving.
- `/generate-plan-async` creates owner-scoped plan jobs.
- `/plan-jobs/{job_id}` enforces owner checks unless privileged admin/ops paths are used.
- `/recipes/catalog`, `/recipes/summary`, and `/recipe/{id}` expose active recipe data to authenticated clients.
- `/health/ready` reports runtime readiness.
- `/db-status` is restricted in production.
- Admin sessions use signed cookies, secure flags in production, and CSRF validation for browser mutations.

### Validation

Core request models in `backend/domain/models.py` include useful bounds for profile and plan generation. `FeedbackRequest` forbids extra fields and caps message length.

Validation is weaker for admin/content writes. Recipe upsert models should enforce `extra="forbid"`, max lengths, non-negative numeric bounds, plausible nutrition ranges, and enum consistency. This matters because admin routes are high-impact content management surfaces.

### Error handling

No-safe-plan handling is a strength. `backend/main.py` returns structured no-safe-plan responses and emits diagnostics/events rather than pretending a plan exists. The planner emits reason tags and diagnostics.

Startup error handling remains risky because migrations and seeding happen during app boot. A migration exception prevents workers from becoming healthy, as seen in the May 16, 2026 Render failure.

### Admin safety

Admin safety is materially improved. The backend avoids legacy shared-token admin access, uses signed sessions or verified Firebase operator identity, applies role dependencies, and includes CSRF protection for browser admin mutations.

Remaining risks are mostly operational:

- admin write schemas need stricter validation;
- content changes need version/review/audit workflows;
- operator session controls need live production verification;
- admin pages should be included in abuse and CSRF regression tests.

### Deployment concerns

Production runtime readiness checks are strong: `backend/main.py` rejects disabled App Check, missing admin session secrets, wildcard hosts, invalid production `DATABASE_URL`, memory queue backend in queued production, missing Redis URL, default UID hash salt, and other unsafe settings.

However, readiness does not fully replace deployment verification. Redis ping failure, worker liveness, queue depth, database migration state, and Sentry alert delivery need live checks before public production.

### Recommended fixes

1. Split startup migrations into an explicit release/migration job.
2. Add strict response models to core user-facing and admin endpoints.
3. Tighten admin Pydantic models.
4. Add live smoke tests for auth, App Check, plan generation, async jobs, recipe catalog, feedback, and admin content routes.
5. Refactor `backend/main.py` into route modules after release-critical correctness work.

## F. Database Audit

### PostgreSQL readiness

`backend/database.py` correctly treats PostgreSQL as required in production. If `DATABASE_URL` is not a valid PostgreSQL URL in production, startup fails rather than silently falling back to SQLite. SQLite remains useful for local development and tests.

The schema includes tables for recipes, plan jobs, schema migrations, feedback/support, policy/config, admin sessions/logs, operator access, price rules, grocery/pantry behavior, ML/planner events, and related operational data.

### Migrations

Schema migrations are tracked and run through `schema_migrations`. The migration list includes recipe metadata, plan job ownership, price rules, feedback constraints, and recipe catalog metadata.

Migration safety is improving but not fully production-grade. The feedback length migration now trims oversized historical feedback before applying `feedback_message_length_check`, which directly addresses the Render failure where row `id=2` had a 2001-character message. But running migrations during app startup remains a deploy availability risk.

### Schema status

Indexes exist for active recipes, plan jobs, admin logs, admin sessions, operator access, support cases, and related lookup paths. Plan jobs include `owner_uid`, `attempt_count`, and retry fields. Recipe rows include active/source/source_version/created/deleted metadata.

### Seed/import behavior

Recipe seeding is non-destructive by default and uses upsert/ignore behavior so admin-edited recipes are preserved unless force reseed is used. `get_recipe_catalog_status()` reports active counts, seed counts, missing seed count, and extra database count.

Force reseed can overwrite admin edits and reactivate rows. That may be necessary for controlled imports, but it needs an explicit production workflow and backup/review step.

### Recipe catalog status

The backend now supports DB-backed recipe catalog APIs and Android snapshot sync. User-side pgAdmin inspection reportedly showed recipes visible after deployment, but this audit did not run a live production database row-count query. Treat live catalog state as user-observed, not independently verified here.

### Risks and fixes

1. Move schema migrations and seed/import jobs out of FastAPI startup.
2. Add backup/restore and rollback runbooks for PostgreSQL.
3. Remove duplicate `save_feedback` and `get_recent_feedback` definitions.
4. Add migration tests using dirty historical data, not only clean schema creation.
5. Add recipe import validation and content version diffs before force reseed.

## G. Planner/Algorithm Audit

### Stage 1

Stage 1 is deterministic and preference-aware before optimization. `backend/services/meal_planner.py` normalizes allergies/restrictions, filters candidate recipes, applies cooking-time constraints, supports optional pantry threshold filtering, computes heuristic scores, and caps candidate lists before CP-SAT.

Hard filters are applied before ML scoring, which supports the project rule that ML cannot override hard constraints.

### LightGBM

LightGBM support is assistive. `backend/services/ml_ranker.py` loads a model from `PCOSINA_ML_MODEL_PATH`, reads feature metadata, returns `None` on load/scoring failure, and does not crash the planner when missing. `backend/services/meal_planner.py` clamps ML influence with policy caps and applies ML only after deterministic filtering.

Risk: the policy naming is confusing. `_stage1_ml_applies_to_ranking()` treats `ML_shadow_enabled` as a live ranking switch in some cases. Rename to separate shadow scoring from live candidate ranking.

### CP-SAT

The authoritative solver is OR-Tools CP-SAT. The safest wording is:

> deterministic rule-based filtering followed by a MILP-formulated 0-1 assignment model solved using OR-Tools CP-SAT.

The model uses boolean assignment variables, exactly-one recipe per meal slot, no adjacent repeat logic, weekly repeat limits, budget constraints, and objective terms for calories/macros, variety, preference, pantry match, cost, and other quality terms.

### Constraints

Strong hard constraints:

- exactly one selected recipe per requested slot;
- disallowed recipe-slot assignments set to zero;
- allergies and restrictions are filtered before solving;
- weekly budget cap when configured;
- no adjacent repeat and weekly repeat limits;
- cooking-time hard filter when provided.

Soft/objective constraints:

- calories and macro targets;
- fiber, sodium, and sugar targets;
- pantry match unless policy threshold makes it a Stage 1 filter;
- variety and preference tradeoffs;
- cost minimization below the hard budget.

This distinction must be clear in defense and thesis writing.

### No-safe-plan behavior

No-safe-plan behavior is one of the stronger production features. The planner can return structured diagnostics and the backend returns a clear no-safe-plan response rather than fabricating a plan. Tests cover planner behavior and reason-code style outputs.

### Runtime/performance risk

The solver has adaptive time limits and retry behavior, but this audit did not verify production load performance with the full recipe catalog, concurrent users, Redis queue pressure, and Render resource limits. Add solve-time performance budgets and load tests for representative profile complexity.

### Correctness risks

1. Nutrition claims can overstate hard guarantee behavior.
2. Pantry wording can overstate strict feasibility.
3. Stale "MILP" labels can confuse defense panels and developers.
4. Deprecated greedy fallback code remains for benchmarks; no production call was found, but it should stay clearly marked non-authoritative.

## H. Security Audit

### Auth/admin

The backend has meaningful production security controls:

- Firebase bearer auth for protected user routes;
- Firebase App Check enforcement in production;
- admin/operator role dependencies;
- signed admin sessions;
- secure, HttpOnly, SameSite cookies in production;
- CSRF protection for browser admin mutations;
- production readiness checks blocking unsafe auth modes.

The Android app uses App Check debug provider in debug and Play Integrity in release. Release signing is guarded by Gradle policy so insecure release signing must be explicitly overridden.

### CORS/secrets

No permissive `CORSMiddleware` configuration was found in the audited backend. Trusted hosts are configured, with production avoiding wildcard hosts.

Secrets are environment-driven. The audit did not print secrets. Render config uses generated or synced environment variables for sensitive fields. Missing real Sentry/Firebase/UID salt/worker secrets remain production readiness items.

### SQL injection risk

No obvious user-controlled SQL string interpolation was found in the inspected paths. Database writes use psycopg parameters. Some dynamic SQL paths use normalized or internal values. Continue to avoid f-string SQL for user inputs.

### Privacy

Privacy posture is good but not complete. The app avoids Android backup, uses encrypted preferences for sensitive reflections/spending logs, and backend logging appears mostly operational. Risks remain around debug HTTP BODY logs, Sentry event content, admin feedback exports, and whether production logs contain health/profile details.

### Security tests

Security-oriented tests exist and passed in the focused backend run, including App Check enforcement, admin feedback security, rate limit backend, async job ownership, runtime readiness, and database backend policy tests.

### Production concerns

1. Redact Authorization/App Check headers and sensitive JSON bodies from debug logs.
2. Tighten admin/write validation.
3. Add explicit abuse tests for large payloads, repeated login attempts, feedback spam, recipe import abuse, and job polling.
4. Make production readiness sensitive to Redis ping/rate-limit health, not just Redis URL presence.
5. Run a secrets scan and rotate any accidentally exposed local/test credentials.

## I. DevOps and Deployment Audit

### Render config

`render.yaml` is a good baseline. It defines:

- managed PostgreSQL service;
- Redis service;
- FastAPI web service using Python 3.12.8 and Gunicorn/Uvicorn;
- `/health/ready` health check;
- worker service running `worker_plan_jobs.py`;
- production environment variables for App Check, Redis-backed queue, Redis-backed rate limiting, Postgres, admin session secret, and runtime controls.

### Health/readiness

`backend/main.py` includes strong production readiness checks and `/health/ready`. The checks catch many misconfigurations before serving traffic.

Missing proof:

- live Redis ping failure behavior;
- worker queue drain behavior;
- Sentry alert receipt;
- production App Check failure mode on actual devices;
- database migration state after deploy.

### Worker/Redis/Postgres

Worker and queue code exists. PostgreSQL and Redis are configured in Render. Focused worker/job ownership tests passed. This audit did not run a live queue throughput or worker restart drill.

### Environment variables

Production environment expectations are explicit. The biggest issue is reproducibility, not missing names. `backend/requirements.txt` is not pinned, so Render builds can pull newer FastAPI, Starlette, OR-Tools, LightGBM, NumPy, pandas, or psycopg versions.

### Logs/monitoring

Sentry support exists in both backend and Android, but live DSN configuration and alert routing were not verified. Production should include a test event and documented owner/escalation path.

### Startup migration/seeding risk

This is a real risk, not theoretical. The May 16, 2026 Render failure happened because startup tried to add `feedback_message_length_check` while an existing row violated the constraint. The current code appears to remediate this case, but the deployment architecture still allows data issues to fail boot.

Recommended deployment model:

1. Build artifact.
2. Run migration job against production database.
3. Run seed/import job with dry-run status.
4. Start web and worker.
5. Run smoke tests.
6. Promote traffic.

## J. Test Coverage Audit

### What tests exist

Backend tests cover admin recipe APIs, admin content console UI, feedback storage constraints, schema migrations, meal planner behavior, ML ranker, ML events, App Check enforcement, admin feedback security, database backend policy, runtime readiness, rate-limit backend, worker plan jobs, and async job ownership.

Android tests cover recipe snapshot cache policy, recipe mappers, local repository policies, App Check policy, manifest security policy, release signing policy, route classification, navigation usage policy, and connectivity observer policy.

### What tests passed

Focused backend command run during audit:

```powershell
python -m pytest backend/tests/test_admin_recipe_api.py backend/tests/test_admin_content_console_ui.py backend/tests/test_feedback_storage_constraints.py backend/tests/test_schema_migrations.py backend/tests/test_meal_planner.py backend/tests/test_ml_ranker.py backend/tests/test_ml_events.py backend/tests/test_app_check_enforcement.py backend/tests/test_admin_feedback_security.py backend/tests/test_database_backend_policy.py backend/tests/test_runtime_readiness.py backend/tests/test_rate_limit_backend.py backend/tests/test_worker_plan_jobs.py backend/tests/test_async_job_ownership.py -q
```

Result: **121 passed in 14.38s**.

Focused Android command run during audit:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest --tests "com.pcosina.app.RecipeSnapshotCachePolicyTest" --tests "com.pcosina.app.RecipeMappersTest" --tests "com.pcosina.app.PlannerLocalRepositoryPolicyTest" --tests "com.pcosina.app.UserProfileLocalRepositoryPolicyTest" --tests "com.pcosina.app.GroceryLocalRepositoryPolicyTest" --tests "com.pcosina.app.AppCheckPolicyTest" --tests "com.pcosina.app.ManifestSecurityPolicyTest" --tests "com.pcosina.app.ReleaseSigningPolicyTest" --tests "com.pcosina.app.RoutesClassificationTest" --tests "com.pcosina.app.NavigationUsagePolicyTest" --tests "com.pcosina.app.ConnectivityObserverPolicyTest"
```

Result: **BUILD SUCCESSFUL**.

### What important tests are missing

1. Full backend test suite in a clean environment.
2. Full Android unit suite in a clean environment.
3. Connected Android instrumentation tests.
4. Visual regression screenshots for major Compose screens.
5. Live Render smoke tests.
6. Load tests for concurrent plan generation and queue depth.
7. Redis outage and database outage drills.
8. Production App Check validation on release builds.
9. Reinstall/multi-device sync recovery tests.
10. Backup/restore and migration rollback drills.

### Commands to run

Recommended pre-release commands:

```powershell
python -m pytest backend/tests -q
```

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest
```

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat connectedDebugAndroidTest
```

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease
```

Run the GitHub Actions workflows after repairing `.github/workflows/ci.yml`.

### Confidence level

Confidence is **moderate-high for code structure and focused backend behavior**, **moderate for Android unit behavior**, and **low for real production operations** because live release, load, Redis, alerting, connected UI, and staging drills were not verified.

## K. Documentation and Thesis Alignment Audit

### Claims that are supported

Supported claims:

- PCOSina is a wellness decision-support app, not a diagnostic or medical device.
- The system is offline-first in local persistence and cached/saved data behavior.
- Production backend uses PostgreSQL.
- Android uses local DataStore/encrypted preference/artifact persistence.
- The planner performs deterministic filtering followed by OR-Tools CP-SAT assignment optimization.
- LightGBM is assistive and Stage 1 only.
- ML does not override hard filters when used after deterministic filtering.
- No-safe-plan handling exists.
- Admin recipe catalog management exists.

### Claims that must be revised

Revise or remove:

- "SQLite is the primary/default production database." Current production authority is PostgreSQL; SQLite is for local/dev/test.
- "Classical MILP solver" or "pure MILP planner." Use "MILP-formulated 0-1 assignment model solved using OR-Tools CP-SAT."
- "Pantry-constrained" if it implies every selected recipe must be pantry-feasible. Use "pantry-aware" unless a hard pantry threshold is explicitly enabled.
- "Fully offline generation" unless Android-side solving is implemented and tested. Use "offline-first saved access and cached recipe snapshot."
- "Greedy heuristic fallback" as production planner behavior. Deprecated greedy code is retained for benchmarks, not authoritative production solving.
- Clinical/treatment claims. Keep decision-support and wellness wording.

### Outdated wording to remove

Files needing review include:

- `defense/PCOSINA_Defense_CheatSheet.md`: pantry-constrained MILP, offline MILP, recipes seeded into SQLite.
- `defense/PCOSINA_Developer_Terminology_Mastery_Kit.md`: SQLite default backend storage and heuristic fallback wording.
- `defense/PCOSINA_Technical_Dossier.md`: SQLite default/Postgres optional, non-ML approach, offline execution.
- `defense/PCOSINA_FinalDefense_SpeakerNotes.md`: broad MILP wording.
- `defense/PCOSINA_Flashcards.md`: simplify to CP-SAT wording consistently.
- Any docs using "MILP Brain" should be updated to planner-neutral or CP-SAT wording.

### Safe thesis wording

Use this wording:

> PCOSina is an offline-first Filipino-PCOS meal-planning decision-support system. It uses deterministic preference-aware filtering, optional LightGBM-assisted Stage 1 candidate ranking, and a MILP-formulated 0-1 assignment model solved using OR-Tools CP-SAT. Allergies, excluded ingredients, explicit hard restrictions, budget caps, assignment feasibility, and repetition rules are enforced deterministically. Nutrition and pantry behavior are optimized according to policy and should be described according to the specific hard or soft constraints implemented.

For offline behavior:

> Offline-first means the app preserves local profiles, saved plans, grocery/progress/reflection data, and cached recipe catalog data where available. New optimized plan generation may require backend availability unless a tested Android-side solver path is added.

## L. Action Plan

| Priority | Task | Files likely affected | Why it matters | Estimated effort | Risk if ignored |
|---|---|---|---|---:|---|
| P0 | Repair CI workflow syntax and run CI successfully | `.github/workflows/ci.yml` | CI cannot be trusted until workflow validity is proven | 0.5 day | Broken releases and false confidence |
| P0 | Update defense docs to correct CP-SAT/Postgres/offline/pantry wording | `defense/*`, `docs/thesis_validation/*` | Prevents thesis defense contradictions | 0.5-1 day | Panel challenges and credibility loss |
| P0 | Run full backend, full Android, and connected tests | `backend/tests`, `app/src/test`, `app/src/androidTest` | Proves more than focused tests | 1 day | Unknown regressions |
| P1 | Move migrations/seeding out of app startup | `backend/main.py`, `backend/database.py`, `scripts/*`, `render.yaml` | Prevents deploy boot failures from data drift | 1-2 days | Repeat Render startup failure |
| P1 | Pin/lock backend dependencies | `backend/requirements.txt`, optional constraints file | Reproducible builds | 0.5 day | Surprise dependency breakage |
| P1 | Tighten admin/write schemas | `backend/domain/models.py`, admin tests | Reduces content/security bugs | 1 day | Invalid production catalog data |
| P1 | Remove duplicate feedback DB functions | `backend/database.py`, feedback tests | Avoids overridden logic and maintenance mistakes | 0.5 day | Silent behavior drift |
| P1 | Add live staging smoke tests | `scripts/*`, docs, Render settings | Verifies auth, App Check, DB, Redis, worker, catalog | 1-2 days | Production issues only found by users |
| P2 | Add recipe snapshot version/TTL/reconciliation | `RecipeSnapshotStore.kt`, `MealPlanRepository.kt`, tests | Makes offline catalog predictable | 1 day | Stale or inconsistent offline data |
| P2 | Add planner performance/load tests | `backend/tests`, `scripts/*` | Proves CP-SAT runtime under catalog size and concurrency | 1-2 days | Slow or timed-out plans |
| P2 | Rename ML shadow/live ranking flags | `backend/services/meal_planner.py`, policy docs/tests | Prevents rollout confusion | 0.5-1 day | Incorrect ML rollout operations |
| P2 | Redact debug HTTP logs | `MealPlanRepository.kt`, tests if applicable | Protects tokens and respondent data in debug builds | 0.5 day | Sensitive data in logs |
| P2 | Add backup/restore and migration rollback runbook | `docs/release/*`, scripts | Operational safety for PostgreSQL | 0.5-1 day | Data loss or slow recovery |
| P3 | Modularize backend routes | `backend/main.py`, route modules | Maintainability | 2-4 days | Slower future changes |
| P3 | Clean stale log/doc labels | `backend/database.py`, docs | Reduces terminology confusion | 0.5 day | Minor defense/dev confusion |

## M. Final Recommendation

Is frontend 100%? **No.** It is broad and functional, with local persistence and many states, but lacks connected test proof, visual verification, complete first-run offline behavior, and finalized recipe snapshot policy.

Is backend 100%? **No.** It has strong auth, App Check, admin, health, planner, and catalog routes, but startup migrations, schema looseness, monolithic structure, and live deployment proof remain issues.

Is database 100%? **No.** PostgreSQL integration, migrations, indexes, recipe catalog, and soft delete are good, but migration operations, duplicate functions, backup/rollback proof, and seed/edit workflows need hardening.

Is security 100%? **No.** The project has meaningful production controls, but debug logging, admin validation, Redis/rate-limit proof, abuse testing, and live alerting are incomplete.

Is testing 100%? **No.** Focused tests passed, but the full suite, connected Android tests, live staging tests, load tests, and CI workflow validity are not proven.

Is the overall system production-ready? **Not for unrestricted public production.** It is a strong closed-pilot candidate after CI repair, release evidence, staging smoke tests, migration hardening, and documentation corrections.

Is it thesis-defense-ready? **Yes, with corrected wording and clear boundaries.** Present PCOSina as an offline-first decision-support system with deterministic filtering, optional LightGBM Stage 1 ranking, and a MILP-formulated 0-1 assignment model solved using OR-Tools CP-SAT. Do not claim pure MILP, guaranteed full offline generation, or hard pantry/nutrition constraints unless the implementation is changed and tested.
