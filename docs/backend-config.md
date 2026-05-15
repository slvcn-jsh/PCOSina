# PCOSINA Backend Configuration

## API Contract

- **Schema Version:** `1.4.0`
- **Policy Schema Version:** `2.0.0`
- `POST /generate-plan` returns `status=success` or `status=no-safe-plan` (never unsafe fallback-as-success).
- `POST /ml/events` ingests validated ML telemetry events (non-authoritative).

## Versioned System Policy (DB-backed)

Policy records are typed, bounded, auditable, rollback-capable, and environment-aware (`environment_profile`, `environment_overrides`).
Canonical schema files:
- `shared-contracts/config_schemas/policy_config.v2.json` (primary)
- `shared-contracts/config_schemas/policy_config.v1.json` (compat alias during migration)

### Production Bootstrap Defaults

The default production bootstrap now applies a conservative latency profile for small hosted instances.
It keeps MILP/CP-SAT authoritative, but reduces search pressure before and during solve:

- `stage1.max_candidates_per_slot = 64`
- `stage1.restricted_shortlist_multiplier = 1.15`
- `stage1.pool_cap_top_share = 0.45`
- `solver.solver_time_limit_seconds = 4`
- `solver.solver_max_seconds = 7`
- `solver.total_solver_seconds = 14`
- `solver.retry_attempts = 1`
- `solver.solver_workers = 2`

These values are bootstrap defaults for production-like environments, not a replacement for explicit operator tuning.
Customized active policies should keep their explicit values.

Top-level policy namespaces:
- `nutrition`
- `planning`
- `stage1`
- `solver`
- `sync_offline`
- `sre`
- `security`

### Required externally managed constants

1. `nutrition`
- `calorie_min`, `calorie_max`, `carb_min`, `carb_max`, `protein_min`, `protein_max`, `fat_min`, `fat_max`, `fiber_min`, `sodium_max`, `sugar_max`, `meal_distribution_targets`, `daily_tolerance_percent`, `weekly_tolerance_percent`

2. `planning`
- `planning_horizon_days`, `meals_per_day`, `snack_rules`, `recipe_repeat_limits`, `cuisine_diversity_weight`, `pantry_utilization_weight`, `grocery_cost_weight`, `prep_time_weight`, `acceptance_score_weight`, `substitution_penalty`, `infeasibility_relaxation_order`
- Additional controls migrated from hardcoded logic: `meal_min_calorie_target`, `group_limit_floor`, `diversity_min_token_target`

3. `stage1`
- `max_candidates_per_slot`, `ranking_cutoff`, `similarity_threshold`, `pantry_match_threshold`, `exclusion_penalty_weights`, `cold_start_defaults`, `ML_shadow_enabled`, `ML_canary_enabled`, `ML_score_weight`, `ML_score_cap`
- `ML_shadow_enabled` is a legacy compatibility key name; when true it now enables live stage-1 ML ranking instead of score-only shadow collection
- Additional controls migrated from hardcoded logic: `restricted_shortlist_multiplier`, `budget_keep_min_count`, `budget_keep_min_ratio`, `minimum_candidates_required`, `pool_cap_top_share`

4. `solver`
- `solver_time_limit_seconds`, `max_solution_count`, `optimality_gap_target`, `infeasibility_diagnostic_depth`, `worker_memory_limit`, `retry_attempts`, `timeout_ms`, `circuit_breaker_threshold`, `queue_priority_rules`, `solver_max_seconds`, `total_solver_seconds`, `solver_workers`

5. `sync_offline`
- `sync_batch_size`, `sync_retry_backoff`, `dead_letter_threshold`, `local_cache_ttl`, `local_cache_max_entries`, `conflict_resolution_policy`, `reinstall_recovery_timeout`, `offline_read_guarantees`

6. `sre`
- `latency_slo_p50_ms`, `latency_slo_p95_ms`, `latency_slo_p99_ms`, `crash_free_target`, `api_error_budget`, `canary_cohort_percent`, `rollback_trigger_thresholds`, `observability_sampling_rate`

7. `security`
- `token_ttl`, `key_rotation_days`, `audit_log_retention_days`, `backup_retention_days`, `encryption_required_fields`

## Policy Admin Endpoints

- `GET /admin/policy/active`
- `GET /admin/policy/versions`
- `GET /admin/policy/audit`
- `POST /admin/policy/versions`
- `POST /admin/policy/activate`
- `POST /admin/policy/rollback`

### Browser Policy Console

- `GET /admin/policy`
- `POST /admin/policy/create`
- `POST /admin/policy/activate-form`
- `POST /admin/policy/rollback-form`

This page is a server-rendered browser console layered on top of the audited policy-admin services.
It shows the active policy, immutable version history, recent audit events, and a validated JSON editor for creating or activating new policy versions.

## Content Admin Endpoints

- `GET /admin/recipes`
- `GET /admin/recipes/{recipe_id}`
- `POST /admin/recipes`
- `PUT /admin/recipes/{recipe_id}`
- `DELETE /admin/recipes/{recipe_id}`
- `GET /admin/price-rules`
- `GET /admin/price-rules/{rule_id}`
- `POST /admin/price-rules`
- `PUT /admin/price-rules/{rule_id}`
- `DELETE /admin/price-rules/{rule_id}`
- `GET /admin/nutrition-corrections`
- `GET /admin/nutrition-corrections/{recipe_id}`
- `PUT /admin/nutrition-corrections/{recipe_id}`
- `DELETE /admin/nutrition-corrections/{recipe_id}`

### Browser Content Console

- `GET /admin/content`
- `GET /admin/content/recipes`
- `POST /admin/content/recipes/save`
- `POST /admin/content/recipes/{recipe_id}/delete`
- `GET /admin/content/price-rules`
- `POST /admin/content/price-rules/save`
- `POST /admin/content/price-rules/{rule_id}/delete`
- `GET /admin/content/nutrition-corrections`
- `POST /admin/content/nutrition-corrections/save`
- `POST /admin/content/nutrition-corrections/{recipe_id}/delete`

These pages are server-rendered operator tooling layered on top of the audited content-admin services.
They require a signed admin browser session and CSRF protection for mutations.

### Browser Ops Console

- `GET /admin/ops`
- `GET /admin/ops/support-cases`
- `POST /admin/ops/support-cases/create`
- `POST /admin/ops/support-cases/{case_id}/update`
- `POST /admin/ops/support-cases/{case_id}/notes`
- `GET /admin/ops/admin-sessions`
- `POST /admin/ops/admin-sessions/{session_id}/revoke`
- `POST /admin/ops/admin-sessions/cleanup`
- `GET /admin/ops/operator-access`
- `POST /admin/ops/operator-access/save`

These pages are server-rendered operator tooling layered on top of the audited ops-admin services.
They require a signed admin browser session and CSRF protection for mutations.

`/admin/content`, `/admin/ops`, `/admin/policy`, and `/admin/feedback` now expose shared console-switching navigation for multi-role operators so browser workflows do not depend on returning to the login landing page between admin tasks.

## Operator Inspection Endpoints

- `GET /ops/plan-jobs/diagnostics`
- `GET /ops/plan-jobs`
- `GET /ops/plan-jobs/{job_id}`
- `POST /ops/plan-jobs/{job_id}/requeue`
- `POST /ops/plan-jobs/{job_id}/replay`
- `GET /ops/admin-sessions`
- `POST /ops/admin-sessions/{session_id}/revoke`
- `POST /ops/admin-sessions/revoke-user/{uid}`
- `POST /ops/admin-sessions/cleanup`
- `GET /ops/operator-access`
- `GET /ops/operator-access/{uid}`
- `PUT /ops/operator-access/{uid}`
- `GET /ops/support-cases`
- `GET /ops/support-cases/{case_id}`
- `GET /ops/support-cases/{case_id}/export`
- `POST /ops/support-cases`
- `PATCH /ops/support-cases/{case_id}`
- `POST /ops/support-cases/{case_id}/notes`
- `GET /ops/users/{uid}/cloud-profile`
- `GET /ops/users/{uid}/support-bundle`
- `GET /ops/schema/migrations`
- `GET /health/ready`

Support cases carry operator handoff metadata:
- `assignee`
- `escalated`

`GET /ops/support-cases` also supports filtering by:
- `user_uid`
- `status`
- `assignee`
- `escalated`
- `q`

`/ops/schema/migrations` returns tracked migration status for both:
- the main application database schema
- the policy store schema

`/health/ready` now includes `schemaMigrations` and returns `503` if:
- schema status cannot be evaluated, or
- any tracked application or policy migration remains pending after bootstrap

## Environment Variables

Core environment switches (non-policy):
- `PCOSINA_ENV`
- `PCOSINA_ENABLE_DOCS`
- `PCOSINA_ALLOWED_HOSTS`
- `DATABASE_URL`
- `FIREBASE_SERVICE_ACCOUNT_JSON` or `FIREBASE_CREDENTIALS_PATH`
- `PCOSINA_ADMIN_SESSION_SECRET`
- `PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL`
- `PCOSINA_REQUIRE_OPERATOR_MFA`
- `PCOSINA_REQUIRE_RECENT_ADMIN_AUTH`
- `PCOSINA_ADMIN_MAX_AUTH_AGE_SECONDS`
- `PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS`
- `PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID`
- `PCOSINA_ADMIN_EMAILS`, `PCOSINA_ADMIN_UIDS`
- `PCOSINA_POLICY_ADMIN_EMAILS`, `PCOSINA_POLICY_ADMIN_UIDS`
- `PCOSINA_OPS_ADMIN_EMAILS`, `PCOSINA_OPS_ADMIN_UIDS`
- `PCOSINA_FEEDBACK_ADMIN_EMAILS`, `PCOSINA_FEEDBACK_ADMIN_UIDS`
- `PCOSINA_CONTENT_ADMIN_EMAILS`, `PCOSINA_CONTENT_ADMIN_UIDS`
- `PCOSINA_ASYNC_MODE`
- `PCOSINA_QUEUE_BACKEND`
- `PCOSINA_REDIS_URL`
- `PCOSINA_ENFORCE_APP_CHECK`
- `PCOSINA_APP_CHECK_HEADER`
- `PCOSINA_POLICY_CACHE_TTL_SECONDS`
- `MAX_REQUEST_BYTES`
- `PCOSINA_RATE_LIMIT_BACKEND`
- `PCOSINA_RATE_LIMIT_REDIS_PREFIX`
- `PCOSINA_RATE_LIMIT_WINDOW_SECONDS`
- `PCOSINA_RATE_LIMIT_MAX`
- `PCOSINA_PRICE_RULE_CACHE_TTL_SECONDS`
- `PCOSINA_DB_NAME` (SQLite dev/test override only; ignored when `DATABASE_URL` is Postgres)
- `PCOSINA_WEBHOOK_RECEIVER_KEY`
- `SENTRY_DSN`, `SENTRY_TRACES_SAMPLE_RATE`, `SENTRY_ENVIRONMENT`, `SENTRY_RELEASE`
- `PCOSINA_ML_MODEL_PATH`, `PCOSINA_ML_METRICS_PATH`
- `PCOSINA_UID_HASH_SALT`

Operational webhook canary calls should send `PCOSINA_WEBHOOK_RECEIVER_KEY` through the `X-PCOSINA-Webhook-Key` header. The legacy path-key route is retained for compatibility, but header-based delivery avoids placing receiver secrets in access logs.

To resolve the current rollout candidate into shell-ready env exports, use:
- `python scripts/prepare_ml_rollout_env.py --phase shadow`
- `python scripts/prepare_ml_rollout_env.py --phase canary --canary-percent 5`

### Administrative Access Model

- Legacy shared admin tokens are no longer accepted for privileged endpoints.
- Operator access now requires either:
  - a Firebase ID token that resolves to an allowlisted operator role, or
  - an existing signed admin session created from a valid operator token.
- Email-derived operator roles require `email_verified=true` when `PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL` is enabled.
- Privileged operator access can require Firebase MFA evidence via `PCOSINA_REQUIRE_OPERATOR_MFA`; accepted signals include Firebase second-factor claims, supported `amr` values, or an equivalent explicit operator MFA claim.
- Admin session minting requires a recent Firebase sign-in when `PCOSINA_REQUIRE_RECENT_ADMIN_AUTH` is enabled.
- Admin browser sessions are revoked automatically after `PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS` of inactivity when that control is enabled.
- Admin session issuance also enforces `PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID`; when the cap is exceeded, older active sessions for the same operator UID are revoked first.
- Server-side operator access overrides can block a specific operator UID/email immediately and can revoke currently active admin sessions as part of the same ops action.
- Operator roles are separated across:
  - `policy_admin`
  - `ops_admin`
  - `feedback_admin`
  - `content_admin`

### Production Readiness Expectations

- Production startup now fails closed when required runtime controls are missing.
- `GET /health/ready` is the readiness endpoint and should be used by the platform health check.
- `scripts/check_schema_migrations.py` is the machine-checkable schema gate used by CI and rollout-readiness evidence collection.
- Mobile-facing endpoints now require a valid Firebase App Check token in the `X-Firebase-AppCheck` header by default in production.
- In production:
  - `PCOSINA_ENFORCE_APP_CHECK` must remain enabled
  - `PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL` must remain enabled
  - `PCOSINA_REQUIRE_OPERATOR_MFA` must remain enabled
  - `PCOSINA_REQUIRE_RECENT_ADMIN_AUTH` must remain enabled
  - `PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS` must remain enabled
  - `PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID` must remain enabled
  - `DATABASE_URL` must be a Postgres connection string
  - `PCOSINA_ADMIN_SESSION_SECRET` must be set
  - `PCOSINA_UID_HASH_SALT` must be set to the same non-default value on the web service and worker
  - Firebase credentials must be configured
  - `PCOSINA_QUEUE_BACKEND=memory` is not allowed for queued mode
  - `PCOSINA_RATE_LIMIT_BACKEND=memory` is not allowed
  - `PCOSINA_REDIS_URL` is required when queueing or shared rate limiting uses Redis

### Respondent Testing Exception

For Firebase App Distribution respondent testing, use `PCOSINA_ENV=staging` and `PCOSINA_ENFORCE_APP_CHECK=false`. In that mode, protected mobile endpoints still require Firebase Auth, but backend App Check verification is skipped so testers do not need manual debug token registration. Return to `PCOSINA_ENV=production` and `PCOSINA_ENFORCE_APP_CHECK=true` for production or Play Store-style testing.

## Notes

- Active policy is resolved per environment before runtime use.
- Unsafe policy overrides are rejected (`hard_rule_mode=strict`, `allow_unsafe_overrides=false`).
- Legacy flat policy payloads are migrated into schema `2.0.0` at load time.
- Full migration inventory (old location -> new key -> default -> bounds -> permission): `docs/roadmap/config_migration_report.csv` and `docs/roadmap/config_migration_report.md`.
