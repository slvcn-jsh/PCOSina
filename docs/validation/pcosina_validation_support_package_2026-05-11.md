# PCOSINA Validation Support Package

Date prepared: 2026-05-11  
Source branch: `ui/layout-refine`  
Commit: `d7c2842ac987bea1eef531ea969d30e3aa10ad8b`  
Android version: versionName `1.10.3`, versionCode `39`  

This file supports validation coordination only. The manual test case document is already complete and should be attached separately from `output/doc/PCOSINA_CSIT_Expert_Manual_Test_Cases_UPDATED_AsExpected.docx`.

## Validation Evidence Inventory

| Evidence Item | Source/Location | Purpose |
|---|---|---|
| Official system audit | `docs/system-audit/pcosina_lead_developer_system_responsibility_package_2026-05-11.md` | Master evidence package. |
| Completed manual test cases | `output/doc/PCOSINA_CSIT_Expert_Manual_Test_Cases_UPDATED_AsExpected.docx` | CS/IT manual execution attachment. |
| App version | `app/build.gradle.kts` | Confirms versionCode, versionName, SDK, API base URL. |
| Auth evidence | `AuthRepository`, `LoginScreen`, `AuthViewModel` | Confirms Google sign-in primary and email support/testing path. |
| Planner evidence | `backend/services/meal_planner.py`, `backend/main.py` | Confirms filtering, CP-SAT solver, no-safe-plan behavior. |
| Local-first evidence | `UserPreferencesRepository`, `ReflectionStore` | Confirms local saved profile, plan, grocery, logs, notifications. |
| Reminder evidence | `NotificationScheduler`, `NotificationPreferences`, `SettingsScreen` | Confirms reminder settings and WorkManager scheduling. |
| Progress evidence | `ProgressViewModel`, `MealLoggingPolicyUseCase`, `HealthMetrics` | Confirms today-only logging, BMI, progress logs. |

## CS/IT Expert Validation Scope

Ask CS/IT experts to validate:

- Functional suitability of implemented Android workflows.
- Correctness of route guardrails and navigation.
- Separation of UI, ViewModel, repository, local storage, API, and backend planner responsibilities.
- Offline-first behavior for saved local data.
- Backend-assisted meal generation and safe constraint handling.
- Error/loading/no-safe-plan handling.
- Feasibility of the architecture for thesis deployment.

Do not ask CS/IT experts to certify medical treatment, PCOS diagnosis, or clinical effectiveness.

## Suggested CS/IT Likert Items

| Item | Scale |
|---|---|
| The system provides the core functions expected from a PCOS meal planning support app. | 1 Strongly Disagree to 5 Strongly Agree |
| The mobile navigation flow is understandable and appropriate. | 1 to 5 |
| The system separates UI, storage, and algorithm/backend responsibilities clearly. | 1 to 5 |
| The system handles missing or invalid inputs appropriately. | 1 to 5 |
| The planner reflects valid constraint-based decision logic. | 1 to 5 |
| The offline-first behavior is appropriate for saved user data. | 1 to 5 |
| The system provides enough feedback for loading, errors, and no-safe-plan cases. | 1 to 5 |
| The system is technically feasible for thesis-level deployment and validation. | 1 to 5 |

## Algorithm Validation Checklist

- Verify `shortlist_candidates` for Stage 1 filtering/ranking.
- Verify `restriction_failure_reasons` for allergy and dietary restriction exclusion.
- Verify `validate_profile` for conflict detection.
- Verify `resolve_budget_weekly` and `model.Add(total_cost <= int(budget_weekly))` for budget handling.
- Verify `cp_model.CpModel`, `model.Minimize`, and `cp_model.CpSolver` in `solve_meal_plan`.
- Verify no-safe-plan response handling in backend `main.py` and Android `MealPlanGenerationNotice.NoSafePlan`.
- Verify Android calls backend through `MealPlanRepository` and `PcosinaApiService`.

## Offline-First Validation Checklist

- Sign in online using the prepared Google test account.
- Complete profile, goal, budget, pantry, and preference setup.
- Generate and save a meal plan.
- Sync or open grocery list from the plan.
- Add a progress check-in and weekly note.
- Disable internet.
- Reopen the app and verify saved profile, plan, grocery, pantry, progress, and reminders remain visible.
- Attempt a new plan generation or meal swap offline and verify the app handles it clearly.
- Re-enable internet and verify online features resume.

## Statistician Support

Provide the statistician with:

- BMI samples from `HealthMetrics.bmi` and `bmiCategory`.
- BMR/TDEE/target calorie samples from `HealthMetrics.targetCaloriesPerDay`.
- Backend macro target samples from `solve_meal_plan`.
- Budget/grocery estimate samples from `GroceryAggregation` and backend planner outputs.
- A manual-vs-system calculation table.
- Actual survey data from the survey team only.

## Nutritionist/OB-GYN Support

Provide domain validators with:

- One sample user profile.
- One generated 7-day meal plan.
- Several recipe detail screenshots.
- Grocery list screenshot/output.
- Nutrition/macros shown by the system.
- Safety disclaimer stating PCOSINA does not diagnose, treat, cure, or prevent PCOS.

## Signature/Certification Text

"I certify that I reviewed the PCOSINA Android system, its implementation evidence, and the attached validation materials. My evaluation is limited to technical/system validation or domain review, as applicable, and does not certify medical treatment, diagnosis, or clinical effectiveness."

Validator name:  
Role/Expertise:  
Affiliation:  
Signature:  
Date:
