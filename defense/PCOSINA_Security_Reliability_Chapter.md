# Security and Reliability Chapter

## Narrative

### 1) Threat Model (Assets, Threats, Attack Surface)
**Assets**
- User profile data (health-related preferences, pantry, goals)
- Firebase ID tokens (auth integrity)
- Meal plan generation service (availability)
- Recipe database (integrity)
- Feedback messages (trust and privacy)

**Attack Surface**
- API endpoints: `/generate-plan`, `/recipe/{id}`, `/recipes/summary`, `/feedback`, `/admin/feedback`, `/docs`, `/openapi.json`, `/health`
- Auth boundary: Firebase token verification on protected routes
- Admin access: token-gated feedback UI
- Client storage: DataStore + encrypted preferences

**Primary Threats**
- Auth bypass if `FIREBASE_AUTH_DISABLED=true` in production
- Information disclosure from public docs
- Token leakage via query-string admin token
- DoS on `/generate-plan`
- Host header abuse due to permissive trusted hosts

### 2) Existing Controls
**Auth and access control**
- Firebase ID token verification on protected routes
- Optional auth bypass for local dev only

**Schema compatibility**
- `X-PCOSINA-Schema-Version` required; mismatch returns 409

**Request limits**
- Request size limit via `MAX_REQUEST_BYTES`

**Admin controls**
- Token required for `/admin/feedback`

**Monitoring**
- Backend: Sentry (optional via `SENTRY_DSN`)
- App: Crashlytics and optional Sentry

### 3) Remaining Risks and Practical Mitigations
- Docs exposure: disable or protect `/docs` and `/openapi.json` in production
- Admin token in query: require header-only token
- Auth bypass risk: fail startup if auth disabled in prod
- Solver DoS: add rate limits, tighten timeouts, monitor latency
- Host header abuse: restrict `allowed_hosts`

### 4) Incident Response Flow and Monitoring Strategy
**Incident response flow**
1. Detect via error logs and monitoring
2. Triage affected endpoint and error pattern
3. Contain via rate limits, token rotation, or temporary disable
4. Patch and redeploy
5. Verify recovery via `/health`
6. Postmortem and preventive changes

**Monitoring strategy**
- Backend error tracking with Sentry
- App crash tracking with Crashlytics
- Health checks on `/health`
- Track solver latency and error rates

## Checklist Version

### Threat Model
- [ ] Assets identified: profile, tokens, solver availability, recipes, feedback
- [ ] Attack surface mapped: API endpoints, admin UI, client storage
- [ ] Key threats recorded: auth bypass, docs exposure, token leakage, DoS, host header abuse

### Existing Controls
- [ ] Firebase auth on protected endpoints
- [ ] Schema version enforcement
- [ ] Request size limit
- [ ] Admin feedback token required
- [ ] Monitoring hooks present

### Remaining Risks
- [ ] Disable or protect `/docs` and `/openapi.json`
- [ ] Admin token header-only
- [ ] Enforce auth in production
- [ ] Add rate limiting for `/generate-plan`
- [ ] Restrict allowed hosts

### Incident Response
- [ ] Detect via monitoring and logs
- [ ] Triage by endpoint and error signature
- [ ] Contain via rate limits or token rotation
- [ ] Patch and redeploy
- [ ] Verify `/health` after fix
- [ ] Postmortem documented

### Monitoring
- [ ] Backend Sentry configured
- [ ] App Crashlytics configured
- [ ] Health endpoint checks
- [ ] Solver latency tracking
