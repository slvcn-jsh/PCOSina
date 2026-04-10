# ADR-005: No-Safe-Plan Contract (No Unsafe Fallback Output)

- Status: Accepted
- Date: 2026-03-10

## Context
Returning approximate unsafe plans as valid results is unacceptable for safety-critical diet constraints.

## Decision
When authoritative solve is infeasible, API returns structured `status=no-safe-plan` response with:
- machine-readable reason codes
- human guidance
- safe relaxation suggestions
- policy version and diagnostics references

No heuristic unsafe approximation is returned as authoritative plan.

## Consequences
- Positive: explicit safety posture and better user trust.
- Negative: more users may receive infeasible outcomes until preference tuning improves.
- Mitigation: actionable guidance + policy tuning + candidate-dataset expansion.
