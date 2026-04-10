# Runbook: Planner Incident Response

## Trigger Conditions
- Hard-violation gate regression
- Sustained p95 latency breach
- Elevated no-safe-plan rate spike
- Queue backlog growth beyond threshold

## Immediate Actions
1. Freeze policy mutations.
2. Activate last-known-good policy version (`/admin/policy/rollback`).
3. Switch async mode to safe fallback (`PCOSINA_ASYNC_MODE=inprocess`) if workers are unhealthy.
4. Increase observability sampling and capture request IDs.
5. Trigger canary guard evaluation and record output artifact:
```powershell
python scripts/monitor_canary_guardrails.py `
  --metrics benchmarks/reports/go_live_metrics.json `
  --output benchmarks/reports/canary_guard_report.json `
  --auto-rollback `
  --actor incident_response
```
6. Build/publish dashboard payload for on-call visibility:
```powershell
python scripts/build_canary_dashboard_panel.py `
  --guard-report benchmarks/reports/canary_guard_report.json `
  --metrics benchmarks/reports/go_live_metrics.json `
  --output benchmarks/reports/ops_dashboard_canary_panel.json `
  --webhook-env PCOSINA_DASHBOARD_WEBHOOK_URL
```
7. Validate reason-tag data-quality gate for ML feedback stability:
```powershell
python scripts/check_reason_tag_distribution.py `
  --summary ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json `
  --output benchmarks/reports/reason_tag_distribution_gate_report.json
```
8. For full staged/prod verification, run unified drill and archive receipt:
```powershell
python scripts/run_canary_drill.py `
  --environment staging `
  --metrics benchmarks/reports/go_live_metrics.json `
  --require-webhooks `
  --alert-on-ok `
  --output benchmarks/reports/canary_drill_receipt.staging.json
python scripts/check_canary_drill_receipt.py `
  --receipt benchmarks/reports/canary_drill_receipt.staging.json `
  --environment staging `
  --require-webhook-delivery `
  --output benchmarks/reports/canary_drill_receipt_check.staging.json
```
9. Run unified rollout readiness batch to capture one auditable report:
```powershell
python scripts/run_rollout_readiness_batch.py `
  --environment staging `
  --metrics benchmarks/reports/go_live_metrics.json `
  --require-live-webhooks `
  --require-redis
```

## Triage Data
- `PLANNER_EVENT` logs for request IDs and reason codes
- `/plan-jobs/{job_id}` for queued/running/error states
- `/ops/plan-jobs/diagnostics` for retry/dead-letter counters and queue status
- Policy audit logs (`/admin/policy/audit`)
- Canary guard report (`benchmarks/reports/canary_guard_report.json`)
- Dashboard payload (`benchmarks/reports/ops_dashboard_canary_panel.json`)
- Reason-tag gate report (`benchmarks/reports/reason_tag_distribution_gate_report.json`)
- Canary drill receipt (`benchmarks/reports/canary_drill_receipt.*.json`)
- Canary drill receipt validation (`benchmarks/reports/canary_drill_receipt_check.*.json`)
- Rollout batch report (`benchmarks/reports/rollout_readiness_batch.*.json`)

## Recovery
- Verify hard-violation suite returns to zero
- Confirm latency gates
- Resume normal async queue mode and monitor

## Rollback Drill (Staging)
1. Generate synthetic breach metrics:
```powershell
python scripts/simulate_canary_breach_metrics.py `
  --input benchmarks/reports/go_live_metrics.example.json `
  --output benchmarks/reports/go_live_metrics.breach.json `
  --scenario all
```
2. Run guard with rollback enabled:
```powershell
python scripts/monitor_canary_guardrails.py `
  --metrics benchmarks/reports/go_live_metrics.breach.json `
  --output benchmarks/reports/canary_guard_report.breach.json `
  --auto-rollback `
  --actor rollback_drill
```
3. Confirm audit evidence from `/admin/policy/audit` and report artifact.

## Post-Incident
- Root-cause analysis
- Add regression tests
- Update gate thresholds/runbook if needed
