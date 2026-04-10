# PCOSINA Risk Register

## Status

PCOSINA is materially hardened for a serious pre-production system, but it is not yet honestly production-ready.
The remaining highest-severity risks are now concentrated in external environment evidence rather than unaddressed core code defects.

## Active Risks

| ID | Risk | Severity | Current status | Evidence | Blocker type |
|---|---|---|---|---|---|
| R1 | Live canary alert routing and archived drill evidence are still missing | High | Open | `docs/roadmap/progress_ledger.md`, `docs/sre/canary_webhook_routing.md` | External environment |
| R2 | Android connected verification still requires a network-enabled environment with Maven access | High | Open | `app/src/androidTest/java/com/pcosina/app/MealPlanNoSafePlanUiTest.kt`, `docs/roadmap/progress_ledger.md` | External environment |
| R3 | Staged multi-device sync validation evidence is still missing | High | Open | `docs/data-models/sync_conflict_policy.md`, `docs/roadmap/progress_ledger.md` | External environment |
| R4 | Production Redis/load evidence is still missing | High | Open | `load-tests/queue_broker_throughput.py`, `docs/roadmap/progress_ledger.md` | External environment |
| R5 | Live reason-feedback volume is still lower than ideal for broader ML calibration | Medium | Open | `docs/roadmap/ml_progress_ledger.md` | Mixed repo + traffic |
| R6 | Top-level documentation was previously incomplete and can drift again without discipline | Medium | Mitigated by current root docs | this file set plus `docs/roadmap/*` | Repo process |

## Closed or Substantially Mitigated Repo Risks

- operator auth and session hardening
- async job ownership enforcement
- Android release manifest hardening
- production runtime fail-closed checks
- file-backed ML fallback and readiness gating
- ML dataset and reason-feedback freshness enforcement in CI

## Definition-of-Done Reality

The repo is approaching production readiness for its intended scope, but external evidence is still required before an honest production-ready claim:
- live environment webhook delivery
- connected Android/device evidence
- staged sync evidence
- production broker/load evidence
