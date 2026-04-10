# Canary Webhook Routing (Staging and Production)

## Required Environment Secrets

Set environment-specific webhook URLs in CI/runtime:

- `PCOSINA_ALERT_WEBHOOK_URL_STAGING`
- `PCOSINA_DASHBOARD_WEBHOOK_URL_STAGING`
- `PCOSINA_ALERT_WEBHOOK_URL_PRODUCTION`
- `PCOSINA_DASHBOARD_WEBHOOK_URL_PRODUCTION`

Native receiver security key:

- `PCOSINA_WEBHOOK_RECEIVER_KEY`

Optional local override keys (single-environment run):

- `PCOSINA_ALERT_WEBHOOK_URL`
- `PCOSINA_DASHBOARD_WEBHOOK_URL`

## Native PCOSINA Webhook Endpoints (Recommended)

If you want first-party production endpoints (instead of `webhook.site`), point webhooks to your deployed backend:

- `POST /ops/webhooks/canary/alert/{receiver_key}`
- `POST /ops/webhooks/canary/dashboard/{receiver_key}`

`receiver_key` must match `PCOSINA_WEBHOOK_RECEIVER_KEY` in backend runtime config.

Example production wiring (PowerShell):

```powershell
$env:PCOSINA_WEBHOOK_RECEIVER_KEY = "<strong-random-key>"
$base = "https://<your-render-backend-domain>"
$env:PCOSINA_ALERT_WEBHOOK_URL_PRODUCTION = "$base/ops/webhooks/canary/alert/$env:PCOSINA_WEBHOOK_RECEIVER_KEY"
$env:PCOSINA_DASHBOARD_WEBHOOK_URL_PRODUCTION = "$base/ops/webhooks/canary/dashboard/$env:PCOSINA_WEBHOOK_RECEIVER_KEY"
```

Optional admin inspection endpoint (requires an authenticated operator with the `ops_admin` role or full `admin` role):

- `GET /ops/webhooks/canary/recent?limit=20`

## Full Canary Drill Command

Validate secret presence before drill:

```powershell
python scripts/check_canary_webhook_secrets.py --environment staging
python scripts/check_canary_webhook_secrets.py --environment production
```

For local loopback drills only:

```powershell
python scripts/check_canary_webhook_secrets.py --environment staging --allow-http-localhost
```

Staging:

```powershell
python scripts/run_canary_drill.py `
  --environment staging `
  --metrics benchmarks/reports/go_live_metrics.json `
  --require-webhooks `
  --alert-on-ok
```

Production:

```powershell
python scripts/run_canary_drill.py `
  --environment production `
  --metrics benchmarks/reports/go_live_metrics.json `
  --require-webhooks `
  --alert-on-ok
```

Breach simulation with rollback drill:

```powershell
python scripts/run_canary_drill.py `
  --environment staging `
  --metrics benchmarks/reports/go_live_metrics.example.json `
  --simulate-breach `
  --expect-breach `
  --require-webhooks `
  --alert-on-ok `
  --auto-rollback
```

## Receipt Evidence

Every drill writes:

- `benchmarks/reports/canary_drill_receipt.<environment>.<timestamp>.json`

Receipt includes:

- command-level exit codes/stdout/stderr
- guard status + breach count
- alert delivery status/error
- dashboard panel generation status
- generated artifact paths

Validate receipt contract (machine-checkable):

```powershell
python scripts/check_canary_drill_receipt.py `
  --receipt benchmarks/reports/canary_drill_receipt.staging.json `
  --environment staging `
  --require-webhook-delivery `
  --output benchmarks/reports/canary_drill_receipt_check.staging.json
```

## Broker Throughput Probe (Scale Testing)

```powershell
python load-tests/queue_broker_throughput.py --backend memory --jobs 1000 --workers 4
```

Redis probe (when `PCOSINA_REDIS_URL` is configured):

```powershell
python load-tests/queue_broker_throughput.py --backend redis --jobs 5000 --workers 8
```

## Unified Rollout Batch Runner

Run all staging checks (webhooks, canary drill + receipt check, queue probes, sync replay E2E) and generate one report:

```powershell
python scripts/run_rollout_readiness_batch.py `
  --environment staging `
  --metrics benchmarks/reports/go_live_metrics.json `
  --require-live-webhooks `
  --require-redis
```

If Redis probe needs a lighter first pass, tune probe size:

```powershell
python scripts/run_rollout_readiness_batch.py `
  --environment staging `
  --metrics benchmarks/reports/go_live_metrics.json `
  --require-live-webhooks `
  --require-redis `
  --redis-jobs 1000 `
  --redis-workers 4 `
  --redis-consume-timeout-seconds 180
```

Local dry-run (loopback webhooks, no Redis requirement):

```powershell
python scripts/run_rollout_readiness_batch.py `
  --environment staging `
  --metrics benchmarks/reports/go_live_metrics.example.json `
  --allow-http-localhost
```

Output report:

- `benchmarks/reports/rollout_readiness_batch.<environment>.<timestamp>.json`
