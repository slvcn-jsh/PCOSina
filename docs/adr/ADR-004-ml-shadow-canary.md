# ADR-004: ML in Shadow Mode then Canary for Stage 1 Ranking Only

- Status: Accepted
- Date: 2026-03-10

## Context
ML can improve ranking personalization but must never compromise safety.

## Decision
- ML scores are allowed only in Stage 1 candidate ranking.
- CP-SAT Stage 2 remains final authority for released plans.
- Rollout path: offline evaluation -> shadow mode -> canary -> guarded expansion.
- Instant rollback if safety/quality gates regress.

## Consequences
- Positive: measurable ML uplift without safety authority transfer.
- Negative: additional telemetry and model governance overhead.
- Mitigation: event taxonomy + feature definitions + canary gate metrics.
