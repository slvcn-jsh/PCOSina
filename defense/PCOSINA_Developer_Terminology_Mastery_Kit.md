# PCOSINA Developer Terminology Mastery Kit

This kit is grounded in the current PCOSINA codebase. If a term is not directly implemented, it is marked as "thesis-only" or "not present" and includes where to verify.

## 1) Core Terminology Dictionary (120+ terms)

### Android UI and Navigation (1-20)
| Term | Definition | Where In PCOSINA | Why It Matters | Common Panel Question | Strong Sample Answer |
|---|---|---|---|---|---|
| Jetpack Compose | Declarative UI toolkit for Android. | `app/src/main/java/com/pcosina/app/ui/screens/*.kt` | All screens are Compose-based. | Why Compose? | It makes UI state-driven and consistent with MVVM. |
| Composable | Function that emits UI. | `app/src/main/java/com/pcosina/app/ui/components/*.kt` | Core unit of every screen. | What is a Composable? | A function that re-renders when state changes. |
| Material3 | Design system components and theming. | `app/src/main/java/com/pcosina/app/ui/theme/*.kt` | Keeps UI consistent and accessible. | Why Material3? | It provides consistent layout and default accessibility. |
| Scaffold | Layout with slots and bottom bar. | `app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt` | Hosts bottom nav and content. | What does Scaffold do here? | It wraps content and the bottom navigation. |
| NavHost | Container for navigation graph. | `app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt` | Declares all routes. | Where is navigation defined? | In `AppNavHost.kt` with `NavHost`. |
| NavController | Handles navigation state and actions. | `AppNavHost.kt` | Drives screen transitions. | How do you navigate screens? | `NavController.navigate` with route strings. |
| Routes | Central route constants and patterns. | `app/src/main/java/com/pcosina/app/ui/navigation/Routes.kt` | Avoids hardcoded route strings. | Why centralize routes? | Prevents mismatches and simplifies navigation updates. |
| BottomNavBar | Bottom tab component. | `app/src/main/java/com/pcosina/app/ui/components/BottomNavBar.kt` | Allows fast feature access. | Where is bottom nav defined? | In `BottomNavBar.kt`. |
| Splash Screen | Initial gating screen. | `app/src/main/java/com/pcosina/app/ui/screens/SplashScreen.kt` | Waits for auth and profile load. | Why a splash gate? | It prevents showing wrong screen before data is ready. |
| Onboarding Screen | Intro step before profile. | `app/src/main/java/com/pcosina/app/ui/screens/OnboardingScreen.kt` | Enforces profile completion. | What happens after onboarding? | User fills profile, then selects goal. |
| User Profile Screen | Profile input UI. | `app/src/main/java/com/pcosina/app/ui/screens/UserProfileScreen.kt` | Collects planning inputs. | What data is captured? | Age, height, weight, goal, restrictions, pantry. |
| Goal Selection Screen | Goal selection UI. | `app/src/main/java/com/pcosina/app/ui/screens/GoalSelectionScreen.kt` | Feeds target calorie logic. | How does goal affect plan? | It changes daily calorie targets. |
| Dashboard Screen | App hub screen. | `app/src/main/java/com/pcosina/app/ui/screens/DashboardScreen.kt` | Main entry point for actions. | What is the main hub? | The dashboard with plan and feature shortcuts. |
| Meal Plan Screen | Weekly plan view. | `app/src/main/java/com/pcosina/app/ui/screens/MealPlanScreen.kt` | Displays generated plan. | Where do users see the plan? | On the Meal Plan tab. |
| Grocery List Screen | Consolidated ingredients view. | `app/src/main/java/com/pcosina/app/ui/screens/GroceryListScreen.kt` | Reduces waste and guides shopping. | How is grocery list created? | Aggregated from chosen meals. |
| Recipe Details Screen | Recipe information and actions. | `app/src/main/java/com/pcosina/app/ui/screens/RecipeDetailsScreen.kt` | Adds ingredients to grocery. | What can users do there? | Review recipe and add ingredients to grocery. |
| Progress Screen | Tracking and journal UI. | `app/src/main/java/com/pcosina/app/ui/screens/ProgressScreen.kt` | Logs adherence and reflections. | What is tracked? | Adherence, weight, and journaling. |
| Settings Screen | Profile and logout UI. | `app/src/main/java/com/pcosina/app/ui/screens/SettingsScreen.kt` | Edit profile, logout, clear data. | Where is logout handled? | In Settings screen actions. |
| IPO Visualization Screen | Input-Process-Output view. | `app/src/main/java/com/pcosina/app/ui/screens/IpoVisualizationScreen.kt` | Supports thesis framework explanation. | Why include IPO view? | It visualizes the system process loop. |
| MainActivity | Android entry activity. | `app/src/main/java/com/pcosina/app/MainActivity.kt` | Hosts Compose app. | What starts the app? | `MainActivity` sets up the Compose content. |

### Android Architecture and State (21-40)
| Term | Definition | Where In PCOSINA | Why It Matters | Common Panel Question | Strong Sample Answer |
|---|---|---|---|---|---|
| MVVM | UI pattern separating view and logic. | `app/src/main/java/com/pcosina/app/ui/*ViewModel.kt` | Keeps UI reactive and testable. | Why MVVM? | It separates UI from logic and supports state flows. |
| ViewModel | Holds UI state and logic. | `app/src/main/java/com/pcosina/app/ui/AuthViewModel.kt` | Survives config changes. | What is the ViewModel role? | It owns UI state and calls repositories. |
| StateFlow | Observable state stream. | `AuthViewModel.kt`, `MealPlanViewModel.kt` | Drives Compose updates. | Why StateFlow? | Compose can collect it to update UI. |
| Flow | Asynchronous stream of data. | `UserPreferencesRepository.kt` | Supports reactive data access. | Why Flow? | It lets UI react to data changes. |
| collectAsState | Collects Flow into Compose state. | `AppNavHost.kt` | Bridges Flow to UI. | How does UI observe state? | `collectAsState()` on flows. |
| MutableStateFlow | Writable state stream. | `AuthViewModel.kt` | Allows controlled state updates. | Why MutableStateFlow? | ViewModel updates state from async operations. |
| LaunchedEffect | Compose side effect handler. | `AppNavHost.kt` | Syncs data at lifecycle points. | Why LaunchedEffect? | It triggers data loading and navigation guards. |
| Repository Pattern | Data access abstraction. | `app/src/main/java/com/pcosina/app/data/repository/*.kt` | Separates data sources from UI. | Why repositories? | Keeps ViewModels clean and testable. |
| UseCase (placeholder) | Domain logic placeholder. | `app/src/main/java/com/pcosina/app/domain/GeneratePlanUseCase.kt` | Marks planned domain layer. | Is generation in app? | Not yet, generation is on the backend. |
| Preferences DataStore | Key-value storage for profile. | `UserPreferencesRepository.kt` | Offline-first persistence. | Why DataStore? | It is lightweight and works offline. |
| Session DataStore | Auth session persistence. | `AuthRepository.kt` | Restores login state. | How is session stored? | In `auth_prefs` DataStore. |
| EncryptedSharedPreferences | Encrypted key-value store. | `ReflectionStore.kt` | Protects journals and logs. | Why encrypt reflections? | They contain sensitive user notes. |
| MasterKey | Encryption key for device storage. | `ReflectionStore.kt` | Enables AES encryption. | Where is the key defined? | `MasterKey` in `ReflectionStore.kt`. |
| Session | Logged-in user state object. | `app/src/main/java/com/pcosina/app/data/model/Session.kt` | Drives auth guard logic. | What triggers auth guard? | `Session.isLoggedIn` in `AppNavHost.kt`. |
| Auth Guard | Navigation gate for login. | `AppNavHost.kt` | Prevents access when logged out. | How do you guard routes? | Check session and redirect to Login. |
| Offline-first | Core features work offline. | `UserPreferencesRepository.kt` | Plan and logs stay local. | What makes it offline-first? | DataStore and local cache store plans. |
| Local Cache | In-memory cached recipe details. | `MealPlanRepository.kt` | Reduces repeated network calls. | How do you cache recipes? | LinkedHashMap LRU cache. |
| LinkedHashMap LRU | LRU style cache map. | `MealPlanRepository.kt` | Limits memory growth. | Why LRU? | Keeps recent recipes while bounding size. |
| UnitConverter | Converts height and weight. | `app/src/main/java/com/pcosina/app/domain/UnitConverter.kt` | Ensures correct unit display. | Why unit conversion? | Users can input metric or imperial. |
| HealthMetrics | Computes BMR and targets. | `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt` | Drives calorie target logic. | How is target computed? | Mifflin-St Jeor with activity and goal. |
### Auth, Networking, and Libraries (41-60)
| Term | Definition | Where In PCOSINA | Why It Matters | Common Panel Question | Strong Sample Answer |
|---|---|---|---|---|---|
| Firebase Auth | Authentication provider. | `AuthRepository.kt` | Secure login and user ID. | Why Firebase Auth? | It handles identity and tokens reliably. |
| Email Verification | Requires verified email login. | `AuthRepository.kt` | Prevents fake accounts. | Do you enforce verification? | Yes, login blocks unverified emails. |
| Google Sign-In | OAuth login option. | `AuthRepository.kt` | Alternative login method. | Is Google Sign-In supported? | Yes, via Firebase credential login. |
| Firebase Analytics | Usage event tracking. | `AppNavHost.kt` | Tracks feedback taps. | What analytics are logged? | Only minimal UI events like feedback tap. |
| Firebase Crashlytics | Crash reporting. | `app/build.gradle.kts` | Captures runtime crashes. | Is crash reporting enabled? | Library is included; DSN in BuildConfig. |
| Retrofit | HTTP client abstraction. | `MealPlanRepository.kt` | Simplifies API calls. | Why Retrofit? | It generates type-safe API calls. |
| OkHttp | HTTP transport client. | `MealPlanRepository.kt` | Handles headers and timeouts. | How are headers added? | Interceptor injects schema and auth token. |
| HttpLoggingInterceptor | Request/response logging. | `MealPlanRepository.kt` | Debug network issues. | Is logging always on? | Only in debug builds. |
| Interceptor | OkHttp middleware. | `MealPlanRepository.kt` | Adds headers and retry logic. | Where is retry logic? | Custom interceptor retries non-success responses. |
| DTO | Data Transfer Object. | `PcosinaApiService.kt` | Matches API schema. | Why DTOs? | Keeps API models separate from UI models. |
| Gson | JSON serialization library. | `MealPlanRepository.kt` | Converts JSON to data classes. | Why Gson? | It works with Retrofit converter. |
| BuildConfig | Build-time constants. | `app/build.gradle.kts` | Stores BASE_URL and schema version. | Why BuildConfig? | Enables environment-specific URLs. |
| BASE_URL | Backend endpoint base. | `app/build.gradle.kts` | Connects app to backend. | Where is backend URL set? | In BuildConfig for debug and release. |
| Schema Version Header | API contract versioning. | `MealPlanRepository.kt`, `backend/main.py` | Detects client-server mismatch. | How do you handle schema mismatch? | Server replies with version header and client logs. |
| Authorization Header | Bearer token header. | `MealPlanRepository.kt`, `backend/main.py` | Secures protected endpoints. | Where is auth header added? | OkHttp interceptor adds Firebase token. |
| Result | Kotlin Result wrapper. | `MealPlanRepository.kt` | Standardizes error handling. | Why wrap in Result? | It separates success and failure cleanly. |
| Coroutine | Async execution model. | `AuthViewModel.kt`, `FeedbackRepository.kt` | Non-blocking operations. | How are async calls done? | Using `viewModelScope.launch`. |
| Dispatchers.IO | Coroutine dispatcher for IO. | `FeedbackRepository.kt` | Keeps network off main thread. | Why use IO dispatcher? | Avoids UI blocking during network calls. |
| Timeout | Network timeout settings. | `MealPlanRepository.kt` | Prevents hanging requests. | How do you handle slow network? | Timeouts plus retries in OkHttp. |

### Backend and API (61-80)
| Term | Definition | Where In PCOSINA | Why It Matters | Common Panel Question | Strong Sample Answer |
|---|---|---|---|---|---|
| FastAPI | Python web framework. | `backend/main.py` | Hosts optimizer API. | Why FastAPI? | It is fast and uses typed Pydantic models. |
| Uvicorn | ASGI server. | `backend/main.py` | Runs the API service. | How is the backend served? | Uvicorn runs the FastAPI app. |
| Pydantic | Data validation models. | `backend/domain/models.py` | Validates request payloads. | How do you validate API input? | Pydantic schemas enforce types. |
| Depends | FastAPI dependency injection. | `backend/main.py` | Injects auth and schema checks. | What is Depends used for? | Firebase auth and schema version validation. |
| Middleware | Request/response hooks. | `backend/main.py` | Adds headers and limits size. | What middleware is used? | Size limit and schema version header. |
| TrustedHostMiddleware | Host validation middleware. | `backend/main.py` | Controls allowed hosts. | Is it restricted? | Currently allows all for local testing. |
| OpenAPI Docs | Auto-generated API docs. | `backend/main.py` | API discoverability. | Do you have API docs? | Yes at `/docs` and `/openapi.json`. |
| JSONResponse | JSON response builder. | `backend/main.py` | Standard API output. | When is JSONResponse used? | For errors or data responses. |
| HTMLResponse | HTML response builder. | `backend/main.py` | Admin feedback UI. | Why HTML responses? | For simple admin feedback pages. |
| HTTP 401 | Unauthorized error. | `backend/main.py` | Auth enforcement. | When do you return 401? | Missing or invalid auth token. |
| HTTP 404 | Not found error. | `backend/main.py` | Missing recipes. | When do you return 404? | Recipe ID not found. |
| HTTP 409 | Conflict error. | `backend/main.py` | Schema mismatch. | When do you return 409? | Client schema version mismatch. |
| HTTP 413 | Payload too large. | `backend/main.py` | Prevents large requests. | Why 413? | Request size exceeds `MAX_REQUEST_BYTES`. |
| HTTP 422 | Unprocessable entity. | `backend/main.py` | Infeasible plan. | When do you return 422? | No feasible meal plan found. |
| HTTP 500 | Internal error. | `backend/main.py` | Unexpected server errors. | When do you return 500? | Unhandled exceptions in API logic. |
| Firebase Admin SDK | Server-side auth verification. | `backend/main.py` | Validates client tokens. | How are tokens verified? | `auth.verify_id_token` in Firebase Admin. |
| ID Token | Firebase identity token. | `backend/main.py` | Authenticates API requests. | What is sent to backend? | Bearer token from Firebase client. |
| Schema Contract | API schema description. | `backend/schema_contract.py` | Client-server alignment. | How do you expose schema? | `/schema` endpoint returns contract JSON. |
| Request Size Limit | Max payload size. | `backend/main.py` | Prevents abuse. | How do you limit size? | Middleware checks `Content-Length`. |
| Plan Cache | Cached plan responses. | `backend/main.py` | Avoids recomputation for identical inputs. | Do you cache plans? | Yes with TTL and max size. |

### Optimization and OR Terms (81-100)
| Term | Definition | Where In PCOSINA | Why It Matters | Common Panel Question | Strong Sample Answer |
|---|---|---|---|---|---|
| OR-Tools | Google optimization library. | `backend/services/meal_planner.py` | Provides solver engine. | Why OR-Tools? | Reliable CP-SAT solver for constraint problems. |
| CP-SAT | Constraint Programming SAT solver. | `meal_planner.py` | Solves plan selection problem. | Is it MILP or CP-SAT? | Implemented with CP-SAT boolean variables. |
| Boolean Decision Variable | 0/1 choice variable. | `meal_planner.py` | Selects recipe for a slot. | What does x[s,i] represent? | Whether recipe i is used in slot s. |
| Integer Variable | Bounded integer variable. | `meal_planner.py` | Tracks penalties and slack. | Why integer vars? | For overuse and deviation penalties. |
| Objective Function | Minimization target. | `meal_planner.py` | Balances nutrition, cost, diversity. | What is optimized? | Deviations plus penalties minus rewards. |
| Constraint | Rule that must hold. | `meal_planner.py` | Enforces one meal per slot. | Give a hard constraint. | Each slot selects exactly one recipe. |
| Hard Constraint | Must be satisfied. | `meal_planner.py` | Enforces slot selection and repeats. | Which constraints are hard? | Slot assignment and max repeats. |
| Soft Constraint | Allowed violation with penalty. | `meal_planner.py` | Diversity and budget allow slack. | Which constraints are soft? | Diversity and budget are penalized. |
| Penalty | Added cost for violations. | `meal_planner.py` | Guides solver tradeoffs. | Why penalties? | They allow feasible plans when strict rules fail. |
| Reward | Negative cost for desired traits. | `meal_planner.py` | Encourages pantry and diversity. | How do you promote pantry use? | Rewards pantry matches in the objective. |
| Tolerance Levels | Relaxation bounds for macros. | `meal_planner.py` | Improves feasibility. | Why multiple tolerances? | Wider bounds if strict bounds fail. |
| Feasible Solution | Satisfies all hard constraints. | `meal_planner.py` | Minimum acceptable result. | What if no feasible plan? | Return 422 or fallback if enabled. |
| Optimal Solution | Best objective value. | `meal_planner.py` | Produces best tradeoff plan. | Do you always get optimal? | It returns feasible or optimal within time. |
| Solver Time Limit | Max solve time. | `meal_planner.py` | Keeps response time bounded. | How do you limit solver time? | `max_time_in_seconds` parameter. |
| Search Workers | Parallel solver threads. | `meal_planner.py` | Speeds up solving. | How do you scale solver? | Set `num_search_workers` based on CPU. |
| AddHint | Warm start suggestion. | `meal_planner.py` | Guides solver to good solutions. | Why add hints? | Helps solver converge faster. |
| Diversity Constraint | Ensures ingredient variety. | `meal_planner.py` | Avoids repeated vegetables. | How do you enforce variety? | Soft constraints on veg token coverage. |
| Protein Group Limit | Soft cap by protein group. | `meal_planner.py` | Avoids too much of one protein. | How do you prevent monotony? | Penalties when group count exceeds limit. |
| Pantry Match | Count of matched pantry items. | `meal_planner.py` | Reduces waste. | How is pantry used? | Ingredient tokens matched and rewarded. |
| Budget Penalty | Cost overrun penalty. | `meal_planner.py` | Controls total spend. | How do you handle budget? | Penalize total cost beyond weekly budget. |
### Data, Deployment, Monitoring, Evaluation (101-126)
| Term | Definition | Where In PCOSINA | Why It Matters | Common Panel Question | Strong Sample Answer |
|---|---|---|---|---|---|
| SQLite | Local file-based database. | `backend/database.py` | Default backend storage. | What DB is used? | SQLite by default, Postgres if configured. |
| PostgreSQL | Server database option. | `backend/database.py` | Supports production deployment. | How do you switch DB? | Use `DATABASE_URL` with Postgres. |
| psycopg | Postgres driver. | `backend/database.py` | Enables Postgres support. | What driver is used? | Psycopg when Postgres is enabled. |
| recipes.json | Recipe dataset file. | `backend/recipes.json` | Seed for planning data. | Where does recipe data come from? | `recipes.json` seeded into DB. |
| Seeding | Loading data into DB. | `backend/database.py` | Ensures recipes exist. | How do you load recipes? | `seed_recipes()` on startup. |
| Schema Migration | Table alteration to add columns. | `backend/database.py` | Keeps DB compatible with schema. | How do you handle schema changes? | `ALTER TABLE` for missing columns. |
| Feedback Table | Stores user feedback. | `backend/database.py` | Captures user reports. | Where is feedback stored? | `feedback` table in backend DB. |
| Render Deployment | Hosting platform. | `render.yaml` | Production backend hosting. | Where is backend deployed? | Render via `render.yaml`. |
| Environment Variables | Runtime configuration. | `backend/main.py` | Controls auth, limits, DSN. | What config is env-driven? | Firebase, Sentry, request limits. |
| Sentry Backend | Error monitoring SDK. | `backend/main.py` | Captures server errors. | Is monitoring enabled? | Sentry is optional via `SENTRY_DSN`. |
| Sentry Android | Mobile error monitoring. | `app/build.gradle.kts` | Captures app crashes. | Is Sentry used on app? | Library is present; DSN is build config. |
| GitHub Actions CI | CI pipeline. | `.github/workflows/ci.yml` | Automates builds and checks. | Do you have CI? | Yes, Android build and backend smoke test. |
| assembleRelease | Android build task. | `.github/workflows/ci.yml` | Validates release build. | What does CI build? | `./gradlew assembleRelease`. |
| Backend Smoke Test | Dependency install check. | `.github/workflows/ci.yml` | Ensures backend deps install. | What backend test runs? | A dependency install smoke step. |
| ISO/IEC 25010 | Software quality model. | Chapter docs: `backend/output/doc/PCOSina Chapter 2.docx` | Evaluation framework. | What quality model is used? | ISO/IEC 25010 criteria. |
| Cronbach alpha | Reliability metric. | Chapter docs | Validates survey instrument. | Did you test reliability? | Cronbach alpha as reported in Chapter 3. |
| Weighted mean | Aggregated survey scoring. | Chapter docs | Summarizes evaluation results. | How are scores summarized? | Weighted mean per criterion. |
| Frequency and percentage | Descriptive stats. | Chapter docs | Summarizes respondent profiles. | What stats are used? | Frequency and percentage in analysis. |
| Decision-support | Guidance without treatment claim. | Thesis framing | Ensures safe positioning. | Is it a medical tool? | No, it is decision-support only. |
| Insulin resistance | Domain constraint driver. | Chapter docs | Shapes nutrition targets. | Why include insulin resistance? | It aligns nutrition targets to PCOS constraints. |
| BMR/TDEE | Metabolic target basis. | `HealthMetrics.kt`, `meal_planner.py` | Drives calorie targets. | How are calorie targets computed? | Mifflin-St Jeor with activity multiplier. |
| BMI | Body mass indicator. | `HealthMetrics.kt` | Baseline health context. | Do you compute BMI? | Yes, for display and context only. |
| API Contract | Client-server schema. | `PcosinaApiService.kt`, `backend/domain/models.py` | Prevents mismatch. | How is contract enforced? | Schema header and Pydantic validation. |
| Schema Versioning | Version check mechanism. | `MealPlanRepository.kt`, `backend/main.py` | Handles incompatible changes. | What happens on mismatch? | Server returns 409 or logs mismatch. |
| Admin Feedback Token | Admin page auth token. | `backend/main.py` | Secures feedback admin UI. | How is admin feedback protected? | Token check in headers or query. |
| DataStore Migration | Legacy email to UID data. | `UserPreferencesRepository.kt` | Preserves user data after auth change. | How do you migrate data? | Copy old email keys to UID keys. |

## 2) System-Specific Terminology Map

| Module | Key Terms | Core Files | Relationship Notes |
|---|---|---|---|
| Mobile UI | Compose, Composable, Scaffold, NavHost | `app/src/main/java/com/pcosina/app/ui/screens/*.kt`, `AppNavHost.kt` | UI observes ViewModel state and triggers actions. |
| ViewModel and State | MVVM, StateFlow, collectAsState | `app/src/main/java/com/pcosina/app/ui/*ViewModel.kt` | ViewModel mediates between UI and repositories. |
| Local Storage | DataStore, EncryptedSharedPreferences | `UserPreferencesRepository.kt`, `ReflectionStore.kt` | Offline-first data and encrypted reflections. |
| Network Layer | Retrofit, OkHttp, DTOs | `MealPlanRepository.kt`, `PcosinaApiService.kt` | Sends profile to backend and receives plan. |
| Backend API | FastAPI, Pydantic, endpoints | `backend/main.py`, `backend/domain/models.py` | Validates requests and returns plans. |
| Optimizer | CP-SAT, constraints, penalties | `backend/services/meal_planner.py` | Two-stage selection and optimization. |
| Ops and Quality | Render, Sentry, GitHub Actions | `render.yaml`, `backend/main.py`, `.github/workflows/ci.yml` | Monitoring and deployment discipline. |

Relationship highlights:
- Objective function and constraints drive feasibility and fallback in `backend/services/meal_planner.py`.
- Auth token generation in app and token verification in backend are `MealPlanRepository.kt` plus `backend/main.py`.
- Schema version header and contract mismatch handling are in `MealPlanRepository.kt` and `backend/main.py`.

## 3) MILP Mastery Section

### Beginner explanation
| Term | Beginner Explanation | Where To Point |
|---|---|---|
| Decision variable | A yes or no choice for each meal slot. | `meal_planner.py` variables `x[s,i]`. |
| Objective function | The score the solver tries to minimize. | `meal_planner.py` `model.Minimize`. |
| Hard constraint | A rule that cannot be broken. | One recipe per slot, repeat cap. |
| Soft constraint | A rule that can break with a penalty. | Diversity, budget, pantry. |
| Penalty | Extra cost added when rules are violated. | `budget_over`, `group_over`, `repeat_over`. |
| Reward | Negative cost for desired traits. | Pantry matches and veg coverage. |
| Tolerance | Acceptable range for nutrition targets. | `PCOSINA_TOLERANCE_LEVELS`. |
| Feasibility | All hard constraints satisfied. | `cp_model.FEASIBLE`. |
| Optimality | Best possible score found. | `cp_model.OPTIMAL`. |
| Solver timeout | Max time the solver can run. | `PCOSINA_SOLVER_TIME_SECONDS`. |
| Fallback heuristic | Simple plan if solver fails. | `_greedy_fallback_plan`. |
| Warm-start hint | Suggests good initial picks. | `model.AddHint`. |
| Diversity constraint | Encourages variety in ingredients. | `veg_cov` and `diversity_slack`. |
| Budget penalty | Penalizes cost beyond budget. | `budget_over` term. |

### Defense-level explanation
| Term | Defense-level Explanation | Where To Point |
|---|---|---|
| Decision variable | Binary variables `x[s,i]` encode selection of recipe i for slot s. | `meal_planner.py`. |
| Objective function | Minimize total deviation from calorie and macro targets plus penalties for repeats and cost, minus pantry and diversity rewards. | `model.Minimize` block. |
| Hard constraint | Slot assignment equalities and max-per-week ensure valid plan structure. | `model.Add(sum(...) == 1)`, `<= max_per_week`. |
| Soft constraint | Diversity and budget are modeled with slack variables to avoid infeasibility. | `diversity_slack`, `budget_over`. |
| Penalty weights | Weights scale priorities such as repeats and protein group balance. | `repeat_weight`, `group_weight`. |
| Tolerance relaxation | Iterates tolerances and repeat caps to widen feasible region when strict constraints fail. | `tolerance_levels`, `PCOSINA_MAX_PER_WEEK`. |
| Feasible vs optimal | Solver may return feasible when time-limited; both are accepted. | `cp_model.FEASIBLE` handling. |
| Timeout strategy | `total_time_limit` bounds overall solve to keep API responsive. | `PCOSINA_TOTAL_SOLVER_SECONDS`. |
| Fallback logic | Optional greedy plan used if solver is infeasible or timed out. | `_greedy_fallback_plan` and `PCOSINA_ALLOW_FALLBACK`. |
| Diversity constraints | Soft caps on protein groups plus vegetable token coverage for variety. | `group_over`, `veg_cov`. |
| Budget handling | Estimates per-recipe cost and penalizes weekly total overshoot. | `estimate_cost`, `budget_over`. |

## 4) If Panel Asks: Defense Simulation (50 questions)

| Question | Ideal concise answer | Extended expert answer | Wrong answer to avoid |
|---|---|---|---|
| Why use CP-SAT over greedy only? | It guarantees feasibility under constraints. | CP-SAT handles hard and soft constraints with penalties, unlike greedy which can violate targets. | Greedy is always enough. |
| Is this a machine learning model? | No, it is constraint optimization. | We use OR-Tools CP-SAT with binary variables and penalties, not ML. | Yes, MILP is machine learning. |
| What happens when no feasible plan exists? | The API returns 422. | We return 422 with infeasibility, or fallback if enabled. | It always finds a plan. |
| How do you handle offline use? | DataStore stores plans and logs locally. | Plans, grocery list, and journals persist locally via DataStore and encrypted prefs. | Offline is not supported. |
| Where is authentication enforced? | Backend verifies Firebase ID tokens. | App sends bearer token; backend verifies with Firebase Admin. | We just trust the client. |
| How do you version the API contract? | Header `X-PCOSINA-Schema-Version`. | Client sends header, server returns version and rejects mismatches. | No versioning. |
| Why DataStore instead of Room? | Simpler key-value and offline needs. | Profile and plan history are key-value; no complex queries. | Room is required for everything. |
| What is the main entry point in Android? | `MainActivity`. | `MainActivity` sets Compose content and navigation host. | Some random screen. |
| Where is navigation defined? | `AppNavHost.kt`. | All composables and routes are in NavHost. | Each screen handles itself. |
| Where are the endpoints defined? | `backend/main.py`. | `/generate-plan`, `/recipes/summary`, `/recipe/{id}` are in FastAPI. | In the app only. |
| How do you prevent repeated meals? | Max-per-week and adjacency constraints. | `max_per_week` and consecutive slot constraints reduce repetition. | We ignore repeats. |
| How is calorie target computed? | Mifflin-St Jeor formula. | Uses weight, height, age, activity, and goal adjustment. | We guess calories. |
| Where is this calculation? | `HealthMetrics.kt` and `meal_planner.py`. | App has BMR utilities; backend uses similar formula. | Only in UI. |
| How do you handle budget? | Penalize cost overrun. | Estimated cost per recipe summed and penalized beyond weekly budget. | We do not use budget. |
| What is the pantry feature? | Reward recipes that match pantry items. | Ingredient tokens matched against pantry and rewarded in objective. | Pantry is ignored. |
| How do you reduce food waste? | Pantry rewards and grocery consolidation. | Pantry match reward plus consolidated grocery list by categories. | We do not target waste. |
| How are recipes stored? | In backend DB. | `recipes.json` seeds SQLite or Postgres. | Only in the app. |
| Is the admin feedback protected? | Token required. | `ADMIN_FEEDBACK_TOKEN` needed in header or query. | No security on admin page. |
| What is the response model for plan? | `GeneratePlanResponse`. | Defined in `backend/domain/models.py` and API DTOs. | There is no model. |
| How do you ensure data integrity? | Pydantic validation and DataStore typing. | Backend validates payloads; app uses typed models. | We do not validate. |
| What monitoring exists? | Optional Sentry. | Backend can send errors to Sentry if DSN set. | No monitoring. |
| What is the CI pipeline? | GitHub Actions builds Android and installs backend deps. | `ci.yml` builds release APK and checks backend deps. | We have no CI. |
| How do you handle slow networks? | Timeouts and retries. | OkHttp retry interceptor plus timeouts in repository. | We do nothing. |
| Why use FastAPI? | Fast and typed. | Pydantic models and auto docs make it reliable. | Because it was easy. |
| What is an endpoint? | A URL path for an API action. | `generate-plan` is an endpoint. | It is a database table. |
| What is a DTO? | Data transfer model. | `PcosinaApiService.kt` DTOs map API schema. | It is a UI object. |
| How is state propagated to UI? | StateFlow and collectAsState. | ViewModel emits state and UI collects it. | We manually refresh UI. |
| What is offline-first? | Local data works without network. | DataStore caches plan and logs for offline use. | Offline-first means no backend. |
| Where is encryption applied? | ReflectionStore uses encrypted prefs. | AES keys in `ReflectionStore.kt`. | No encryption. |
| How do you handle schema mismatch? | Log and reject in backend. | Backend checks header and returns 409 if mismatch. | We ignore mismatches. |
| Why not pure heuristic? | Heuristic cannot guarantee constraints. | CP-SAT ensures constraints and balance tradeoffs. | Heuristic is always enough. |
| Is it explainable? | Yes, through constraints and scores. | Explanation includes targets, deviations, and confidence. | It is a black box. |
| How do you handle missing nutrients? | Use placeholders on seed. | Seeding logic fills missing values to keep planning possible. | We do nothing. |
| What is the plan cache? | Cache for identical requests. | `_plan_cache` with TTL and size limit. | No cache exists. |
| Why do you allow fallback? | To avoid total failure in demos. | Optional heuristic plan if MILP times out. | Fallback is always used. |
| How is auth session persisted? | DataStore in `AuthRepository`. | Session stored under `auth_prefs`. | We rely on memory only. |
| Where is grocery consolidation stored? | DataStore keys under user ID. | `UserPreferencesRepository.kt` grocery JSON. | Only on server. |
| What is the base URL? | Backend endpoint address. | BuildConfig `BASE_URL`. | It is hardcoded in code. |
| How do you handle profile migration? | Email to UID migration. | `migrateFromEmailIfNeeded` copies keys. | We delete old data. |
| What is a constraint? | A rule in solver. | Implemented via `model.Add`. | Any preference is a constraint. |
| What is a penalty weight? | Importance of a soft constraint. | `repeat_weight`, `group_weight`. | Penalty weight does not matter. |
| What is a plan explanation? | Metadata about targets and deviations. | `PlanExplanation` DTO and `_build_explanation`. | No explanation exists. |
| What is `UserProfile` used for? | Input to planner. | `app/data/model/UserProfile.kt`, `backend/domain/models.py`. | Just UI data. |
| What is role of `PlanInstance`? | Identifies plan history locally. | `app/src/main/java/com/pcosina/app/data/model/PlanInstance.kt`. | Unused. |
| What is the app main flow? | Login -> onboarding -> dashboard -> plan. | `AppNavHost.kt` guards flow. | Starts on dashboard always. |
| What are evaluation criteria? | ISO/IEC 25010 categories. | Chapter docs. | We did not evaluate. |
| What does 422 mean? | Valid request but no feasible plan. | Used for infeasible optimization. | Server crash. |
| What is the daily target? | Calories per day after goal adjustment. | `meal_planner.py` daily targets. | Fixed 2000 calories. |
| How is variety enforced? | Soft diversity and protein group caps. | `group_over`, `veg_cov`. | We pick random recipes. |

## 5) Misuse and Confusion Guardrail

| Confused Terms | Correct Usage In PCOSINA | Where To Point |
|---|---|---|
| Authentication vs Authorization | Auth is Firebase login; authorization is backend token check. | `AuthRepository.kt`, `backend/main.py`. |
| Model vs Schema | Model is code structure; schema is API contract. | `backend/domain/models.py`, `schema_contract.py`. |
| Endpoint vs Route | Endpoint is API path; route is app navigation path. | `backend/main.py`, `Routes.kt`. |
| Heuristic vs Optimization | Heuristic is fallback; optimization is CP-SAT solver. | `meal_planner.py`. |
| Validation vs Verification | Validation is Pydantic input checks; verification is Firebase token. | `backend/main.py`. |
| Offline-first vs Offline-only | Offline-first still uses backend when available. | `UserPreferencesRepository.kt`. |
| Cache vs Persistence | Cache is memory; persistence is DataStore. | `MealPlanRepository.kt`, `UserPreferencesRepository.kt`. |
| Hard vs Soft Constraint | Hard must hold; soft is penalized. | `meal_planner.py`. |
| Feasible vs Optimal | Feasible satisfies constraints; optimal is best score. | `meal_planner.py`. |
| Token vs Session | Token is bearer credential; session is local state. | `MealPlanRepository.kt`, `AuthRepository.kt`. |
| DTO vs Domain Model | DTO is API contract; domain model is app logic. | `PcosinaApiService.kt`, `data/model/*.kt`. |
| Logging vs Monitoring | Logging is local; monitoring is Sentry. | `MealPlanRepository.kt`, `backend/main.py`. |

## 6) Memory and Study Tools

### 6A) One-page terminology cheat sheet
- Compose stack: Compose, Composable, Material3, Scaffold, NavHost, Routes.
- State stack: MVVM, ViewModel, StateFlow, collectAsState, LaunchedEffect.
- Data stack: DataStore, EncryptedSharedPreferences, Session, Repository.
- Network stack: Retrofit, OkHttp, DTO, schema header, auth header.
- Backend stack: FastAPI, Pydantic, endpoints, 422, 409.
- Optimizer stack: CP-SAT, decision variable, constraint, objective, penalty, tolerance, fallback.
- Ops stack: Render, Sentry, GitHub Actions.

### 6B) Flashcards (Q/A)
- Q: What is CP-SAT? A: OR-Tools solver used for constraint optimization.
- Q: What is `x[s,i]`? A: Binary decision variable for recipe i in slot s.
- Q: What is a hard constraint? A: A rule that must always be satisfied.
- Q: What is a soft constraint? A: A rule allowed to break with penalty.
- Q: Why DataStore? A: Offline-first key-value persistence.
- Q: Where is navigation defined? A: `AppNavHost.kt`.
- Q: What does 422 mean here? A: Infeasible plan.
- Q: How is auth enforced? A: Firebase ID tokens verified on backend.
- Q: What is the plan cache? A: TTL cache for identical plan requests.
- Q: What is the fallback? A: Greedy heuristic plan if solver fails.
- Q: What is schema versioning? A: Header check for API compatibility.
- Q: Where are recipes stored? A: `recipes.json` seeded into DB.
- Q: What is a DTO? A: API data class for network calls.
- Q: What is offline-first? A: Local data works without network.
- Q: What are diversity constraints? A: Soft limits to avoid repeats.
- Q: What is a pantry reward? A: Objective reward for pantry matches.
- Q: What is the goal adjustment? A: Calorie adjustment for weight loss.
- Q: Where are reflections stored? A: EncryptedSharedPreferences.
- Q: What does `GeneratePlanResponse` contain? A: Week label, days, status, explanation.
- Q: What is `UserProfile` used for? A: Planner input and targets.

### 6C) 7-day mastery plan
- Day 1: Read `AppNavHost.kt` and all screen files for app flow.
- Day 2: Read `AuthRepository.kt`, `UserPreferencesRepository.kt`, `ReflectionStore.kt`.
- Day 3: Read `MealPlanRepository.kt` and `PcosinaApiService.kt` for API flow.
- Day 4: Read `backend/main.py` for endpoints, auth, cache.
- Day 5: Read `meal_planner.py` and map constraints to terms.
- Day 6: Read `backend/database.py` and dataset seeding.
- Day 7: Rehearse Q and A and review the cheat sheet.

### 6D) 15-minute pre-defense quick review script
- Minute 1-3: Explain app flow from Splash to Dashboard using `AppNavHost.kt`.
- Minute 3-5: Explain profile storage and offline-first DataStore.
- Minute 5-7: Explain API flow: Retrofit to FastAPI endpoints.
- Minute 7-10: Explain CP-SAT variables, constraints, objective, and fallback.
- Minute 10-12: Explain security: Firebase tokens and schema versioning.
- Minute 12-15: Review evaluation terms and decision-support boundary.

## Top 25 must-memorize terms before defense
1. CP-SAT
2. Decision variable
3. Objective function
4. Hard constraint
5. Soft constraint
6. Feasible solution
7. Fallback heuristic
8. Schema versioning
9. Firebase ID token
10. Retrofit
11. OkHttp interceptor
12. DataStore
13. EncryptedSharedPreferences
14. ViewModel
15. StateFlow
16. NavHost
17. Routes
18. FastAPI
19. Pydantic
20. HTTP 422
21. `GeneratePlanResponse`
22. Pantry reward
23. Diversity constraint
24. Max-per-week repeat cap
25. Offline-first
