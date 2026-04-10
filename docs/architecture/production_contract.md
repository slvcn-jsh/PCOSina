# PCOSINA Production Contract (v1)

## Architecture
PCOSINA is an offline-first modular hybrid system:
- Mobile client (offline-first UX)
- Local persistence for profile/pantry/plan/grocery/log continuity
- Cloud sync for account-backed recovery and multi-device continuity
- Remote optimizer service (two-stage; CP-SAT authoritative stage)
- Supportive ML path for Stage 1 ranking only

## Planning Authority
- Stage 1: filtering/ranking/candidate generation
- Stage 2: formal 0-1 model solved by OR-Tools CP-SAT
- Hard constraints are never bypassed by heuristic or ML outputs

## Safety Contract
- Authoritative valid output only from constrained solver path
- On infeasibility: return `status=no-safe-plan` with diagnostics and guidance
- No unsafe approximate plan is returned as successful

## Runtime Policy Contract
- Planner policy is typed, bounded, versioned, auditable
- Active policy is resolved from persisted policy store with cache TTL
- Unsafe policy toggles are rejected at validation time

## Deployment Alignment
This contract supersedes older assumptions that implied pure client-side solving or heuristic fallback authority.
