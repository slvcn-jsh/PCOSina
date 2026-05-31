# ADR-002: Offline-First Sync Contract

- Status: Accepted
- Date: 2026-03-10

## Context
Core workflows must remain usable without network while preserving account recovery and continuity.

## Decision
- Local-first data as default source of truth for day-to-day UX.
- Cloud sync is supportive and best-effort for backup/reinstall/multi-device continuity.
- Planner generation may require network, but latest saved artifacts remain available offline.
- Product restore promises are bounded by the [offline, sync, and restore matrix](../data-models/offline_restore_matrix.md).

## Consequences
- Positive: continuity under intermittent connectivity.
- Negative: eventual consistency complexity.
- Mitigation: idempotent sync jobs, conflict policy, recovery tests.
