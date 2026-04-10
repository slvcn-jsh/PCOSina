# Release Checklist

## Pre-Release Validation
- [ ] Planner authoritative hard-violation gate pass
- [ ] Latency SLO artifacts generated (p50/p95/p99)
- [ ] Offline continuity and reinstall recovery tests pass
- [ ] Policy config validation and rollback tests pass
- [ ] Benchmark reproducibility artifacts generated
- [ ] Security review and admin audit-log checks pass
- [ ] Schema migration gate passes (`scripts/check_schema_migrations.py`)
- [ ] Operator auth policy gate passes (`scripts/check_operator_auth_policy.py`)
- [ ] `/health/ready` returns `200` with `schemaMigrations.ok == true`
- [ ] Live staged/production drill receipts are archived and validated through `scripts/run_rollout_readiness_batch.py` when applicable

## Rollout Steps
1. Internal alpha build and smoke tests
2. Closed beta with monitored cohort
3. Canary/staged rollout with rollback readiness
4. Full rollout only after gate pass and incident review

## Rollback Readiness
- [ ] Policy rollback endpoint tested
- [ ] Service rollback runbook validated
- [ ] Incident communication template prepared
- [ ] Canary guard script validated (`scripts/monitor_canary_guardrails.py`)
- [ ] Canary breach simulation drill completed (`scripts/simulate_canary_breach_metrics.py`)
- [ ] Full canary drill executed (`scripts/run_canary_drill.py`)
- [ ] Alert webhook route tested for staging + production
- [ ] Dashboard webhook route tested for staging + production
- [ ] Drill receipt evidence archived (`benchmarks/reports/canary_drill_receipt.*.json`)
- [ ] Drill receipt contract check archived (`benchmarks/reports/canary_drill_receipt_check.*.json`)
- [ ] Queue broker throughput probe archived (`benchmarks/reports/queue_broker_throughput*.json`)
- [ ] Schema migration gate report archived (`benchmarks/reports/schema_migration_gate.*.json`)
- [ ] Unified rollout batch report archived (`benchmarks/reports/rollout_readiness_batch.*.json`)
- [ ] Release evidence bundle archived (`benchmarks/reports/release_evidence_bundle.*.json`)
