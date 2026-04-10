# Optimizer Queue/Worker Scaffold

## Current Modes
- `PCOSINA_ASYNC_MODE=queued` (default): `/generate-plan-async` stores queued jobs and returns immediately.
- `PCOSINA_ASYNC_MODE=inprocess|background`: legacy background-task execution.

Optional broker-backed signaling for scale testing:

- `PCOSINA_QUEUE_BACKEND=db` (default): DB-only queue claim.
- `PCOSINA_QUEUE_BACKEND=memory`: in-memory broker for local/dev validation.
- `PCOSINA_QUEUE_BACKEND=redis`: Redis signal queue (requires `PCOSINA_REDIS_URL`).

## Queue Storage
`plan_jobs` now stores:
- `request_json`
- `status`
- `attempt_count`
- `next_attempt_at`
- `worker_id`
- `idempotency_key`
- `result_json` / `error`

`plan_job_diagnostics` stores aggregated operational counters such as:
- `queue_worker_claimed_total`
- `queue_worker_retry_queued_total`
- `queue_worker_dead_letter_total`
- `sync_requests_total`
- `async_requests_total`

## Worker
Run:

```powershell
python backend/worker_plan_jobs.py
```

Worker loop:
1. Claim oldest queued job (`claim_next_plan_job`)
2. Execute authoritative CP-SAT planning
3. On failure, re-queue with `next_attempt_at` backoff until dead-letter threshold
4. On terminal failure, mark `status=dead-letter`
5. Write `status=done` result (`success` or `no-safe-plan`) for completed jobs
6. Persist runtime metadata and diagnostics counters

## Diagnostics Endpoint
- `GET /ops/plan-jobs/diagnostics` (admin token required)
- Returns queue status counts + diagnostics counters + broker health for SRE dashboards/alerts

This is the first durability slice; dedicated external queue and autoscaled worker pool are next-phase tasks.
