# Offline Plan Generation Feasibility (PCOSINA)

This note documents feasibility considerations for on-device optimization.

## Current State
- Optimization runs on backend (FastAPI + OR-Tools CP-SAT).
- Android app sends profile and receives weekly plan.

## Feasibility Checks
- Model size: binary variables per slot and per candidate recipe can grow quickly.
- Device constraints: mobile CPU and battery make long CP-SAT solves risky.
- Memory: candidate pools and constraint graphs can be large.

## Practical Path
1. Reduce candidate pool locally using filters (restrictions, mealType, max time).
2. Use a lightweight heuristic fallback on-device for offline generation.
3. Keep full CP-SAT optimization on backend for high-quality plans.

## Recommendation
Implement on-device heuristic plans for offline-only scenarios and keep CP-SAT as the primary solver in backend.

## Next Steps
- Prototype a small candidate pool heuristic (<=30 recipes).
- Measure solve time and battery impact on mid-range devices.
- Add configuration to switch between heuristic and backend solver based on connectivity.
