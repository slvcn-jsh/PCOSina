# PCOSINA Lead Developer/System Responsibility Package

Date prepared: 2026-05-11  
Source branch inspected: `ui/layout-refine`  
Scope: read-only codebase audit plus thesis, validation, and defense support material.  
Important boundary: the manual test cases are already completed and are not regenerated here. Attach the completed test case document separately.

## 1. Executive Summary

PCOSINA is an Android wellness decision-support application for Filipino PCOS meal planning. The current implementation is strongest as an authenticated, local-first mobile app that stores user profile data, pantry data, meal plans, grocery data, progress logs, weekly reflections, and notification preferences locally, while using Firebase and a remote backend for authentication, profile cloud sync, recipe details, meal plan generation, meal swaps, feedback, and admin/ops tooling.

The Android app uses Jetpack Compose screens, route guardrails, ViewModels, local repositories, DataStore-backed persistence, Firebase Auth/Firestore, Retrofit APIs, and WorkManager-based reminders. The authoritative meal planning algorithm is not inside the Android app. Android prepares the profile and calls the backend planner. Backend evidence shows a two-stage process: Stage 1 deterministic filtering/ranking with optional ML scoring, then Stage 2 OR-Tools CP-SAT constrained optimization in `backend/services/meal_planner.py`.

Safe high-level claim: PCOSINA is an offline-first Android decision-support system that stores and reuses core user data locally after login, and generates meal plans through a backend constrained optimizer. Avoid claiming fully offline meal generation, clinical treatment, diagnosis, guaranteed PCOS improvement, or that all features are fully validated unless the validation team has executed and signed the evidence.

## 2. Official System Version

| Item | Current Evidence |
|---|---|
| Repository name | `PCOSINA` in `settings.gradle.kts` via `rootProject.name = "PCOSINA"` |
| Current branch | `ui/layout-refine` |
| Expected validation branch | `ui/layout-refine` |
| Latest commit hash | `d7c2842ac987bea1eef531ea969d30e3aa10ad8b` |
| Short commit hash | `d7c2842` |
| Upstream status | `0 0` ahead/behind against upstream, so local HEAD matches `origin/ui/layout-refine` |
| Android namespace | `com.pcosina.app` in `app/build.gradle.kts` |
| Application ID | `com.pcosina.app` in `app/build.gradle.kts` |
| versionCode | `39` in `app/build.gradle.kts` |
| versionName | `1.10.3` in `app/build.gradle.kts` |
| compileSdk / minSdk / targetSdk | `36` / `24` / `36` in `app/build.gradle.kts` |
| API base URL | `https://pcosina-backend.onrender.com/` in `app/build.gradle.kts` |
| Schema version | `1.5.0` in `app/build.gradle.kts` |
| Existing APK artifacts found | `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Build status in this audit | Not executed in this session. Existing APK files were found, but freshness against current uncommitted workspace is not proven. |
| Completed manual test case artifacts | `output/doc/PCOSINA_CSIT_Expert_Manual_Test_Cases_UPDATED_AsExpected.docx` and `output/doc/PCOSINA_CSIT_Expert_Manual_Test_Cases.docx` |

Important warning: the active branch is correct, but the working tree has uncommitted and untracked changes in UI files, navigation files, image assets, and tests. For official validation, use one of these wordings:

- "The official validation build is PCOSINA Android version 1.10.3, versionCode 39, from branch `ui/layout-refine`, commit `d7c2842`, with the recorded validation build artifact attached."
- If using the dirty local workspace: "The validation build was prepared from branch `ui/layout-refine`, commit `d7c2842`, with additional uncommitted UI/layout and asset changes recorded in the validation evidence log."

Recommended action: before CS/IT expert validation, create a clean validation tag or record the exact APK hash, branch, commit, and dirty-worktree file list.

## 3. System Implementation Audit

| Module | Status | Evidence in Files/Classes/Functions | What It Does Now | Missing or Risky | Safe to Claim |
|---|---|---|---|---|---|
| Login/authentication | Complete with testing fallback | `LoginScreen`, `AuthViewModel.onGoogleLogin`, `AuthRepository.loginWithGoogle`, `AuthRepository.login`, `AuthRepository.logout` | Primary UI offers `Continue with Google`. Email/password access is still present through `Use email access` and useful for emulator/testing. Firebase Auth is used. | Google auth requires internet and configured Google/Firebase client. Email flow should be described as testing/support access if not part of final official user process. | Yes, as "Google sign-in primary, email access retained for testing/support." |
| Sign up | Partial/testing support | `SignUpScreen`, `AuthViewModel.onSignUp`, `AuthRepository.signUp`, `sendEmailVerification` | Email/password registration exists and sends verification email. | Official current auth direction is Google sign-in; avoid overemphasizing email sign-up as the main production flow. | Safe only as "email sign-up exists for testing/support." |
| User profile setup | Complete | `UserProfileScreen`, `StepOneIdentity`, `StepTwoMedical`, `StepThreeDiet`, `UserViewModel.updatePersonalDetails`, `updatePcosDetails`, `updateDietaryRestrictions` | Captures name, age, height, weight, activity, insulin resistance level, symptoms, restrictions, allergies, budget, household size, cooking time, variety, priority, and pantry. | Profile data accuracy depends on user input. | Yes. |
| Profile edit/settings | Complete | `Routes.UserProfileEdit`, `AppNavHost`, `SettingsScreen`, `UserProfileScreen` reuse | Existing profile can be edited from settings. | Account management beyond sign-out/clear history is limited. | Yes, as profile editing/settings. |
| Goal selection | Complete | `GoalSelectionScreen`, `parseGoalOptions`, `goalTextFromOptions`, `UserViewModel.updateGoal` | Captures Weight Loss, PCOS Symptom Management, and General Health combinations and saves them. | Goals steer planning but do not prove clinical effect. | Yes, as preference/personalization input. |
| Preferences | Complete for data capture, partial for clinical personalization | `UserProfileScreen.StepThreeDiet`, `UserViewModel.updateCookingPreferences`, `updatePlanningPriority`, backend `stage1_recipe_adjustments`, `symptom_adjustments` | Captures food rules and planning style; backend uses profile, symptoms, restrictions, and priority. | Not a clinical treatment model. Recipe coverage limits personalization quality. | Yes with cautious wording. |
| Budget | Complete for planner cap, partial for real-world pricing | `UserProfileScreen`, `UserViewModel.updateBudget`, backend `resolve_budget_weekly`, `model.Add(total_cost <= int(budget_weekly))`, `GroceryAggregation`, `GroceryRebuildUseCase`, `PriceCatalog.estimatePriceDetail` | Weekly budget is captured and backend can enforce hard cap. Grocery cost is estimated. | Price estimates may not match actual local store prices. | Yes as budget-aware planning with estimated costs. |
| Pantry/grocery data | Partial | `UserViewModel.updatePantryEntries`, `UserPreferencesRepository.savePantryEntries`, `GroceryRefinedScreen`, backend `normalize_pantry` | Pantry items are stored locally and used for matching grocery/meal planning signals. | Pantry quantity/expiration depth is limited; pantry feasibility is only as strong as user-entered data. | Yes as pantry-aware, not automatic inventory tracking. |
| Meal plan generation | Complete backend-assisted, not fully offline | Android `MealPlanViewModel.generateMealPlan`, `MealPlanRepository.generatePlan`, `PcosinaApiService.generatePlanAsync`; backend `generate_plan_async`, `solve_meal_plan` | Android sends profile to backend async planner, polls job result, saves plan locally. | Requires auth, App Check, and internet. Sync fallback endpoint has a Sunday-only guard in backend `generate_plan`; async path is primary. | Yes as backend-generated constrained plan. Do not claim local offline generation. |
| Meal plan viewing | Complete | `MealPlanRefinedScreen`, `MealPlanViewModel.loadSavedPlan`, `UserPreferencesRepository.getSavedPlanJson`, `savePlanJson` | Shows current/saved plan, Today plan, week plan, and plan history from local storage. | If no saved plan exists, user must generate online first. | Yes. |
| Recipe details | Partial | `RecipeDetailsScreen`, `MealPlanViewModel.loadRecipeDetails`, `MealPlanRepository.getRecipeDetails`, `PcosinaApiService.getRecipe` | Displays nutrition, ingredients, steps, add-to-grocery action, and logging entry points. | Details are fetched from backend and cached in memory only; full offline recipe detail availability is not guaranteed. | Yes as online recipe detail viewing with memory cache. |
| Meal swap/alternatives | Partial | `MealSwapDialog`, `MealPlanViewModel.getSwapOptions`, `MealPlanViewModel.swapMeal`, backend `build_swap_candidates`, `/recipes/swap-options` | Users can request alternatives and apply a meal swap; plan override is saved locally. | Swap options require internet. Hard-rule equivalence should be validated by experts. | Safe as partial, backend-assisted swap. |
| Grocery list generation/sync | Partial/implemented | `MealPlanViewModel.extractGrocerySourcesForPlan`, `GroceryViewModel.setPlanSources`, `GroceryAggregation.buildGroceryListEntries`, `GroceryLocalRepository` | Extracts ingredients from plan recipe details, aggregates them, scales by household size, estimates category and cost, and saves grocery data locally. | Requires recipe detail fetch for complete source extraction. Price estimates are approximate. | Yes as generated grocery list with estimated cost. |
| Grocery item purchased tracking | Partial | `GroceryRefinedScreen.checkedNames`, `pantryOptOut`, `effectiveChecked`, item labels `Bought`, `In Pantry`, `To Buy` | UI lets users mark items bought or covered by pantry. | `checkedNames` is `rememberSaveable(activePlanId)` and is not clearly persisted in repository, so durable purchased tracking after reinstall/relaunch is risky. | Safe only as UI purchased tracking; persistence needs validation. |
| Progress tracking | Complete as local wellness tracking | `ProgressRefinedScreen`, `ProgressViewModel.dailyLogs`, `saveReflection`, `setWeight`, `ReflectionStore` | Tracks today's reflection, weight entry, meal completion, mood/energy/craving/symptom notes, progress summaries. | Not clinical outcome measurement. | Yes as wellness/progress tracking. |
| Meal logging/check-in | Complete for today-only logging, partial sequence risk | `MealLoggingPolicyUseCase.evaluate`, `loggingLockReason`, `sequenceLockReason`, `ProgressViewModel.toggleMeal`, `markMealAsEaten`, `saveMealCheckIn` | Meal completion is only allowed for today and enforces breakfast/lunch/dinner sequence when completion functions are used. | `saveMealCheckIn` checks date but does not itself call `MealLoggingPolicyUseCase`; sequence safety depends on calling flow. | Claim today-only logging and sequence-guarded completion, with note. |
| Meal logging constraints | Partial/implemented | `MealLoggingPolicyUseCase`, `ProgressViewModel.LoggingPolicySummary` | Past/future days are read-only. Lunch requires breakfast if breakfast is planned; dinner requires prior planned meals. | Not based on real clock time of breakfast/lunch/dinner. It is date-based and sequence-based. | Yes, if stated exactly. |
| Weekly journal/reflection | Complete | `ProgressRefinedScreen`, `ProgressViewModel.saveWeeklyJournal`, `saveWeeklySpend`, `ReflectionStore.saveWeeklyJournal`, `saveWeeklySpend` | Saves weekly notes and actual spend locally. | Free-text quality depends on user. | Yes. |
| BMI/health computation | Complete but non-diagnostic | `HealthMetrics.bmi`, `HealthMetrics.bmiCategory`, `ProgressRefinedScreen` | Computes BMI and category from profile height/weight. | BMI is not diagnosis; category interpretation should be validated by health experts. | Yes with disclaimer. |
| Macro/calorie computation | Partial/implemented | Android `HealthMetrics.targetCaloriesPerDay`; backend `macro_ratios`, `activity_multiplier`, `target`, protein/carb/fat bounds in `solve_meal_plan` | Computes calorie targets, BMR/TDEE in app, and backend macro targets/constraints for planning. | Recipe nutrition data and formula assumptions need validation. | Yes as computation needing expert/statistician review. |
| Offline/local storage | Partial/strong local persistence | `UserPreferencesRepository`, `Context.dataStore`, `SecureArtifacts`, `ReflectionStore`, local repository adapters | Stores profile, plan, plan history, pantry, grocery, daily logs, weekly journals/spend, notification preferences locally. | Initial login and new plan generation require internet; recipe detail/swap/feedback require backend. | Yes as local-first/offline access to saved data, not fully offline system. |
| Cloud sync/Firebase/Auth | Complete/implemented | `AuthRepository`, `FirebaseAuth`, `GoogleAuthProvider`, `FirebaseFirestore`, `UserPreferencesRepository.syncProfileWithCloud`, `syncProfileToCloud`, backend `require_firebase_auth` | Firebase Auth handles identity; Firestore sync backs up profile/artifacts; backend endpoints require Firebase auth and App Check. | Internet required. Cloud sync failure is skipped/retried gracefully but should be demoed carefully. | Yes. |
| Notifications/reminders | Complete but requires OS validation | `NotificationPreferences`, `SettingsScreen`, `NotificationScheduler.rescheduleAll`, `NotificationRescheduleReceiver`, `AndroidManifest.xml` permissions | Supports master reminders, meal reminders, weekly reset, plan/grocery alerts, streak/inactivity nudges, quiet hours, boot/time reschedule. | Actual delivery depends on Android permission, OEM background limits, and device state. | Yes as implemented reminders pending device validation. |
| Feedback/support | Complete/partial online | `CommunityScreen`, `MoreToolsScreen`, `FeedbackRepository.sendFeedback`, backend `/feedback`, `/admin/feedback` | Support screen, email feedback intent, and backend feedback endpoint exist. | Requires network or email app. Not a live chat/community platform. | Yes as support/feedback channel. |
| Admin/methodology/tools | Partial | `MoreToolsScreen`, `IpoVisualizationScreen`, `AppNavHost` operator guard, backend admin routes in `backend/main.py` | Mobile shows methodology/support tools and operator-only areas; backend has admin/ops/feedback/recipe/policy consoles. | Not ordinary end-user feature. Admin UX is mostly backend/browser. | Safe as internal support/admin tooling. |
| Navigation guardrails/route locking | Complete | `Routes.requiresPlan`, `RouteAccess.PlanRequired`, `AppNavHost` guards, `BottomNavBar` enabled routes | Locks Grocery/Progress/Recipe details until a plan exists; routes through profile and goal setup. | Guardrails should be regression-tested on actual build. | Yes. |
| Error handling/loading states | Complete/partial | `MealPlanUiState`, `RecipeDetailsUiState`, `MealPlanGenerationNotice.NoSafePlan`, `MealPlanRepository.mapGeneratePlanException`, backend `_build_no_safe_plan_response` | Handles loading, backend errors, no-safe-plan guidance, retry/fallback, and auth errors. | Some failures depend on backend messages and connectivity. | Yes as implemented error states. |

## 4. Thesis Alignment Matrix

| Thesis/System Claim | Evidence in Codebase | Current Status | Safe Paper Wording | Risk Level | Recommended Action |
|---|---|---|---|---|---|
| Offline-first architecture | `UserPreferencesRepository`, `DataStore`, `SecureArtifacts`, `ReflectionStore` | Supported for saved data | "PCOSINA uses local-first storage for profile, pantry, saved plans, groceries, logs, and reminder preferences." | Medium | Do not say all features work offline. |
| Two-stage meal planning | Backend `shortlist_candidates` and `solve_meal_plan` | Supported in backend | "The backend planner first filters/ranks candidates, then uses OR-Tools CP-SAT to select the week plan." | Low | Cite backend, not Android, as algorithm authority. |
| Constraint-based filtering | `restriction_failure_reasons`, `passes_restrictions`, `validate_profile`, `shortlist_candidates` | Supported | "Hard food rules and feasibility checks are applied before final solver selection." | Low | Include examples: allergy, restrictions, prep time, budget. |
| MILP/optimization | `backend/services/meal_planner.py` imports `cp_model`, creates `CpModel`, uses `CpSolver`, `model.Minimize` | Supported backend-side | "The optimizer is implemented backend-side using OR-Tools CP-SAT constrained optimization." | Medium | Avoid saying Android app implements MILP locally. |
| Personalization based on profile/preferences | Android profile screens plus backend `symptom_adjustments`, `macro_ratios`, `priority_overrides` | Supported/partial | "Meal planning is personalized using declared profile, goal, symptoms, restrictions, budget, household, and planning preferences." | Medium | Validate with sample profiles. |
| Pantry-constrained planning | Android pantry persistence, backend `normalize_pantry`, pantry reward/min slack in solver | Partial | "Pantry items influence candidate ranking and plan/grocery matching." | Medium | Avoid saying pantry stock is automatically verified. |
| Grocery list generation | `extractGrocerySourcesForPlan`, `GroceryViewModel.setPlanSources`, `buildGroceryListEntries` | Supported/partial | "The app can derive grocery items from the generated plan and aggregate them for shopping." | Medium | Validate with sample plan and recipe details. |
| Budget awareness | `weeklyBudgetPhp`, backend `model.Add(total_cost <= int(budget_weekly))`, grocery price estimates | Supported | "Budget is treated as a planning constraint when provided, using estimated costs." | Medium | State price estimates, not guaranteed market prices. |
| Progress tracking | `ProgressRefinedScreen`, `ProgressViewModel`, `ReflectionStore` | Supported | "The app provides local wellness progress tracking and weekly reflection." | Low | Do not claim clinical effectiveness. |
| Meal logging/check-in | `MealLoggingPolicyUseCase`, `toggleMeal`, `markMealAsEaten`, `saveMealCheckIn` | Supported/partial | "Meal completion is date-locked to today and sequence-guarded for planned meals." | Medium | Validate flow from UI to ViewModel. |
| PCOS dietary support | Profile symptoms/goals, backend insulin macro ratios and fiber/sugar targets | Partial/supportive | "PCOSINA supports PCOS-aware meal planning inputs and dietary guidance, subject to expert review." | High | Health experts must validate wording and sample plans. |
| User feedback | `CommunityScreen`, `FeedbackRepository`, backend `/feedback` and `/admin/feedback` | Supported | "Users can submit feedback through support channels." | Low | Validate endpoint/email behavior. |
| Expert validation | Existing completed test cases in `output/doc`; this package | Process artifact | "The system was prepared for CS/IT expert validation using manual test cases and system evidence documents." | Medium | Do not report validation results until signed. |
| Computation precision testing | `HealthMetricsTest`, `GroceryAggregationTest`, backend planner tests | Partially supported | "Computations have unit-test evidence and require statistician confirmation for thesis reporting." | Medium | Prepare manual-vs-system computation sample table. |
| ISO 25010 evaluation | Test suite names and validation materials | Process, not code feature | "ISO 25010 characteristics are used as the evaluation framework." | Medium | Use expert Likert form and signed validation. |

## 5. Architecture Explanation

### A. Detailed Explanation for Paper/Documentation

PCOSINA uses a mobile-client plus backend architecture. The Android client is implemented with Jetpack Compose UI screens under `app/src/main/java/com/pcosina/app/ui/screens`. Navigation is centralized through `AppNavHost` and route definitions in `Routes`. Bottom navigation is configured by `DefaultBottomNavItems`.

User identity is handled by Firebase Auth through `AuthRepository`. The current primary user-facing authentication path is Google sign-in through `LoginScreen` and `AuthViewModel.onGoogleLogin`. Email/password sign-in, sign-up, verification, and password reset still exist in code and are useful for emulator/testing and operator/support access.

App state is separated into ViewModels and repositories. `UserViewModel` holds profile and pantry state. `MealPlanViewModel` handles plan generation, saved plans, recipe details, swaps, and grocery source extraction. `GroceryViewModel` manages grocery data and plan ingredient sources. `ProgressViewModel` manages daily logs, meal completion, check-ins, weekly notes, and spending. These ViewModels use local repository adapters backed mainly by `UserPreferencesRepository` and `ReflectionStore`.

Local storage is a major part of the architecture. `UserPreferencesRepository` uses Android DataStore and secure artifact helpers for profile, pantry, saved plan, plan history, grocery state, grocery sources, grocery snapshots, feedback queue, notification preferences, and UI preferences. `ReflectionStore` stores daily logs, weekly journals, and weekly spending in local shared preferences with recovery behavior.

Cloud and online responsibilities are separate from local display responsibilities. Firebase Auth provides the user session. Firestore sync in `UserPreferencesRepository.syncProfileWithCloud` and `syncProfileToCloud` backs up profile and artifact data for reinstall/new-device recovery. Meal plan generation, recipe details, meal swaps, and feedback use the backend configured by `BuildConfig.BASE_URL`, currently `https://pcosina-backend.onrender.com/`.

The backend exposes `/generate-plan-async`, `/plan-jobs/{job_id}`, `/generate-plan`, `/recipe/{recipe_id}`, `/recipes/summary`, `/recipes/swap-options`, `/feedback`, and admin/ops endpoints in `backend/main.py`. Android primarily calls `generatePlanAsync`, polls the plan job, and saves the result locally. The backend planner in `backend/services/meal_planner.py` performs the actual constrained meal selection.

Data flow:

1. The user signs in through Firebase Auth.
2. The app loads or restores the local profile through `UserPreferencesRepository`; Firestore sync may run after login.
3. The user completes profile setup and goal selection.
4. `MealPlanViewModel.generateMealPlan` prepares the profile using `PlannerProfilePreparationUseCase`.
5. `MealPlanRepository.generatePlan` sends the profile to the backend async planner.
6. The backend filters/ranks recipe candidates and uses OR-Tools CP-SAT to build the plan.
7. Android receives and saves the plan locally through `savePlanJson` and plan history storage.
8. The meal plan screen and dashboard display the saved plan.
9. Grocery sources are extracted from recipe details and stored through `GroceryViewModel`.
10. Progress, meal logging, and weekly reflection are saved locally through `ProgressViewModel` and `ReflectionStore`.

### B. Short Explanation for Defense PowerPoint

PCOSINA is a local-first Android app with a remote optimizer backend. The mobile app handles sign-in, profile setup, goals, meal plan display, grocery list, reminders, and progress logs. Local storage keeps the user's saved data available after login. The backend handles heavy meal planning: it filters safe recipe candidates, then uses OR-Tools CP-SAT optimization to generate the weekly plan. The app is not a medical device and does not diagnose or treat PCOS; it supports PCOS-aware meal planning decisions.

## 6. Algorithm and Logic Explanation

### Meal Planning Process

The Android app does not perform the full optimizer locally. In `MealPlanViewModel.generateMealPlan`, the profile is prepared and passed to `MealPlanRepository.generatePlan`. `MealPlanRepository` calls `PcosinaApiService.generatePlanAsync`, polls `getPlanJob`, and falls back to `generatePlan` only for specific backend fallback conditions.

Backend planning is implemented in `backend/services/meal_planner.py`:

- `validate_profile` rejects conflicting or impossible profile inputs.
- `shortlist_candidates` performs Stage 1 candidate filtering/ranking.
- `restriction_failure_reasons` and `passes_restrictions` enforce dietary restrictions and allergies.
- `resolve_budget_weekly` resolves budget input.
- `macro_ratios`, `activity_multiplier`, `symptom_adjustments`, and `priority_overrides` adjust targets.
- `solve_meal_plan` builds the final constrained optimization model.
- OR-Tools evidence: `from ortools.sat.python import cp_model`, `cp_model.CpModel()`, `model.Minimize(...)`, `cp_model.CpSolver()`.

### Represented Constraints and Signals

Implemented constraints/signals include:

- Allergy and dietary restriction filtering.
- Cooking-time filtering.
- Meal-type assignment constraints.
- One recipe selection per meal slot.
- Adjacent duplicate prevention.
- Weekly repeat limit.
- Budget hard cap when weekly budget exists.
- Daily calorie target deviation.
- Protein, carbohydrate, and fat bounds.
- Fiber minimum slack.
- Sodium and sugar overage penalties.
- Meal distribution targets.
- Pantry usage reward and pantry minimum slack.
- Diversity and repeat penalties.
- Planning priority weights such as budget-first, variety-first, and nutrition-focused behavior.

### MILP/Optimization Status

The code uses OR-Tools CP-SAT backend optimization. Thesis wording may call this deterministic constrained optimization. If the paper specifically says "MILP", clarify that the production implementation evidence is backend OR-Tools CP-SAT, which is a constrained optimization solver implementation. Do not claim that the Android app itself contains MILP.

### ML Role

ML is optional and assistive. `Stage1MLRanker` in `backend/services/ml_ranker.py` can load LightGBM artifacts and score Stage 1 candidates. If unavailable, the backend falls back to deterministic shadow/heuristic scoring. `ml/feature_definitions/v1.md` states fallback behavior. Final selection remains controlled by the solver path in `solve_meal_plan`.

### Meal Swaps

Meal swaps are backend-assisted. Android opens `MealSwapDialog`, calls `MealPlanViewModel.getSwapOptions`, which uses `MealPlanRepository.getSwapOptions` and backend `/recipes/swap-options`. Backend `build_swap_candidates` shortlists valid alternatives for the same meal label. Applying a swap uses `MealPlanViewModel.swapMeal`, updates the local saved plan, and can update grocery sources.

### Grocery Extraction

Grocery sources are extracted from recipe details using `MealPlanViewModel.extractGrocerySourcesForPlan` and `getGrocerySourcesForRecipe`. `GroceryViewModel.setPlanSources` stores plan ingredient sources. `GroceryAggregation.buildGroceryListEntries` aggregates names, quantities, household scaling, category inference, and cost estimates.

### Progress and Meal Logging Logic

`ProgressViewModel` stores daily logs in local state and persists through `ReflectionStore`. It supports:

- `toggleMeal` for meal completion toggle.
- `markMealAsEaten` for recipe-detail completion.
- `saveMealCheckIn` for energy/fullness/cravings/satisfaction/note.
- `setWeight` for daily weight.
- `saveReflection` for daily wellness reflection.
- `saveWeeklyJournal` and `saveWeeklySpend` for weekly review.

Meal logging policy:

- It is date-based, not actual clock-time based.
- `MealLoggingPolicyUseCase.isDateLoggable` returns true only when the selected date equals `LocalDate.now()`.
- `loggingLockReason` blocks past days and future days.
- `sequenceLockReason` requires planned earlier meal slots before later slots. Lunch is blocked until breakfast is logged if breakfast is part of the planned labels. Dinner is blocked until earlier planned slots are logged.
- `ProgressViewModel.toggleMeal` and `markMealAsEaten` call the policy before logging a new meal.
- `saveMealCheckIn` checks that the date is today but does not itself call `MealLoggingPolicyUseCase`; it should be used after or alongside the meal completion flow.

Safe defense wording: "Meal logging is limited to today and follows the planned sequence of meals. It is not based on the exact time of day; it prevents users from skipping ahead in the planned meal sequence."

## 7. Offline-First Evidence

### A. Offline/Online Matrix

| Capability | Works Offline After Login/Data Exists? | Requires Internet? | Evidence | Notes |
|---|---:|---:|---|---|
| Open app to saved local state | Partial/Yes | No for saved data | `UserPreferencesRepository`, `ReflectionStore` | Depends on prior login/session and stored data. |
| Google sign-in | No | Yes | `LoginScreen`, `AuthRepository.loginWithGoogle`, Firebase Auth | Internet required. |
| Email sign-in/sign-up/reset | No | Yes | `AuthRepository.login`, `signUp`, `sendPasswordReset` | Testing/support access, not current primary flow. |
| Profile viewing/editing | Yes | Sync optional | `UserViewModel`, `UserPreferencesRepository.updateProfile` | Local write first; Firestore sync attempted. |
| Goal/preferences/budget/pantry | Yes | Sync optional | `UserViewModel`, `savePantryEntries` | Local source of truth for app display. |
| Saved meal plan viewing | Yes | No | `getSavedPlanJson`, `MealPlanViewModel.loadSavedPlan` | Requires plan to have been generated and saved before. |
| New meal plan generation | No | Yes | `MealPlanRepository.generatePlan`, backend `/generate-plan-async` | Backend optimizer required. |
| Recipe details | Partial | Usually yes | `getRecipeDetails`, `recipeCache` | In-memory cache only; not guaranteed after restart. |
| Meal swaps | No | Yes | `getSwapOptions`, backend `/recipes/swap-options` | App explicitly shows internet-required message. |
| Grocery list display | Yes if saved | No for saved list | `GroceryLocalRepository`, `getGroceryJson` | Full regeneration may need recipe details. |
| Pantry editing | Yes | Sync optional | `updatePantryEntries`, `savePantryEntries` | Local persistence. |
| Purchased/Bought UI state | Partial | No | `GroceryRefinedScreen.checkedNames` | UI-saveable only; durable persistence needs validation. |
| Progress logs/check-ins | Yes | No | `ProgressViewModel`, `ReflectionStore` | Local. |
| Weekly journal/spend | Yes | No | `saveWeeklyJournal`, `saveWeeklySpend` | Local. |
| Notifications/reminders | Yes, device-dependent | No for scheduled local reminders | `NotificationScheduler`, WorkManager | Requires notification permission and OS scheduling. |
| Feedback submission | No | Yes | `FeedbackRepository.sendFeedback`, backend `/feedback`, email intent | Needs network or email app. |
| Cloud restore/sync | No | Yes | Firestore sync methods | Used for reinstall/new-device recovery. |

### B. Defense-Ready Answer

"Offline-first in PCOSINA means the app stores the user's important working data locally after authentication: profile, goals, pantry, saved meal plan, grocery list, progress logs, weekly reflections, and reminder preferences. The user can still view and update much of this saved data without internet. However, authentication, cloud restore, new meal plan generation, recipe detail retrieval, meal swaps, and feedback submission still require internet because they depend on Firebase or the backend optimizer."

### C. Risk Notes

- Do not say the entire system is offline.
- Do not say meal generation works offline.
- Do not demo offline mode before the profile and plan are already saved.
- Recipe details may not persist across app restarts unless previously cached and still in memory.
- Purchased item tracking persistence needs validation because it is UI `rememberSaveable` state rather than clear repository storage.

## 8. Validation Materials Checklist

The manual test case document is already complete and should be attached separately. Do not recreate it.

| Material | Owner | Status | Notes |
|---|---|---|---|
| Official build/branch info | Lead developer | Needed | Include branch `ui/layout-refine`, commit `d7c2842`, versionName `1.10.3`, versionCode `39`, APK file/hash. |
| System overview sheet | Lead developer / paper lead | Needed | Use Executive Summary and Architecture sections here. |
| Feature implementation audit | Lead developer | Prepared here | Use Section 3. |
| Architecture explanation | Lead developer | Prepared here | Use Section 5. |
| Algorithm explanation | Lead developer | Prepared here | Use Section 6. |
| Offline-first explanation | Lead developer | Prepared here | Use Section 7. |
| Known limitations | Lead developer / paper lead | Prepared here | Use Section 14. |
| Demo script | Presenter/demo lead | Prepared here | Use Section 12. |
| Screenshots | Documentation/design lead | Needed | Use Section 13. |
| APK/build link | Lead developer | Needed | Use latest verified build artifact. |
| GitHub branch/commit evidence | Lead developer | Needed | Screenshot branch, commit, and clean/dirty status. |
| CS/IT expert validation form | Validation lead | Needed | Use Section 9. Attach completed test cases separately. |
| Algorithm validation form | CS/IT expert / statistician | Needed | Include planner constraints and sample outputs. |
| Offline-first validation form | CS/IT expert | Needed | Use offline/online matrix. |
| Computation validation samples | Statistician lead | Needed | Use Section 10. |
| Nutrition/OB-GYN materials | Domain lead | Needed | Use Section 11. |
| Survey instruments/data | Survey/statistics lead | Separate | Must come from survey team, not invented by developer. |
| Defense Q&A cheat sheet | Presenter/demo lead | Prepared here | Use Section 15. |

## 9. CS/IT Expert Validation Package

Short system overview:

PCOSINA is a local-first Android meal planning decision-support system for users managing PCOS-related wellness routines. It collects user profile, goals, restrictions, pantry, budget, and preferences, then uses a backend constrained optimizer to generate a weekly meal plan. The app also provides grocery support, local progress tracking, reminders, support/feedback, and saved local data access.

What the CS/IT expert should validate:

- Functional suitability of implemented flows.
- Architecture separation between UI, ViewModels, repositories, local storage, backend API, and planner logic.
- Offline-first behavior for saved local data.
- Correct handling of route guardrails and error/loading states.
- Algorithm feasibility and safe constraint handling.
- Reliability of reminders, persistence, and recovery behavior.
- Security/authentication appropriateness for thesis prototype scope.

Suggested Likert-scale items:

| Item | Rating Scale |
|---|---|
| The system provides the core functions expected from a PCOS meal planning support app. | 1 Strongly Disagree to 5 Strongly Agree |
| The navigation flow is understandable and appropriate for mobile users. | 1 to 5 |
| The system separates user interface, storage, and algorithm/backend responsibilities clearly. | 1 to 5 |
| The system handles missing or invalid inputs appropriately. | 1 to 5 |
| The meal planning process reflects valid constraint-based decision logic. | 1 to 5 |
| The offline-first behavior is appropriate for saved user data. | 1 to 5 |
| The system provides enough feedback for loading, errors, and no-safe-plan cases. | 1 to 5 |
| The system is technically feasible for thesis-level deployment and validation. | 1 to 5 |

Algorithm validation checklist:

- Verify Stage 1 filtering/ranking evidence in `shortlist_candidates`.
- Verify hard restriction/allergy handling in `restriction_failure_reasons`.
- Verify profile conflict detection in `validate_profile`.
- Verify budget hard cap in `solve_meal_plan`.
- Verify CP-SAT solver model creation and objective in `solve_meal_plan`.
- Verify no-safe-plan response and guidance in backend `main.py`.
- Verify Android only calls backend and does not claim local optimization.

Architecture validation checklist:

- Verify Compose screens under `ui/screens`.
- Verify navigation in `Routes` and `AppNavHost`.
- Verify ViewModels: `AuthViewModel`, `UserViewModel`, `MealPlanViewModel`, `GroceryViewModel`, `ProgressViewModel`.
- Verify local repositories: `UserPreferencesRepository`, `GroceryLocalRepository`, `ProgressLocalRepository`, `ReflectionStore`.
- Verify API service: `PcosinaApiService`.
- Verify backend planner and endpoints: `backend/main.py`, `backend/services/meal_planner.py`.

Offline-first validation checklist:

- Sign in online, complete profile, generate plan, sync grocery, make progress logs.
- Turn off internet.
- Reopen app and verify saved profile, plan, grocery, pantry, reminders, and progress logs are still visible.
- Attempt new plan generation and meal swap offline and verify clear error/blocked behavior.
- Re-enable internet and verify online features resume.

Signature/certification page content:

"I certify that I reviewed the PCOSINA Android system, its implementation evidence, and the attached validation materials. My evaluation is limited to technical/system validation and does not certify medical treatment, diagnosis, or clinical effectiveness."

Comments/recommendations:

- Strengths observed:
- Issues or risks observed:
- Required corrections before final defense:
- Recommended future improvements:
- Expert name/signature/date:

## 10. Statistician Support Package

System computations available for checking:

| Computation | Evidence | What to Validate |
|---|---|---|
| BMI | `HealthMetrics.bmi`, `HealthMetrics.bmiCategory` | Formula, rounding/display, category thresholds. |
| BMR/TDEE/target calories | `HealthMetrics.bmrMifflinStJeorFemale`, `activityMultiplier`, `targetCaloriesPerDay`; backend target calculation in `solve_meal_plan` | Formula assumptions, female Mifflin-St Jeor, activity multiplier, weight-loss adjustment. |
| Macro targets | backend `macro_ratios`, `target_protein`, `target_carbs`, `target_fats` | Ratio logic and bounds. |
| Budget | `resolve_budget_weekly`, `model.Add(total_cost <= int(budget_weekly))`, `GroceryAggregation`, `PriceCatalog.estimatePriceDetail` | Weekly budget conversion, hard cap, cost estimate behavior. |
| Grocery aggregation | `buildGroceryListEntries`, `scaleQuantityText` | Quantity scaling and duplicate merging. |
| Progress summaries | `ProgressRefinedScreen`, `ProgressViewModel` | Displayed counts, weekly spend vs budget, saved logs. |

Manual vs system value table structure:

| Sample ID | Input Data | Manual Formula/Computation | Manual Result | System Result | Difference | Accepted? | Notes |
|---|---|---|---|---|---|---|---|
| BMI-01 | 70 kg, 160 cm | 70 / 1.60^2 | 27.34 | To be filled from app | To compute | Yes/No | Category should be Overweight. |
| CAL-01 | Age 25, 65 kg, 160 cm, Lightly Active | Mifflin + activity | To compute | To fill | To compute | Yes/No | Female formula. |
| BUD-01 | Weekly budget PHP 1500 | total estimated cost <= budget | To compute | To fill | To compute | Yes/No | Requires sample plan. |

What the statistician should validate:

- Formula correctness and rounding.
- Whether reported computations match manual calculations.
- Whether sample sizes, survey scoring, Likert interpretation, weighted means, and ISO 25010 result interpretation are statistically appropriate.

What should come from the survey team, not developer:

- Respondent counts.
- Actual survey responses.
- Weighted means.
- Standard deviations.
- Interpretation categories.
- Reliability analysis if required.
- Final Chapter 4 statistical tables.

## 11. Nutritionist/OB-GYN Support Package

Materials to provide:

- Sample generated 7-day meal plan from the validation build.
- Sample recipe details with ingredients, steps, calories, protein, carbohydrates, fats, fiber, sodium, and sugar if available.
- Sample grocery list generated from the plan.
- User profile sample used to generate the plan, including restrictions, allergies, budget, pantry, symptoms, and goal.
- Explanation that PCOSINA is a wellness meal planning support tool, not a diagnosis or treatment tool.

What they are validating:

- Whether sample meals are generally appropriate for PCOS-aware dietary support.
- Whether nutrition/macros shown are understandable and reasonable.
- Whether allergy/restriction handling is safe from a domain perspective.
- Whether disclaimers and wording avoid medical overclaiming.
- Whether the recipe data quality is acceptable for thesis prototype validation.

What they are not validating:

- Diagnosis of PCOS.
- Treatment of PCOS.
- Guaranteed clinical improvement.
- Replacement for dietitian/physician care.
- Long-term clinical outcomes.

Safety disclaimer:

"PCOSINA is a wellness decision-support application for meal planning. It does not diagnose, treat, cure, or prevent PCOS or any medical condition. Users should consult qualified health professionals for medical diagnosis, treatment, and individualized nutrition care."

Comment/recommendation form content:

- Are the sample meals appropriate for general PCOS-aware wellness support?
- Are there meals or ingredients that should be revised?
- Are the nutrition values and labels understandable to users?
- Are the dietary claims appropriately limited?
- Recommended revisions:
- Validator name/signature/date:

## 12. Demo Flow Script

Target duration: 10 to 15 minutes.

| Step | What to Show | What to Say | System Claim Supported | What Not to Say |
|---|---|---|---|---|
| 1 | Open PCOSINA app and show Login screen | "The production-facing path is Continue with Google. Email access remains for emulator/testing and support." | Authentication | Do not say email is removed. |
| 2 | Sign in with a prepared Google account | "Authentication uses Firebase and Google sign-in." | Secure access | Do not demo with unstable credentials. |
| 3 | Profile setup/edit | "The app collects user profile, activity, PCOS-related inputs, food rules, budget, household, and pantry." | Personalization | Do not claim diagnosis. |
| 4 | Goal selection | "Goals steer meal planning priorities." | Preference-aware planning | Do not claim guaranteed outcomes. |
| 5 | Meal plan generation | "The app sends profile data to the backend optimizer and saves the result locally." | Backend constrained planning | Do not claim offline generation. |
| 6 | Meal plan viewing/Today plan | "The saved plan can be viewed in the app after generation." | Local saved plan display | Do not claim recipe details are always offline. |
| 7 | Recipe details | "Recipe details show ingredients, steps, and nutrition values from backend recipe data." | Recipe support | Do not claim nutrition data is clinically certified unless validated. |
| 8 | Meal swap | "Swaps are backend-assisted alternatives for the selected meal slot." | Alternative planning | Do not claim swaps work offline. |
| 9 | Grocery list | "The app derives grocery items from planned recipes and scales for household size." | Grocery generation | Do not claim exact market prices. |
| 10 | Bought/pantry tracking | "Items can be marked bought or covered by pantry." | Shopping support | Do not overclaim durable purchased tracking until validated. |
| 11 | Progress/check-in | "Daily check-ins and meal logging are stored locally for today." | Progress tracking | Do not call it clinical monitoring. |
| 12 | Meal logging policy | "Logging is today-only and sequence-based, not clock-time based." | Data integrity | Do not say it detects actual eating time. |
| 13 | Settings/reminders | "Users can configure local reminders and quiet hours." | Usability/reliability | Do not guarantee OS delivery. |
| 14 | Offline-first explanation | Turn off internet after data exists and show saved data | "Saved profile, plan, grocery, and logs remain available locally." | Offline-first | Do not generate a new plan offline. |
| 15 | Limitations | Briefly show support/settings or closing slide | "This is a decision-support prototype, not a medical device." | Honest scope | Do not overpromise clinical impact. |

## 13. Screenshots/Evidence Checklist

Capture these from the official validation build:

- Login screen showing `Continue with Google`.
- Email access panel, only if explaining emulator/testing support.
- Sign-up screen, only if documenting test/support flow.
- Profile setup Step 1.
- Medical profile setup Step 2.
- Preferences/budget/pantry setup Step 3.
- Goal selection screen.
- Dashboard/Home screen.
- Meal plan empty state.
- Meal plan generation loading state.
- Generated 7-day meal plan.
- Today's Plan section.
- Recipe details screen.
- Meal swap dialog.
- Grocery list screen.
- Grocery item marked Bought.
- Grocery item marked In Pantry.
- Pantry dialog/add item dialog.
- Progress screen.
- Today's check-in dialog/form.
- Meal check-in dialog/form.
- Weekly review/notes.
- Settings screen.
- Reminder settings screen.
- Notification summary screen if included in build.
- Offline demo: saved plan visible with internet disabled.
- Offline demo: new plan generation or swap blocked/errored clearly.
- GitHub branch `ui/layout-refine`.
- Commit `d7c2842`.
- Git status or validation tag evidence.
- Build success or APK artifact evidence.
- Completed CS/IT validation forms/signatures after validation.
- Completed nutrition/OB-GYN validation forms/signatures.
- Statistician computation validation table.

## 14. Known Limitations and Safe Wording

| Limitation | Safe Paper Wording | Defense Answer | Chapter 6 Recommendation |
|---|---|---|---|
| Not a diagnostic tool | "PCOSINA is a wellness decision-support app and does not diagnose PCOS." | "Diagnosis remains with medical professionals." | Add stronger clinician-reviewed disclaimers and referral guidance. |
| Not a treatment tool | "The system supports meal planning decisions but does not claim to treat or cure PCOS." | "It supports habits and planning, not treatment." | Conduct future clinical/user outcome studies only with proper ethics review. |
| Google Auth requires internet | "Authentication uses Firebase/Google services and requires connectivity." | "Offline-first starts after the user has authenticated and saved data." | Explore offline session recovery and clearer offline messaging. |
| New meal generation requires backend | "Meal plans are generated by the backend optimizer." | "The mobile app stores results locally, but solving is online." | Research local fallback planner for limited offline generation. |
| Recipe database may be limited | "Plan quality depends on the available recipe and nutrition dataset." | "Experts should review and recommend dataset improvements." | Expand and professionally review Filipino PCOS recipe dataset. |
| Nutrition data needs validation | "Nutrition values are used for planning and must be validated against trusted references." | "The thesis validation includes domain review." | Add validated nutrition source provenance for every recipe. |
| Price estimates are approximate | "Budgeting uses estimated ingredient costs." | "It guides planning, but actual market prices may vary." | Add live or periodically updated market price sources. |
| Pantry depends on user input | "Pantry awareness depends on user-maintained pantry entries." | "The system cannot know actual household stock automatically." | Add barcode/inventory features in future work. |
| Purchased tracking persistence unclear | "Bought item status is available in the grocery UI; persistence should be validated." | "We will validate whether it remains after relaunch before final claim." | Persist purchased state per active plan. |
| Recipe details offline limited | "Saved plan viewing is local; full recipe details may require backend access." | "Offline demo should show saved plan and grocery, not new recipe fetch." | Cache recipe details locally with versioning. |
| Reminders device-dependent | "Reminders use Android local scheduling and depend on permissions/device policies." | "We can demonstrate setup; delivery depends on OS behavior." | Add notification delivery verification logs. |
| No iOS version | "The implementation is Android only." | "The thesis scope is Android." | Develop cross-platform or iOS app in future work. |
| Family/shared access not found | "The current app supports individual authenticated user data." | "Family sharing is not implemented as a separate feature." | Add household/shared account support. |
| Long-term outcomes not evaluated | "The study evaluates system quality and usability, not long-term clinical outcomes." | "Clinical outcomes require a different study design." | Conduct longitudinal research with health professionals. |

## 15. Defense Q&A Cheat Sheet

| Question | Concise Answer |
|---|---|
| What is PCOSINA? | PCOSINA is an Android wellness decision-support app for Filipino PCOS-aware meal planning, grocery support, reminders, and progress tracking. |
| Is it a medical app? | It is health-related wellness support, but it is not a medical device. |
| Does it diagnose PCOS? | No. It does not diagnose PCOS or any condition. |
| Does it treat PCOS? | No. It supports meal planning decisions and habit tracking only. |
| What is offline-first here? | Saved profile, plan, pantry, grocery, logs, reflections, and reminder preferences are stored locally and can be viewed after login/data setup. |
| What needs internet? | Google/Firebase auth, cloud sync/restore, new plan generation, recipe details, meal swaps, and feedback submission. |
| Why Google Auth? | Google Auth simplifies secure identity verification and account continuity while Firebase manages authenticated sessions. |
| Why is email still present? | Email access remains in the system for emulator testing, support, and operator/testing workflows. The user-facing primary path is Continue with Google. |
| How does the algorithm work? | The backend filters unsafe or unsuitable recipe candidates, optionally scores them, then uses OR-Tools CP-SAT constrained optimization to select the weekly plan. |
| Where is the two-stage approach? | Stage 1 is `shortlist_candidates`; Stage 2 is `solve_meal_plan` with CP-SAT in `backend/services/meal_planner.py`. |
| Is MILP actually implemented? | The backend implements constrained optimization using OR-Tools CP-SAT. It is backend-side, not Android-side. |
| How does pantry affect grocery and planning? | Pantry entries are saved locally, matched against grocery items, and used as a planning signal/reward in the backend. |
| How does meal logging work? | Meal completion is local, today-only, and sequence-based for planned meals. |
| Can users log meals for past days? | The policy blocks past-day and future-day logging. |
| Is meal logging time-based? | No. It is date-based and sequence-based, not based on exact clock time. |
| Why include secondary users? | If secondary/support users are discussed, frame them as future household support unless actual shared access is validated. |
| Can family/spouse access the account? | No dedicated family/shared access feature was found in the Android audit. |
| How was the system validated? | Manual test cases are completed separately; CS/IT experts, domain validators, and statistician materials should be attached after execution/signature. |
| Why ISO 25010? | ISO 25010 gives a recognized framework for evaluating software product quality, including functional suitability, usability, reliability, maintainability, and portability. |
| What did CS/IT experts validate? | Technical correctness, feature behavior, architecture, data handling, offline-first behavior, algorithm feasibility, and error handling. |
| What did the statistician validate? | Computation samples, formula outputs, and statistical treatment of survey/validation data. |
| What did nutrition/OB-GYN validate? | Meal plan appropriateness, nutrition presentation, PCOS-safe wording, and health limitations. |
| What are the limitations? | It is Android-only, not a medical device, not fully offline, depends on recipe/nutrition data, and does not prove clinical outcomes. |
| What should future researchers improve? | Local fallback generation, larger validated recipe dataset, durable grocery purchased tracking, health professional review, and longitudinal outcome evaluation. |

## 16. Team Task Assignment Plan

| Role | Responsibilities | Deliverables | Priority | Dependencies | Deadline Recommendation |
|---|---|---|---|---|---|
| Lead developer | Freeze validation version, provide APK, branch/commit evidence, feature audit, architecture and algorithm evidence. | Official version sheet, APK/hash, system audit, demo build. | High | Clean branch/build. | Before expert validation. |
| Paper lead | Align thesis claims with evidence, update Chapter 3/4/5/6 wording, avoid overclaims. | Thesis-aligned system description and limitations. | High | System audit and validation outputs. | Before Chapter 4 drafting. |
| Survey/statistics lead | Manage respondent data, compute statistics, coordinate statistician validation. | Survey dataset, computation tables, statistical results. | High | Completed survey and computation samples. | Before Chapter 4. |
| Domain/health validation lead | Coordinate nutritionist/OB-GYN review. | Sample plan packet, signed health validation forms, comments. | High | Stable sample plan and recipe outputs. | Before final Chapter 4. |
| Documentation/design lead | Capture screenshots, organize validation appendices, prepare forms. | Screenshot folder, appendix-ready files, signed forms. | Medium/High | Official build and demo flow. | Before mock defense. |
| Presenter/demo lead | Prepare 10-15 minute demo, rehearse safe wording and limitations. | Demo script, Q&A cheat sheet, backup screenshots/video. | High | Stable APK and sample account. | Before mock defense. |

## 17. Final Prioritized Action Plan

### Must Do Before Expert Validation

- Freeze official validation build from `ui/layout-refine`.
- Record commit `d7c2842`, versionName `1.10.3`, versionCode `39`, APK path/hash, and dirty/clean status.
- Prepare one stable Google test account.
- Attach completed manual test case document separately.
- Prepare CS/IT expert form using Section 9.
- Capture required screenshots from Section 13.
- Prepare sample input profile and generated meal plan.

### Must Do Before Survey

- Confirm which app version respondents will see.
- Make sure survey questions match actual implemented features.
- Remove or reword claims not supported by the code audit.
- Prepare screenshots or short demo video for respondents if needed.
- Keep raw responses separate from developer-authored materials.

### Must Do Before Chapter 4

- Obtain signed CS/IT expert validation results.
- Obtain domain validation comments/signatures.
- Obtain statistician-reviewed computation/statistical outputs.
- Insert actual validation results only after they exist.
- Use safe wording from this package for implementation findings.

### Must Do Before Mock Defense

- Rehearse demo using the official APK/account.
- Verify Google sign-in and backend meal generation on the demo network.
- Prepare offline demo only after saving profile/plan/grocery/logs.
- Prepare backup screenshots/video in case emulator or phone fails.
- Rehearse Q&A from Section 15.

### Must Do Before Final Defense

- Finalize validation appendices.
- Freeze final thesis claims to match evidence.
- Include limitations and future work.
- Prepare APK/build evidence and GitHub evidence.
- Keep the manual test case document as a separate attachment, not duplicated here.
