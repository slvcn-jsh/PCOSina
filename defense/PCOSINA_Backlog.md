# PCOSINA Jira/Linear-Style Backlog

This backlog is derived from the current PCOSINA codebase and the upgrade analysis. Status is set to Backlog by default.

## P0 — Correctness and Contract Alignment

| Title | Problem | Proposed Solution | Acceptance Criteria | Priority | Effort | Status | Dependencies |
|---|---|---|---|---|---|---|---|
| Define canonical UserProfile schema | App and backend profiles diverge; fields are lost in transit. | Define a single canonical schema and map all fields explicitly. | One shared schema documented; app and backend models match in fields and names. | P0 | M | Backlog | None |
| Align budget fields (weekly/monthly) | App sends `weeklyBudgetPhp` but backend expects `budgetWeekly`/`budgetMonthly`. | Add explicit mapping in API DTO and backend model. | Budget values appear in backend request and influence solver. | P0 | S | Backlog | Define canonical UserProfile schema |
| Enforce mealType in optimizer | Backend ignores `mealType`, allowing breakfast at dinner. | Restrict slot eligibility using recipe mealType buckets. | Breakfast, lunch, dinner slots only pick eligible recipes. | P0 | M | Backlog | None |
| Add allergies filter in backend | App collects allergies; backend ignores them. | Include allergy tokens in restriction filter. | Recipes with allergens are excluded. | P0 | M | Backlog | Define canonical UserProfile schema |
| Use insulin resistance level in constraints | App captures insulin resistance; backend ignores it. | Adjust macro targets or tolerance based on insulin resistance level. | Solver behavior changes based on insulin level input. | P0 | M | Backlog | Define canonical UserProfile schema |
| Normalize BMR/TDEE logic | App and backend may compute targets differently. | Extract shared logic or align equations and parameters. | Targets match within expected rounding. | P0 | M | Backlog | Define canonical UserProfile schema |

## P1 — Optimization Quality and UX

| Title | Problem | Proposed Solution | Acceptance Criteria | Priority | Effort | Status | Dependencies |
|---|---|---|---|---|---|---|---|
| Preference-aware weights | Variety and cooking time exist in profile but unused. | Use `varietyPreference` and `maxCookingTimeMinutes` to weight objective or filter. | Plan composition changes when preferences change. | P1 | M | Backlog | Define canonical UserProfile schema |
| Pantry coverage constraint | Pantry is a reward only; weak waste reduction. | Add minimum pantry match coverage constraint per week. | Plans include a measurable pantry usage threshold. | P1 | M | Backlog | Align budget fields |
| Meal-level macro distribution | Daily macro totals can be imbalanced across meals. | Add per-meal bounds or penalties to smooth distribution. | Each meal stays within defined macro ranges. | P1 | L | Backlog | Normalize BMR/TDEE logic |
| Explainability cards in UI | Plan explanation exists but not surfaced. | Display explanation metrics in Meal Plan screen. | UI shows target vs average and confidence. | P1 | S | Backlog | None |
| Plan expiration UX | Expired plan state computed but not emphasized. | Add badge or banner for expired plans. | Users see plan is expired and prompted to regenerate. | P1 | S | Backlog | None |
| Local swap optimization | Swap logic fetches details repeatedly. | Cache summaries and details for swaps and grocery sources. | Swaps are instant for cached recipes. | P1 | S | Backlog | None |
| Adaptive solver timeouts | Fixed timeouts under/over-allocate compute. | Adjust solver time based on pool size and restrictions. | Solver uses longer time for stricter inputs. | P1 | M | Backlog | None |
| Plan cache normalization | Cache misses for equivalent profiles. | Normalize request payload ordering and default values. | Cache hits increase for similar inputs. | P1 | S | Backlog | Define canonical UserProfile schema |

## P1 — Security and Reliability

| Title | Problem | Proposed Solution | Acceptance Criteria | Priority | Effort | Status | Dependencies |
|---|---|---|---|---|---|---|---|
| Restrict allowed hosts | Backend accepts all hosts in production. | Configure allowed host list per environment. | Production rejects unknown Host headers. | P1 | S | Backlog | None |
| Disable docs in production | `/docs` exposed in production. | Gate docs by environment flag. | Docs disabled by default in production. | P1 | S | Backlog | None |
| Remove auth bypass in production | Auth can be disabled via env flag. | Fail closed when production env set. | Unauthorized requests are always rejected. | P1 | S | Backlog | None |
| Admin token via header only | Token in query string leaks via logs. | Require `X-Admin-Token` header only. | Query token rejected, header required. | P1 | S | Backlog | None |
| Add request rate limits | Optimization endpoints can be abused. | Add rate limit middleware. | Requests over threshold return 429. | P1 | M | Backlog | None |

## P2 — Data Quality and Dataset Pipeline

| Title | Problem | Proposed Solution | Acceptance Criteria | Priority | Effort | Status | Dependencies |
|---|---|---|---|---|---|---|---|
| Replace placeholder nutrition values | Seeding uses random nutrition if missing. | Integrate verified nutrition sources or flag missing. | All recipes have verified nutrition values. | P2 | L | Backlog | None |
| Nutrition normalization pipeline | Inconsistent serving sizes and units. | Add preprocessing to normalize values. | Nutrition units consistent across dataset. | P2 | L | Backlog | Replace placeholder nutrition values |
| Price catalog integration | Budget estimate is heuristic only. | Integrate `PriceCatalog.kt` with backend cost model. | Costs reflect real price data per ingredient. | P2 | M | Backlog | Align budget fields |

## P2 — Testing and Evaluation

| Title | Problem | Proposed Solution | Acceptance Criteria | Priority | Effort | Status | Dependencies |
|---|---|---|---|---|---|---|---|
| Optimizer unit tests | No automated tests for constraints. | Add tests for feasibility and constraints. | Tests cover hard and soft constraints. | P2 | M | Backlog | Define canonical UserProfile schema |
| API contract tests | Client and server can drift silently. | Add contract tests for request/response fields. | Contract tests fail on schema mismatch. | P2 | M | Backlog | Define canonical UserProfile schema |
| UI flow tests | Critical flows not tested. | Add instrumentation tests for login-plan-grocery-progress. | Tests pass on CI for core flows. | P2 | L | Backlog | None |

## P2 — Scalability and Deployment

| Title | Problem | Proposed Solution | Acceptance Criteria | Priority | Effort | Status | Dependencies |
|---|---|---|---|---|---|---|---|
| Background optimization jobs | Heavy solves block API threads. | Add job queue for optimization. | API returns job id; result fetched later. | P2 | L | Backlog | Adaptive solver timeouts |
| Staging vs production configs | Single config risks accidental prod settings. | Add explicit staging/prod environment configs. | Separate env values for each deployment. | P2 | M | Backlog | None |

## P2 — Product Enhancements

| Title | Problem | Proposed Solution | Acceptance Criteria | Priority | Effort | Status | Dependencies |
|---|---|---|---|---|---|---|---|
| “Why this meal” explanation | Users don’t know why a meal was chosen. | Add explanation chips per meal. | Each meal shows 1-2 reason tags. | P2 | M | Backlog | Explainability cards in UI |
| Feedback-driven preference tuning | Preferences are static. | Add weighting adjustments from feedback logs. | Planner weights change with user feedback. | P2 | L | Backlog | None |
| Offline plan generation (research) | New plans require backend. | Evaluate on-device solver feasibility. | Feasibility report and prototype if viable. | P2 | L | Backlog | Background optimization jobs |
