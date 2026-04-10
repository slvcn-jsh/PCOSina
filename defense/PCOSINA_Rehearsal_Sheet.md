# PCOSINA 15-Minute Rehearsal Sheet (2 pages target)

Time 0:00-1:00 Intro and project definition
- PCOSINA is an offline-first, Filipino-focused meal planning app for PCOS.
- Decision-support tool, not medical treatment.
- Core goal: generate weekly plans using two-stage MILP optimization.

Time 1:00-3:00 Problem and background
- PCOS is common and often undiagnosed; diet is crucial for management.
- Existing apps are generic, not culturally relevant, ignore pantry and offline needs.
- Filipino diets are carb-heavy; guidance is needed in local context.

Time 3:00-4:30 Objectives and scope
- General objective: build and evaluate PCOSINA.
- Specific objectives: two-stage MILP, personalization, pantry integration, offline-first, evaluation.
- Scope: Filipino recipes, 7-day plan, prototype focus on algorithm and offline usability.

Time 4:30-6:30 Architecture
- Android app: onboarding, profile, meal plan, grocery list, progress tracking.
- Local storage: DataStore + encrypted preferences for offline access.
- Backend: FastAPI for plan generation and recipe details.
- Deployment: Render hosting and Firebase auth.

Time 6:30-9:30 Algorithm and optimization
- Two-stage: filter recipes then MILP optimization.
- Variables: x[s,i] selects recipe i for slot s.
- Constraints: one meal per slot, no consecutive repeats, weekly repeat caps.
- Objective: minimize calorie/macro deviation, repeats, budget overrun; reward pantry and diversity.
- Tolerance relaxation: try 0.2, 0.3, 0.4 and increasing repeat limits.
- Fallback: infeasible reported; optional greedy fallback if enabled.

Time 9:30-11:30 Evaluation approach
- ISO/IEC 25010: functional suitability, usability, reliability, performance.
- Alpha and beta testing.
- Stats: frequency, percentage, weighted mean.
- Reliability: Cronbach’s alpha.
- Computation accuracy: AE and PE for BMI and macro calculations.

Time 11:30-13:30 Security and reliability
- Auth: Firebase tokens verified in backend.
- Schema version checks to prevent mismatched contracts.
- Request size limit to reduce abuse.
- Risks: docs exposure, admin token leakage, solver DoS.
- Mitigations: restrict docs, header-only admin token, rate limits.

Time 13:30-15:00 Closing
- Built a constraint-aware, culturally localized, offline-first meal planner.
- Transparent optimization suited for PCOS decision support.
- Next steps: richer dataset, stronger offline planning, larger evaluations.

Delivery reminders
- Always say “decision-support, not medical treatment.”
- Admit offline-first is partial: saved data works offline; plan generation is online.
- If asked about results, use exact numbers from Chapter 3 or say “not reported.”
