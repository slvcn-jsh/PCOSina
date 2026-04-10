# ADR-003: Versioned Policy Config with Rollback and Audit

- Status: Accepted
- Date: 2026-03-10

## Context
Frequent planner policy tuning should not require code rewrites and must remain safe.

## Decision
Implement typed, bounded, versioned policy config with:
- schema validation (`backend/policy_config.py`)
- persisted versions + activation + rollback (`backend/policy_store.py`)
- admin-only mutation endpoints with audit logs

Hard safety controls (`hard_rule_mode=strict`, `allow_unsafe_overrides=false`) are non-negotiable.

## Consequences
- Positive: policy agility with auditability.
- Negative: new admin surface requires security hardening.
- Mitigation: token-protected endpoints + audit logging + test coverage.
