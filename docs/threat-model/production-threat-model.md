# PCOSINA Production Threat Model (v1)

## Assets
- User profile and dietary constraints
- Pantry/grocery continuity data
- Planner policy versions and audit logs
- Authoritative plan generation outputs

## Trust Boundaries
1. Mobile client <-> backend API
2. Backend <-> Firebase auth and cloud sync services
3. Admin policy endpoints <-> privileged operators
4. Worker process <-> planner queue and policy store

## Primary Threats
- Unauthorized admin policy mutation
- Unsafe policy values attempting to weaken hard constraints
- Token replay/idempotency abuse on plan APIs
- Queue poisoning via malformed async payloads
- Data leakage in logs/events

## Mitigations Implemented
- Admin token checks on policy mutation endpoints
- Typed policy validation with strict safety locks
- Structured no-safe-plan contract (no unsafe fallback output)
- Request-size and rate-limit middleware
- Idempotency cache for duplicate request suppression
- Audit logs for policy create/activate/rollback

## Remaining Gaps
- External secrets rotation automation
- End-to-end encryption/key-management review
- PII minimization for telemetry pipeline
- Formal red-team abuse-path tests
