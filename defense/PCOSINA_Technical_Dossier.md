# PCOSINA Defense-Ready Technical Dossier

## Executive Summary (1 page)
PCOSINA is an offline-first, Filipino-focused meal planning system for individuals with PCOS. It generates weekly meal plans using a two-stage approach: preference-aware filtering followed by Mixed-Integer Linear Programming (MILP) optimization using OR-Tools CP-SAT. The Android app provides onboarding, profiling, plan viewing, grocery consolidation, and progress tracking. A FastAPI backend generates plans and serves recipe data. Firebase authentication is used for user identity, while core user data and logs remain local on the device. PCOSINA is a decision-support tool for wellness guidance, not a medical treatment system.

The architecture separates the user interface, optimization logic, and data storage to support explainability, constraint compliance, and practical use in low-connectivity environments. The algorithm prioritizes nutrition targets, budget sensitivity, pantry usage, and meal variety while remaining transparent and constraint-aware. Evaluation follows ISO/IEC 25010, using alpha and beta testing, descriptive statistics, Cronbach's alpha for instrument reliability, and computation precision checks. Main operational risks include misconfigured authentication, open API docs exposure, and plan-generation load under constrained hosting. These are mitigated through token-based auth, schema version checks, request size limits, solver time caps, and plan caching.

## Technical Summary (1 page)
PCOSINA uses Kotlin and Jetpack Compose with ViewModels for state management. DataStore stores profiles, plans, groceries, and feedback queues. EncryptedSharedPreferences stores reflections and weekly journals. The backend is FastAPI with a CP-SAT solver that implements a two-stage MILP optimization pipeline. Recipes are seeded from `backend/recipes.json` into SQLite by default, with optional Postgres via `DATABASE_URL`. Deployment uses Render for the backend, and Firebase for authentication and analytics on the Android client.

The algorithm filters recipes by restrictions, pantry match, and estimated cost, then optimizes a weekly plan across daily meal slots using macro deviation penalties, hard budget caps when configured, repeat limits, and diversity incentives. If no complete safe plan can be solved, production returns structured `no-safe-plan` diagnostics and guidance instead of a greedy authoritative fallback. Offline-first behavior means users can view saved plans, groceries, and logs without connectivity; generating a new plan still requires the backend. Security uses Firebase ID tokens, schema version validation, request size limits, and admin token gating for feedback access. Performance relies on candidate shortlisting, pool caps, solver time limits, and caching.

## 1) End-to-End Architecture
### Components and Roles
- Android client: UI, onboarding, profile, plan view, grocery list, progress tracking.
- Local storage: DataStore for preferences and plan history; encrypted storage for reflections.
- Backend API: FastAPI service for plan generation and recipes.
- Optimization engine: OR-Tools CP-SAT MILP solver.
- Database: SQLite by default for recipes and feedback; Postgres optional.
- Deployment: Render hosting for backend; Firebase for auth.

### Evidence
- Android navigation and flows: `app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt`
- Backend entrypoint and endpoints: `backend/main.py`
- Solver implementation: `backend/services/meal_planner.py`
- Database layer: `backend/database.py`
- Render deployment: `render.yaml`

## 2) Complete File/Module Map (Major Files)
### Android
- `app/src/main/java/com/pcosina/app/MainActivity.kt`: Compose host entrypoint.
- `app/src/main/java/com/pcosina/app/PcosinaApp.kt`: Sentry init.
- `app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt`: Navigation graph and auth guard.
- `app/src/main/java/com/pcosina/app/ui/screens/*.kt`: UI screens (Splash, Login, Onboarding, MealPlan, Grocery, Progress, Settings).
- `app/src/main/java/com/pcosina/app/ui/*ViewModel.kt`: State logic for auth, profile, meal plan, grocery, progress.
- `app/src/main/java/com/pcosina/app/data/repository/*`: Auth, DataStore, API, feedback, encrypted store.
- `app/src/main/java/com/pcosina/app/data/api/*`: DTOs and Retrofit interface.
- `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt`: BMR, TDEE, BMI.
- `app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt`: Grocery categories and price estimates.
- `app/src/main/java/com/pcosina/app/data/model/*`: Core data models.

### Backend
- `backend/main.py`: FastAPI app, routes, auth, caching.
- `backend/services/meal_planner.py`: MILP/CP-SAT solver.
- `backend/domain/models.py`: Pydantic request/response schemas.
- `backend/database.py`: SQLite/Postgres access, recipe seeding, feedback storage.
- `backend/recipes.json`: Recipe dataset.
- `backend/schema/pcosina_contract.json`: API contract.
- `docs/backend-config.md`: Environment variables.
- `render.yaml`: Render deployment configuration.

## 3) API Contract Summary
| Endpoint | Method | Auth | Role |
|---|---|---|---|
| `/health` | GET | None | Liveness check |
| `/generate-plan` | POST | Firebase bearer | Generate weekly plan |
| `/recipe/{id}` | GET | Firebase bearer | Recipe details |
| `/recipes/summary` | GET | Firebase bearer | Recipe summaries for swaps |
| `/feedback` | POST | Schema version | Save feedback |
| `/admin/feedback` | GET | Token | Admin feedback view |
| `/schema` | GET | None | Schema contract |

Evidence: `backend/main.py`, `backend/domain/models.py`, `backend/schema/pcosina_contract.json`.

## 4) Full Optimization Explanation (MILP/CP-SAT)
- Decision variables: binary `x[s,i]` for selecting recipe `i` in slot `s`.
- Objective: minimize calorie deviation, macro deviation, repeat penalties, group penalties, optional cost-priority terms; reward pantry matches and diversity.
- Hard constraints: one recipe per slot; no consecutive repeats; max repeats per week; weekly budget cap when configured.
- Soft constraints: nutrition deviations, protein group diversity, ingredient diversity, pantry rewards.
- Fallback: production returns structured `no-safe-plan` diagnostics instead of an authoritative greedy plan.

Evidence: `backend/services/meal_planner.py`.

## 5) Why MILP/CP-SAT Over Greedy/Rule-Only
- MILP guarantees feasibility under explicit constraints and provides explainable tradeoffs.
- Greedy or rule-only methods cannot reliably satisfy multiple constraints simultaneously.
- Non-ML approach aligns with thesis emphasis on transparency and offline execution.

Evidence: Chapter 1 Sec 1.4.4; `backend/services/meal_planner.py`.

## 6) Security Model, Threat Model, and Mitigations
### Security Model (Repo-Grounded)
- Firebase ID token verification on protected routes.
- Schema version enforcement to prevent contract mismatch.
- Request size limit middleware.
- Admin feedback endpoint protected by token.
- Encrypted local storage for reflections.

Evidence: `backend/main.py`, `docs/backend-config.md`, `ReflectionStore.kt`.

### Threat Model
Full report: `PCOSINA2-threat-model.md` (created at repo root).

### Key Mitigations
- Disable or protect `/docs`, `/openapi.json` in production.
- Restrict `allowed_hosts` to trusted domains.
- Avoid query-string admin tokens.
- Enforce Firebase auth in production.
- Add rate limiting for `/generate-plan`.

## 7) Performance Strategy and Scalability Notes
- Candidate shortlist to shrink solver search space.
- Pool cap to bound variable count.
- Solver time limits with tolerance levels.
- Cached plans to reduce duplicate computation.
- Render deployment with 2 workers and 120s timeout.

Evidence: `backend/services/meal_planner.py`, `backend/main.py`, `render.yaml`.

## 8) Limitations, Risks, and Future Improvements
- Offline-first is partial; plan generation still requires backend connectivity.
- Nutrition values can be incomplete; repo uses placeholder estimates.
- Variety limited by dataset size and constraints.
- Admin token management is manual and could be strengthened.

Future work: expand dataset with validated nutrition, add offline sync, enforce stricter production security, add monitoring and rate limits.

Evidence: Chapter 1 Sec 1.7.4; `backend/database.py`.

## 9) Oral Defense Q&A (30)
1. What is PCOSINA? A decision-support, offline-first meal planner for Filipino PCOS users using MILP.
2. Why MILP not ML? MILP enforces constraints and remains explainable.
3. What is the two-stage approach? Filter recipes then optimize weekly plan.
4. What constraints are enforced? Calories, macros, budget, repeats, pantry, variety.
5. How do you handle infeasible plans? Return `status=no-safe-plan` with reason codes, diagnostics, and safe relaxation guidance.
6. Is there a fallback? Yes: a structured no-safe-plan guidance path, not an authoritative greedy meal plan.
7. What is offline-first in your app? Local storage of plans and logs; plan generation needs backend.
8. What screens exist? Splash, Login, SignUp, Onboarding, Profile, Goal, Dashboard, MealPlan, Grocery, Progress, Settings.
9. What are backend endpoints? `/generate-plan`, `/recipe/{id}`, `/recipes/summary`, `/feedback`, `/health`.
10. How is auth enforced? Firebase bearer tokens.
11. Where is user data stored? Local DataStore and encrypted preferences.
12. What data is stored server-side? Recipes and feedback messages.
13. How is grocery cost estimated? Rule-based keyword matching.
14. How is personalization handled? Restrictions, pantry items, budget, goals in filtering and MILP.
15. What is your evaluation framework? ISO/IEC 25010.
16. What statistics are used? Frequency, percentage, weighted mean.
17. How is reliability measured? Cronbach's alpha.
18. Why no precision/recall? The system is not a classifier.
19. What is the dataset source? Filipino recipes per thesis; implemented as `recipes.json`.
20. How is BMI calculated? Standard BMI and Mifflin-St Jeor for targets.
21. How is pantry used? Pantry matches are rewarded; grocery list is consolidated.
22. Why offline-first? Low connectivity in PH context.
23. What are your limitations? Data completeness, variety constraints, online generation.
24. How prevent cross-account data mix? User ID namespacing and reset on logout.
25. How is feedback handled? Queued offline and sent when online.
26. Is this a medical device? No, decision-support only.
27. What are next steps? Expand dataset, tighten security, improve offline sync.
28. Where is backend hosted? Render.
29. How do you handle schema changes? Schema version header check returns 409.
30. What is the solver time strategy? Pool cap + time limit + tolerance levels.

## 10) One-Page Executive Summary and Technical Summary
Included at the top of this dossier.
