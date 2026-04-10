# Go-Live Gates (Machine-Checkable Targets)

## Safety
- `hard_violation_rate == 0.0` on authoritative planner suite
- `unsafe_fallback_return_count == 0`

## Latency SLO (authoritative path)
- `latency_p50_ms <= 2500`
- `latency_p95_ms <= 7000`
- `latency_p99_ms <= 12000`

## Continuity
- `offline_continuity_suite == pass`
- `reinstall_recovery_suite == pass`
- `backup_restore_suite == pass`

## Reliability
- `api_error_rate <= 1.0%`
- `planner_timeout_rate <= 3.0%`
- `crash_free_sessions >= 99.5%`
- `queue_dead_letter_rate <= 1.0%`

## Security/Compliance
- `admin_audit_logging_test == pass`
- `policy_validation_rejects_unsafe == pass`
- `secrets_rotation_check == pass`
- `operator_auth_policy_check == pass` (via `scripts/check_operator_auth_policy.py`)

## ML/Canary Operational Gates
- `reason_tag_distribution_gate == pass` (via `scripts/check_reason_tag_distribution.py`)
- `canary_guard_status == ok` (via `scripts/monitor_canary_guardrails.py`)
- `canary_dashboard_panel_generation == pass` (via `scripts/build_canary_dashboard_panel.py`)
- `canary_webhook_secret_check == pass` (via `scripts/check_canary_webhook_secrets.py`)
- `canary_drill_receipt_status == ok` (via `scripts/run_canary_drill.py`)
- `canary_drill_receipt_contract == pass` (via `scripts/check_canary_drill_receipt.py`)

## Queue/Planner Diagnostics Gates
- `/ops/plan-jobs/diagnostics` returns `status == ok`
- `/health/ready.schemaMigrations.ok == true`
- `schema_migration_gate.status == ok` (via `scripts/check_schema_migrations.py`)
- `/ops/plan-jobs/diagnostics.queueBroker.enabled` is true for broker-scale tests
- `queue_worker_claimed_total` increases under async load
- `queue_worker_retry_queued_total` and `queue_worker_dead_letter_total` are non-negative and observable
- `sync_requests_total` and `async_requests_total` counters are emitted for planner traffic
- `queue_broker_throughput_probe.success == true` (via `load-tests/queue_broker_throughput.py`)

## Release Gates
Internal Alpha -> Closed Beta -> Staged Production rollout progression only if all mandatory gates pass for current stage.
- Live staged/production releases should archive externally generated canary-drill receipts and external Redis throughput reports, then validate them through `scripts/run_rollout_readiness_batch.py` using:
  - `--external-canary-receipt`
  - `--external-queue-throughput-report`
  - `--require-external-canary-receipt`
  - `--require-external-queue-throughput-report`

## Batch Gate Runner
- `rollout_readiness_batch.status == ok` (via `scripts/run_rollout_readiness_batch.py`)
- `release_evidence_bundle.status == ok` and required artifact hashes archived (via `scripts/run_rollout_readiness_batch.py`)
