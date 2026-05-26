1# PCOSINA Taglish Defense Coach Kit

This document is tailored to the current PCOSINA codebase and thesis scope. Any metrics are described without invented values. Use placeholders only if your panel requires exact figures.

## 1) Complete Defense Speaking Script in Taglish

### Opening (30-60 sec)
**Formal English version**
Good day, panel. I am presenting PCOSINA, a decision-support meal planning system for Filipino users with PCOS. The system integrates user profiles, dietary restrictions, and pantry inventory to generate a weekly meal plan that is nutritionally guided and culturally relevant. It is designed as an offline-first mobile app with an optimization backend, not as a medical treatment tool.

**Natural Taglish version**
Magandang araw po, panel. I’m presenting PCOSINA, a decision-support meal planning system para sa Filipino users with PCOS. We use user profile, dietary restrictions, at pantry inventory para makabuo ng weekly meal plan na nutritionally guided at culturally relevant. Offline-first siya, with optimization backend, and clearly hindi ito medical treatment tool.

**Simple explanation version**
PCOSINA is a meal planning app that helps Filipino users choose weekly meals based on their health goals and available ingredients. It gives guidance, not medical treatment.

**Technical depth version**
PCOSINA is an offline-first Android system using MVVM and a FastAPI backend. It collects profile constraints, filters recipes, and applies CP-SAT optimization to produce feasible weekly plans. Outputs include a plan, grocery consolidation, and progress tracking, framed as decision-support only.

### Problem, Objectives, Scope
**Formal English version**
The problem is that PCOS meal planning is complex, constraint-heavy, and rarely localized for Filipino food practices. Our objectives are to integrate user preferences and dietary restrictions, reduce waste through pantry-aware planning, and ensure offline usability. The scope is decision-support meal planning, not diagnosis or treatment.

**Natural Taglish version**
Ang problema: PCOS meal planning is complex, maraming constraints, at madalas hindi Filipino-localized. Ang objectives namin: i-integrate ang preferences at restrictions, bawasan ang waste gamit pantry data, at gawing usable kahit low or no internet. Scope namin is decision-support lang, hindi diagnosis or treatment.

**Simple explanation version**
It’s hard to plan meals for PCOS, especially using Filipino dishes and what you already have at home. The app helps with that, but it does not replace a doctor.

**Technical depth version**
We target constraint-aware weekly planning with personalization, pantry utilization, and offline-first storage. The system focuses on meal plan feasibility and usability rather than clinical outcomes. Scope excludes medical diagnosis, prescriptions, or therapy.

### Architecture Walkthrough
**Formal English version**
The Android app uses Jetpack Compose and MVVM. Screens are controlled by ViewModels that read and write to DataStore. Network requests go through Retrofit and OkHttp to a FastAPI backend that runs the optimizer. The backend accesses a recipe database seeded from recipes.json.

**Natural Taglish version**
Sa app side, Jetpack Compose + MVVM ang architecture. Screens are driven by ViewModels, tapos local storage is DataStore. Kung kailangan ng plan, dumadaan sa Retrofit/OkHttp papunta sa FastAPI backend. Sa backend, recipes are pulled from the DB seeded by recipes.json.

**Simple explanation version**
The app has screens that talk to a server. The server calculates the best meal plan and sends it back. The app saves the plan on the phone.

**Technical depth version**
UI is Compose-based with StateFlow in ViewModels. Repositories handle data sources: DataStore for offline persistence and Retrofit/OkHttp for API calls. The backend uses FastAPI with Pydantic schemas, reads recipes from SQLite/Postgres, and calls the CP-SAT planner.

### Backend, API, and Auth Flow
**Formal English version**
The app authenticates users using Firebase Auth. Each API call includes a Firebase ID token and a schema version header. The backend verifies tokens via Firebase Admin and validates payloads with Pydantic. Core endpoints include generate-plan, recipe details, recipe summaries, feedback, and health.

**Natural Taglish version**
Auth is Firebase. Every API request may include Firebase ID token at schema version header. Sa backend, Firebase Admin verifies token at Pydantic validates payloads. Endpoints: generate-plan, recipe/{id}, recipes/summary, feedback, at health.

**Simple explanation version**
Users log in, the app sends a secure token to the server, and the server checks it before giving a plan.

**Technical depth version**
Auth is enforced using Authorization: Bearer ID tokens in `MealPlanRepository.kt` and verified in `backend/main.py`. The backend includes request size limits and schema version checks, returns `status=no-safe-plan` for infeasible plans, and exposes `/schema` for contract compatibility.

### Optimization and MILP/CP-SAT Explanation
**Formal English version**
We use a two-stage approach: first, rule-based filtering ensures recipes meet restrictions and pantry criteria. Second, CP-SAT optimization assigns recipes to meal slots while minimizing nutritional deviations, penalizing repeats, enforcing budget caps when configured, and rewarding pantry usage and diversity.

**Natural Taglish version**
Two-stage siya: una, filtering based on restrictions and pantry; pangalawa, CP-SAT optimization ang nag-aassign ng recipes sa meal slots. Minimize ang deviations sa calories at macros, may penalties for repeats, hard cap ang budget kapag configured, at rewards for pantry matches at diversity.

**Simple explanation version**
We first remove recipes the user cannot eat. Then we pick the best set of meals that fits nutrition targets and variety rules.

**Technical depth version**
Binary decision variables `x[s,i]` select recipe i for slot s. Hard constraints ensure one recipe per slot, max usage per week, and the weekly budget cap when configured. Soft constraints model nutrition deviations, diversity, prep-time burden, and pantry tradeoffs. Objective minimizes deviations plus penalties, minus pantry and diversity rewards.

### Testing, Security, Deployment
**Formal English version**
We validate inputs using Pydantic schemas and local profile checks. The Android app uses logging and supports error monitoring via Sentry if configured. CI builds the Android release and checks backend dependencies, and the backend is deployable via Render.

**Natural Taglish version**
Validation ay Pydantic sa backend at profile checks sa app. May logging at optional Sentry monitoring. CI via GitHub Actions builds the Android release at backend dependency smoke test. Deployment target is Render.

**Simple explanation version**
We check inputs for errors, we can monitor crashes, and we deploy the backend online.

**Technical depth version**
Backend uses FastAPI middleware for request limits and schema headers, with Firebase verification. Android uses Retrofit with retry and timeouts. CI pipeline runs assembleRelease and backend dependency install. Render deployment is configured in render.yaml.

### Limitations and Future Work
**Formal English version**
Current limitations include reliance on dataset quality, heuristic cost estimates, and dependency on backend availability for new plans. Future work includes improved nutrition data, richer localization, stronger production security, and offline plan generation.

**Natural Taglish version**
Limitations: dataset quality, heuristic lang ang cost estimates, at kailangan ng backend para sa new plan. Future work: better nutrition data, richer localization, mas secure production config, at possible offline plan generation.

**Simple explanation version**
We still need better data and some features that work without internet when generating new plans.

**Technical depth version**
We use placeholder nutrition values when missing, and cost is estimated from ingredients and calories. Solver runs on backend only. Future work includes verified nutrition databases, price catalogs, and on-device optimization support.

### Closing Statement
**Formal English version**
PCOSINA delivers a constraint-aware, culturally relevant meal planning system that prioritizes feasibility, usability, and offline resilience. It supports users through decision guidance, not medical treatment, and provides a technical foundation for future improvements in personalization and evaluation.

**Natural Taglish version**
PCOSINA nagbibigay ng constraint-aware at culturally relevant meal planning, na focus ang feasibility, usability, at offline resilience. Decision-support lang ito, hindi medical treatment, at solid ang foundation for future improvements.

**Simple explanation version**
PCOSINA helps users plan meals better, using rules and data, and it can work even with limited internet.

**Technical depth version**
The system integrates MVVM mobile architecture, Firebase Auth, FastAPI backend services, and CP-SAT optimization. It balances constraints and practical usability, offering a defensible and extensible meal planning platform for PCOS decision-support.

## 2) Panel Q&A Taglish Bank (60 questions)

| Question | Short confident answer (15-30 sec) | Expanded expert answer (45-90 sec) | Keywords to mention | Line to avoid sounding unsure |
|---|---|---|---|---|
| What is PCOSINA in one sentence? | PCOSINA is a decision-support meal planning app for Filipino users with PCOS, using constraints and optimization for weekly plans. | It combines user profiles, restrictions, and pantry data to generate feasible weekly plans with a CP-SAT optimizer on the backend. It is not a medical tool. | decision-support, CP-SAT, constraints | Avoid: “Parang diet app lang.” |
| Why Taglish? | It’s for natural delivery and local context, but the system is fully technical. | We use Taglish here to communicate clearly, while the system itself uses formal architecture and optimization. | clarity, local context | Avoid: “Kasi di ko kaya pure English.” |
| Is it a medical device? | No, it’s decision-support only. | We do not diagnose or treat PCOS; we provide meal plan guidance using nutrition constraints. | decision-support, not treatment | Avoid: “It treats PCOS.” |
| What is the main algorithm? | Two-stage filtering plus CP-SAT optimization. | First we filter recipes by restrictions and pantry. Then CP-SAT assigns recipes to slots to minimize deviations and penalties. | filtering, CP-SAT, objective | Avoid: “We just pick top recipes.” |
| Why not ML? | Constraints need guaranteed feasibility; ML can’t guarantee that. | ML could predict preferences but would still need a solver to satisfy hard constraints like macros and restrictions. | feasibility, constraints | Avoid: “ML is better for everything.” |
| Where are recipes stored? | In the backend database seeded from recipes.json. | The backend seeds recipes.json into SQLite or Postgres, then serves via endpoints. | recipes.json, seeding | Avoid: “We fetch from the internet.” |
| What inputs are used? | Age, height, weight, activity, goal, restrictions, pantry, budget. | These inputs feed calorie and macro targets and filtering rules, plus cost and pantry rewards. | UserProfile, targets | Avoid: “Just preferences.” |
| What outputs are produced? | Weekly plan, grocery list, progress logs. | The backend returns a structured week; the app consolidates ingredients and stores logs. | weekly plan, grocery | Avoid: “A single recipe.” |
| Offline-first meaning? | Data stays on device; only new plan needs backend. | Plans, grocery lists, and reflections are stored in DataStore and encrypted prefs. | DataStore, encrypted | Avoid: “No backend needed.” |
| How is auth handled? | Firebase Auth on app, Firebase Admin on backend. | The app sends a Bearer token; backend verifies with Firebase Admin before planning. | Firebase ID token | Avoid: “No auth checks.” |
| What is schema versioning? | A header check for client-server compatibility. | Client sends X-PCOSINA-Schema-Version; server returns version and checks mismatch. | schema contract | Avoid: “We ignore versions.” |
| What if plan is infeasible? | Server returns `status=no-safe-plan`; user can relax safe inputs. | We return reason codes, diagnostics, and guidance without forcing a greedy or unsafe plan. | no-safe-plan, diagnostics | Avoid: “It never fails.” |
| How is budget handled? | Hard cap when configured. | Estimated weekly cost must stay within budget; cost is optimized directly only for budget-priority profiles. | budget cap | Avoid: “We ignore budget.” |
| How do you ensure diversity? | Soft diversity constraints and protein group caps. | We penalize repeats and limit protein group overuse; also reward vegetable coverage. | diversity, penalties | Avoid: “Random variety.” |
| What is a decision variable? | Binary choice of recipe per slot. | `x[s,i]` indicates whether recipe i is selected for slot s. | decision variable | Avoid: “A user choice.” |
| What is the objective function? | Minimize deviations and penalties, reward pantry and diversity. | It sums calorie and macro deviations plus repeat, prep-time, diversity, and optional cost-priority terms minus pantry and diversity rewards. | objective, penalties | Avoid: “Maximize calories.” |
| What is CP-SAT? | OR-Tools constraint solver using SAT-based techniques. | CP-SAT solves binary decision problems efficiently with constraints and penalties. | OR-Tools, CP-SAT | Avoid: “It’s ML.” |
| Why not greedy only? | Greedy can violate constraints; CP-SAT enforces them. | Greedy may pick good local meals but fail global feasibility, so CP-SAT is safer. | feasibility | Avoid: “Greedy is enough.” |
| What is the backend stack? | FastAPI with Pydantic, SQLite/Postgres. | FastAPI validates schemas, reads recipes, and runs the solver. | FastAPI, Pydantic | Avoid: “Just a script.” |
| What is the Android stack? | Jetpack Compose + MVVM + DataStore. | UI is Compose, state is in ViewModels, data in DataStore. | Compose, MVVM | Avoid: “Just XML.” |
| What testing exists? | Input validation, CI build, and smoke checks. | We validate inputs and have CI that builds Android release and installs backend deps. | CI, validation | Avoid: “No testing.” |
| How do you handle data privacy? | Keep data local; encrypt reflections. | User profiles and logs are on device; reflections use encrypted prefs. | EncryptedSharedPreferences | Avoid: “We store everything on server.” |
| What is the grocery consolidation flow? | Ingredients aggregated per plan and categorized. | The app aggregates ingredient lists and stores snapshots in DataStore. | aggregation, snapshots | Avoid: “Manual entry only.” |
| What is the role of DataStore? | Offline persistence for profile and plan data. | It stores profile, plan history, grocery lists, and logs per user. | DataStore | Avoid: “It’s cache only.” |
| What is the role of ViewModel? | Holds state and business logic for UI. | ViewModels manage profile, plan generation, and progress tracking. | ViewModel, StateFlow | Avoid: “Just a data class.” |
| What is the main deployment target? | Render for backend. | Render is configured in render.yaml with environment variables. | Render | Avoid: “Local only.” |
| How do you monitor errors? | Optional Sentry integration. | Both backend and Android can send error events if DSNs are configured. | Sentry | Avoid: “No monitoring.” |
| Why Filipino recipes? | Cultural relevance improves adherence. | Local dishes make plans practical and realistic for Filipino users. | cultural relevance | Avoid: “Any recipe is fine.” |
| What are the limitations? | Dataset quality, heuristic costs, backend dependency. | Missing nutrients are filled, costs estimated, and new plans require backend. | limitations, mitigation | Avoid: “No limitations.” |
| What’s next improvement? | Use verified nutrition data and stronger production security. | Upgrade dataset quality, add strict host checks, and improve offline planning. | future work | Avoid: “Nothing to improve.” |
| How do you compute calorie targets? | Mifflin-St Jeor with activity multiplier and goal adjustment. | Use weight, height, age, and activity to compute BMR/TDEE and adjust for goal. | BMR, TDEE | Avoid: “We guess.” |
| What’s the difference between auth and authorization? | Auth is login; authorization is token verification. | Firebase Auth issues tokens; backend checks them for API access. | auth, authorization | Avoid: “Same thing.” |
| What is a schema contract? | A shared API structure definition. | It defines request/response fields and versioning. | schema contract | Avoid: “Just a doc.” |
| Why do you cap repeats? | To improve variety and adherence. | Max-per-week constraint prevents monotony and improves diet quality. | repeat cap | Avoid: “Randomly chosen.” |
| What does no-safe-plan mean? | Valid request, but no complete safe plan was found. | It indicates infeasibility or timeout under current constraints and returns guidance instead of a plan. | no-safe-plan, infeasible | Avoid: “Server error.” |
| How do you handle errors in app? | Result wrappers and UI error states. | Repositories return Result and ViewModels map to UI state. | Result, UI state | Avoid: “We don’t.” |
| What is the plan explanation? | Metadata about targets and deviations. | It includes averages, tolerance used, pantry matches, and confidence score. | explanation, confidence | Avoid: “No explanation.” |
| What is the pantry reward? | Positive scoring for recipes matching pantry items. | It reduces waste by prioritizing available ingredients. | pantry reward | Avoid: “Pantry is forced.” |
| What is diversity slack? | A soft constraint allowing minimum variety. | It permits some violation with penalty if diversity cannot be met. | diversity slack | Avoid: “We ignore diversity.” |
| Why is backend needed? | Optimization is compute-heavy. | CP-SAT runs on the backend to avoid heavy device computation. | backend, solver | Avoid: “App does everything.” |
| How do you handle slow network? | Timeouts and retries. | OkHttp uses retry logic and timeouts before failing gracefully. | timeout, retry | Avoid: “It just hangs.” |
| How do you protect admin feedback? | Token required. | Admin feedback page checks a secret token before access. | admin token | Avoid: “Open to all.” |
| Are you using Room? | No, we use DataStore. | DataStore fits our key-value storage needs for profile and plan JSON. | DataStore | Avoid: “Room is required.” |
| How do you store reflections? | EncryptedSharedPreferences. | Sensitive logs and journals are encrypted at rest. | encryption | Avoid: “Plain text.” |
| What is the CI pipeline? | GitHub Actions build release and backend deps. | It builds Android release and installs backend requirements as smoke test. | CI, Actions | Avoid: “We have none.” |
| How do you ensure compatibility? | Schema version headers. | Client-server versions are checked to prevent mismatches. | schema versioning | Avoid: “We assume it works.” |
| Why not store data on server? | Privacy and offline-first design. | Local storage protects privacy and supports offline usage. | privacy, offline-first | Avoid: “We didn’t think about it.” |
| What if user logs out? | ViewModels reset and data remains per user. | Session clears and data is keyed by user ID to avoid leaks. | logout reset | Avoid: “Data mixes.” |
| How do you ensure meals per day? | Slot constraints ensure exactly one per slot. | Each day has 3 slots and each slot selects exactly one recipe. | slot constraint | Avoid: “We don’t enforce.” |
| What is the role of Pydantic? | Input validation and schema enforcement. | Pydantic ensures request fields are typed and valid. | Pydantic | Avoid: “We trust input.” |
| Why use FastAPI? | Fast, typed, and easy to document. | FastAPI provides automatic docs and strong request validation. | FastAPI | Avoid: “Because it was trendy.” |
| What is the data flow? | UI -> ViewModel -> Repository -> Backend -> UI. | Input flows from UI to backend and output is persisted locally. | data flow | Avoid: “It’s random.” |
| What is the main risk? | Data quality and constraint infeasibility. | Missing nutrients and strict constraints can cause infeasible plans. | risk, feasibility | Avoid: “No risks.” |
| How do you handle conflicting restrictions? | Validate and return conflict message. | We detect conflicts like Pescatarian plus No Seafood and fail early. | validation | Avoid: “We ignore.” |
| How do you control plan size? | Candidate pool is capped. | We limit candidate recipes to a maximum pool for performance. | pool cap | Avoid: “No limit.” |
| Why not purely rule-based? | Optimization gives better tradeoffs. | Rule-based alone cannot balance multiple objectives simultaneously. | optimization | Avoid: “Rules are enough.” |
| What is the performance bottleneck? | Solver time on optimization. | CP-SAT solve time grows with pool size and constraints. | solver time | Avoid: “No bottleneck.” |
| How do you explain results to users? | Plan explanation fields. | We return targets, averages, and deviations to justify plan quality. | explainability | Avoid: “We don’t explain.” |
| What if backend is down? | App uses cached plans, no new plan. | Offline data still displays, but new generation needs backend. | offline-first | Avoid: “App breaks entirely.” |
| Why are costs estimated? | Dataset lacks prices. | We estimate cost via ingredient count and calories as proxy. | heuristic cost | Avoid: “Prices are exact.” |
| Do you store user profile on server? | No, profile stays local. | Only plan request payload is sent during generation. | privacy | Avoid: “Yes, all data is on server.” |
| What is the role of feedback? | Collects user reports. | Feedback is stored in backend DB for review. | feedback | Avoid: “No feedback system.” |
| How do you handle schema mismatch? | 409 conflict or log warning. | The backend can return 409 if schema version differs. | 409, schema | Avoid: “Ignore it.” |
| What is the fallback? | Structured no-safe-plan guidance. | If CP-SAT cannot build a safe complete plan, the app preserves saved local plans and shows reason codes, diagnostics, and safe adjustment guidance. | no-safe-plan, cached plan | Avoid: “Always fallback.” |
| Why keep docs enabled? | Convenience for testing. | For production, docs should be restricted. | dev vs prod | Avoid: “Always open.” |
| How do you justify CP-SAT vs MILP? | CP-SAT solves binary constraint formulation effectively. | Our formulation is MILP-style, but we use CP-SAT for efficiency. | CP-SAT, MILP-style | Avoid: “They’re identical.” |
| What are the key constraints? | Slot assignment, repeat cap, macro bounds, and budget cap. | Hard constraints ensure structure and configured budget; soft terms handle diversity, pantry use, prep-time, and nutrition deviations. | hard vs soft | Avoid: “We only have macros.” |
| Why is evaluation important? | It validates usability and quality. | We use ISO/IEC 25010 criteria to assess system quality. | ISO/IEC 25010 | Avoid: “Evaluation is optional.” |
| How do you handle app crashes? | Crashlytics and optional Sentry. | If configured, crashes are reported for debugging. | Crashlytics, Sentry | Avoid: “We don’t track crashes.” |

## 3) Pronunciation + Delivery Coaching

### Difficult technical words and pronunciation
- CP-SAT: “see-pee-sat”
- Mifflin-St Jeor: “MIFF-lin st JEE-or”
- Pydantic: “pie-DAN-tik”
- Retrofit: “RET-ro-fit”
- EncryptedSharedPreferences: “en-CRYP-ted shared PREH-fer-ences”
- Schema: “SKEE-ma” or “SKAY-ma” but choose one consistently
- FastAPI: “fast A-P-I”
- DataStore: “DAY-ta store”
- Constraint: “con-STRAYNT”
- Feasible: “FEE-zuh-bul”

### Pacing guidance
- Slow down on the definition of PCOSINA and decision-support boundary.
- Pause after stating the two-stage algorithm.
- Emphasize “constraints,” “feasible,” and “offline-first.”
- When listing endpoints or modules, pause between items.

### Filler-word replacement list
- “uhm” -> “Let me clarify.”
- “like” -> “Specifically.”
- “parang” -> “In technical terms.”
- “maybe” -> “Based on our implementation.”
- “I think” -> “Our system shows.”

### Confidence phrases
- “Based on our implementation…”
- “In the current codebase…”
- “The solver enforces feasibility by…”
- “We intentionally scoped it as decision-support…”
- “The tradeoff we made is…”

## 4) Memory Tools

### 1-page cheat sheet (Taglish)
- PCOSINA = decision-support meal planning for PCOS, Filipino-focused.
- Offline-first: DataStore + encrypted reflections.
- Two-stage: filter recipes, then CP-SAT optimization.
- Hard constraints: slot assignment, max repeats.
- Soft constraints: diversity, budget.
- Backend: FastAPI + Pydantic + Firebase Admin.
- Auth: Firebase ID token, Authorization header.
- Endpoints: generate-plan, recipe/{id}, recipes/summary, feedback, health.

### 20 must-memorize technical terms
| Term | Simple definition |
|---|---|
| CP-SAT | Constraint solver used for optimization. |
| Decision variable | Binary choice per recipe per slot. |
| Objective function | The score we minimize. |
| Hard constraint | Must always be satisfied. |
| Soft constraint | Can be violated with penalty. |
| Feasible | All hard constraints satisfied. |
| Infeasible | No solution meets hard constraints. |
| DataStore | Offline key-value storage. |
| ViewModel | Holds UI state and logic. |
| StateFlow | Reactive state stream. |
| Retrofit | API client library. |
| OkHttp | HTTP transport library. |
| Pydantic | Backend validation schema. |
| FastAPI | Backend framework. |
| Firebase Auth | User authentication system. |
| Authorization header | Carries ID token. |
| Schema version | Client-server compatibility marker. |
| Pantry reward | Objective bonus for pantry matches. |
| Diversity penalty | Penalty for low variety. |
| Budget cap | Hard estimated weekly cost ceiling when configured. |

### 10 recovery lines
1. “Let me answer that based on the codebase.”
2. “In our current implementation, the flow is…”
3. “The constraint we enforce there is…”
4. “We treat that as a limitation and here’s our mitigation.”
5. “I’ll distinguish the hard constraint from the soft constraint.”
6. “That’s a good point; here’s the tradeoff we made.”
7. “From an optimization view, the objective is…”
8. “We intentionally scoped it as decision-support.”
9. “If a plan is infeasible, we return `status=no-safe-plan` with guidance.”
10. “For production, we would harden that setting.”

### 7-day rehearsal plan
- Day 1: Opening + problem + objectives.
- Day 2: Architecture walkthrough.
- Day 3: Backend and auth flow.
- Day 4: Optimization deep dive.
- Day 5: Testing, security, deployment.
- Day 6: Limitations and future work.
- Day 7: Full 10-minute run-through + Q&A drill.

## 5) Full 10-minute Taglish Mock Defense Speech (verbatim)

Magandang araw po, panel. I’m presenting PCOSINA, a decision-support meal planning system for Filipino users with PCOS. The goal is to help users generate weekly meal plans that are nutritionally guided, culturally relevant, and realistic given their constraints. The system is clearly not a medical treatment tool; it provides guidance and planning support only.

The problem we address is that PCOS meal planning is complex and constraint-heavy, and most tools are not localized to Filipino food practices. Our objectives are to integrate user preferences and dietary restrictions, reduce food waste using pantry data, and ensure offline usability. The scope is decision-support meal planning, not diagnosis or treatment.

For architecture, the Android app is built with Jetpack Compose and MVVM. The screens are driven by ViewModels, and local persistence uses DataStore. When a user generates a plan, the app sends a request through Retrofit and OkHttp to our FastAPI backend. The backend runs the optimization and returns a weekly plan, which the app saves locally for offline access.

On the backend, we use FastAPI with Pydantic schemas to validate inputs. Authentication uses Firebase: the app sends a Firebase ID token in the Authorization header, and the backend verifies it using Firebase Admin. We also send a schema version header to detect client-server mismatches. Core endpoints are generate-plan, recipe detail retrieval, recipe summary retrieval, feedback submission, and health check.

For the algorithm, we use a two-stage approach. First is rule-based filtering: we remove recipes that violate restrictions such as No Pork or Vegetarian. We also infer tags and pantry matches. Second is CP-SAT optimization using OR-Tools. We define binary decision variables for whether a recipe is selected for a meal slot. Hard constraints ensure exactly one meal per slot, limit how often a recipe can repeat, and enforce the weekly budget cap when configured. Soft terms handle nutrition deviations, diversity, prep-time, and pantry rewards. The objective minimizes deviations from daily calorie and macro targets, plus penalties, minus rewards. This gives us a feasible and explainable plan when one exists; otherwise the system returns no-safe-plan guidance.

Testing and reliability are handled through validation and CI. The backend validates requests with Pydantic and uses middleware for size limits and schema headers. The Android app uses retries and timeouts for network calls. Our CI pipeline builds the Android release and runs a backend dependency smoke test. Deployment for the backend is configured on Render. Monitoring is possible through Sentry if configured.

For limitations, we acknowledge that dataset quality affects results. Some nutrition values are missing and are replaced with placeholders in the seed. Cost estimates are heuristic based on ingredient count and calories. Also, new plan generation requires backend connectivity, although existing plans are available offline. Future work includes verified nutrition data, stronger production security settings, richer localization, and possible offline generation.
In closing, PCOSINA provides a constraint-aware and culturally relevant meal planning system, balancing feasibility, usability, and offline resilience. It is designed as decision-support for users with PCOS, not a medical device. Our architecture and optimization approach make the system explainable and extensible, and our future work focuses on improving data quality and production hardening. Thank you, and I’m ready for your questions.
