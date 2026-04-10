# ADR-001: Remote CP-SAT as Authoritative Planner

- Status: Accepted
- Date: 2026-03-10

## Context
PCOSINA needs strict hard-constraint compliance (allergy/restrictions/slot validity) with diagnosable infeasibility.

## Decision
Use a two-stage planning contract:
1. Stage 1 filtering/ranking for tractability.
2. Stage 2 formal 0-1 model solved by OR-Tools CP-SAT as final authority.

## Consequences
- Positive: hard constraints remain enforceable and auditable.
- Negative: requires backend solver capacity and robust timeout handling.
- Mitigation: no-safe-plan response contract and policy-tunable runtime limits.
