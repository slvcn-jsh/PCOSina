# Render Testing Environment

This file documents the Render settings for respondent testing. Do not commit Firebase service account JSON, `.env` files, keystores, debug tokens, or local credentials.

## Required Testing Values

Use these for the respondent testing Render service:

```text
PCOSINA_ENV=staging
PCOSINA_ENFORCE_APP_CHECK=false
PCOSINA_ALLOWED_HOSTS=pcosina-backend.onrender.com
PCOSINA_ENABLE_DOCS=false
PCOSINA_ASYNC_MODE=queued
PCOSINA_QUEUE_BACKEND=redis
PCOSINA_RATE_LIMIT_BACKEND=redis
```

Important: do not set `FIREBASE_AUTH_DISABLED=true`. Firebase Auth must remain enabled for respondent testing.

## Required Secret or Platform Values

Render should provide or store these outside git:

```text
DATABASE_URL=<Render Postgres connection string>
PCOSINA_REDIS_URL=<Render Redis connection string>
FIREBASE_SERVICE_ACCOUNT_JSON=<Firebase Admin SDK JSON stored as a secret>
PCOSINA_ADMIN_SESSION_SECRET=<generated secret>
PCOSINA_WEBHOOK_RECEIVER_KEY=<generated secret, if webhooks are used>
SENTRY_DSN=<optional backend crash/error reporting DSN>
```

Keep admin/operator allowlists configured for the team:

```text
PCOSINA_ADMIN_EMAILS=<team admin emails>
PCOSINA_POLICY_ADMIN_EMAILS=<policy admin emails>
PCOSINA_OPS_ADMIN_EMAILS=<ops admin emails>
PCOSINA_FEEDBACK_ADMIN_EMAILS=<feedback admin emails>
PCOSINA_CONTENT_ADMIN_EMAILS=<content admin emails>
```

## Why `PCOSINA_ENV=staging`

Production mode intentionally fails closed if App Check is disabled. For respondent testing, use `PCOSINA_ENV=staging` with `PCOSINA_ENFORCE_APP_CHECK=false` so the backend can start while still requiring Firebase Auth on protected routes.

For production or Play Store-style testing:

```text
PCOSINA_ENV=production
PCOSINA_ENFORCE_APP_CHECK=true
```

## Expected App Check Behavior

Testing:
- Missing `X-Firebase-AppCheck` does not fail protected mobile requests.
- Invalid `X-Firebase-AppCheck` is ignored.
- Firebase ID token verification is still required.

Production:
- Missing or invalid `X-Firebase-AppCheck` fails protected mobile requests.
- Firebase ID token verification is still required.

## Health Checks

After Render deploys the latest `dev` commit, check:

```text
https://pcosina-backend.onrender.com/health
https://pcosina-backend.onrender.com/health/ready
```

`/health/ready` should report `appCheckEnforced=false` during respondent testing and should not report production readiness errors.
