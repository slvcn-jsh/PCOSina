# PCOSINA Privacy and Security

## Scope

PCOSINA handles health-adjacent preference, pantry, plan, and behavioral feedback data.
The product is not a medical device, but it still requires privacy-conscious handling.

## Local-First Privacy Posture

- local device state remains the primary source of truth
- cloud sync is supplementary
- the app must remain useful even when cloud sync is unavailable

## Main Data Surfaces

### Android app

- user profile and preference artifacts in DataStore-backed repositories
- local plan, pantry, grocery, and log artifacts managed by repositories
- encrypted reflection content in `ReflectionStore.kt`

### Backend

- planner requests and responses
- operator/admin surfaces
- ML telemetry events
- support and diagnostic records

## Existing Hardening in Repo

- production backend fails closed without Postgres and required runtime controls
- operator authentication replaces legacy shared admin-token behavior
- operator MFA and verified-email controls exist for privileged access
- App Check is enforced by default in production
- Android manifest disables global backup and cleartext traffic in release
- network security config is defined in release manifest
- Render blueprint declares managed Postgres, Redis, web, and worker services

## Logging Rules

- logs must be useful for debugging but avoid raw sensitive payload dumps
- common Android flow logs now use `app/src/main/java/com/pcosina/app/util/LogPrivacy.kt` to replace raw user IDs with bounded SHA-256-derived scopes
- ML/debug telemetry should prefer hashed or bounded identifiers
- new logs should avoid free-text reason content unless explicitly sanitized

## Privacy Expectations

- keep only the minimum local data needed for core offline flows
- treat optional cloud sync as convenience, not dependency
- review ML and telemetry payloads for PII minimization before production rollout
- document any new remotely stored field before release

## Current Security Priorities

- keep privileged operator flows session-based and auditable
- preserve async job ownership checks
- maintain App Check enforcement in release and production
- continue validating local storage minimization and encryption coverage

## References

- `docs/backend-config.md`
- `docs/threat-model/production-threat-model.md`
- `docs/release/production_readiness_checklist.md`
