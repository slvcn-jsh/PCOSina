# PCOSINA Backend Configuration

This document lists the runtime environment variables and API contract used by the backend.

## API Contract

- **Schema Version:** `1.0.0`
- **Schema Endpoint:** `GET /schema`
- **Response Header:** `X-PCOSINA-Schema-Version: 1.0.0`

The mobile client sends `X-PCOSINA-Schema-Version` on API calls. If the header is present and does not match the server, the server returns **409** for `POST /generate-plan` and `POST /feedback`.

## Core Endpoints

- `GET /health` — service liveness
- `POST /generate-plan` — generate a meal plan
- `GET /recipe/{id}` — get recipe details
- `POST /feedback` — save feedback
- `GET /admin/feedback` — feedback admin (requires token)

## Environment Variables

### API & Security

- `ADMIN_FEEDBACK_TOKEN`  
  Required to access `/admin/feedback` and delete entries.  
  The token can be sent via `X-Admin-Token` header or `?token=...` query.

- `FIREBASE_AUTH_DISABLED`  
  If `true`, bypass Firebase auth on protected routes.

- `FIREBASE_SERVICE_ACCOUNT_JSON`  
  Raw JSON service account content (string).  
  Overrides `FIREBASE_CREDENTIALS_PATH` when set.

- `FIREBASE_CREDENTIALS_PATH`  
  Path to service account JSON (default: `backend/secrets/firebase-service-account.json`).

### Sentry

- `SENTRY_DSN`  
  Enables Sentry when set.

- `SENTRY_TRACES_SAMPLE_RATE`  
  Float string, default `0.1`.

- `SENTRY_ENVIRONMENT`  
  Environment label (ex: `production`, `dev`).

- `SENTRY_RELEASE`  
  Release identifier for tagging events.

### Request Limits

- `MAX_REQUEST_BYTES`  
  Request body size cap. Default: `524288` (512 KB).

### Solver & Optimization

- `PCOSINA_SOLVER_TIME_SECONDS`  
  Max solve time. Default: `3.0`.

- `PCOSINA_SOLVER_WORKERS`  
  Number of solver threads. Default: `8`.

- `PCOSINA_TOLERANCE_LEVELS`  
  Comma-separated float list. Default: `0.2,0.3,0.4`.

- `PCOSINA_MAX_PER_WEEK`  
  Comma-separated int list for max repeats. Default: `2,3,4,10`.

- `PCOSINA_MILP_WEIGHTS`  
  JSON string of weights for objective terms.  
  Keys: `repeat_weight`, `group_weight`, `diversity_weight`, `pantry_weight`.

- `PCOSINA_SEED_SALT`  
  Optional salt to make random seed deterministic per profile.

### Shortlist Pruning

- `PCOSINA_SHORTLIST_LIMIT`  
  Default: `80`.

- `PCOSINA_SHORTLIST_LIMIT_RESTRICTED`  
  Default: `120`.

- `PCOSINA_SHORTLIST_KEEP_MIN`  
  Default: `20`.

- `PCOSINA_SHORTLIST_KEEP_RATIO`  
  Default: `0.8`.

## Notes

- The backend attaches `X-PCOSINA-Schema-Version` to all responses.
- The schema contract lives at `backend/schema/pcosina_contract.json`.
