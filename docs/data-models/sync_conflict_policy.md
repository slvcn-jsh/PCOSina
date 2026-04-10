# Sync Conflict Policy (UID-Scoped)

## Scope
Applies to account-backed recovery and multi-device continuity for offline-first artifacts.

## Entity Classes
- Profile snapshot
- Pantry entries
- Grocery checklist state
- Saved plan snapshots
- Progress/log entries

## Conflict Rules
1. **UID boundary first**: never merge across different UID scopes.
2. **Plan snapshots are immutable**: new generation creates new `plan_id`; no destructive overwrite.
3. **Checklist/log updates are last-write-wins with timestamp guard**.
4. **Profile fields are field-level merge with `updated_at_ms` precedence**.
5. **Pantry quantity conflicts**: latest event wins, but keep previous value in audit stream for traceability.
6. **Name-only pantry/grocery items**: when item IDs are missing, fallback merge keys normalize case plus separator differences so `Brown Rice`, `brown-rice`, and `brown/rice` resolve to one logical item.

## Idempotency
- Sync operations must include deterministic operation IDs.
- Duplicate operation IDs are ignored safely.

## Retry and Dead-Letter
- Retries use exponential backoff.
- After max retry attempts, operation moves to dead-letter queue for operator review.

## Recovery
After reinstall/login:
1. Pull active UID snapshot
2. Rehydrate local persistence
3. Emit recovery-complete telemetry event

## Current Implementation Evidence
- Conflict/recovery merge helper: `backend/services/sync_recovery.py`
- Retry/dead-letter durability for async planner jobs: `backend/worker_plan_jobs.py`, `backend/database.py`
- Recovery + conflict assertions: `backend/tests/test_sync_recovery_e2e.py`
