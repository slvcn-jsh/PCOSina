# Chapter 4 Tables Ready

## Table 1: System Input Variables

| Variable | Actual Source | Used For |
| --- | --- | --- |
| age, heightCm, weightKg | app/src/main/java/com/pcosina/app/data/model/UserProfile.kt:6 | BMI/BMR/TDEE and planner validation |
| activityLevel | app/src/main/java/com/pcosina/app/data/model/UserProfile.kt:6 | TDEE multiplier and calorie target |
| goal | app/src/main/java/com/pcosina/app/data/model/UserProfile.kt:6 | Calorie adjustment and planner strategy |
| insulinResistanceLevel | backend/domain/models.py:14 | Backend macro ratio mapping |
| allergies, dietaryRestrictions | backend/domain/models.py:14 | Safety filtering |
| weeklyBudgetPhp / budgetWeekly / budgetMonthly | backend/services/meal_planner.py:932 | Budget cap and cost objective |
| householdSize | backend/domain/models.py:14 | Cost scaling and grocery scaling |
| maxCookingTimeMinutes | backend/domain/models.py:14 | Recipe filtering |
| pantryItems | app/src/main/java/com/pcosina/app/data/model/UserProfile.kt:6 | Pantry overlap scoring and grocery UI coverage |

## Table 2: Health Computations Used by PCOSina

| Computation | Actual Formula Summary | Source |
| --- | --- | --- |
| BMI | weight / (height_m^2) | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:29 |
| BMI category | Underweight <18.5, Normal <25, Overweight <30, else Obese | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:35 |
| BMR | (10*w) + (6.25*h) - (5*a) - 161 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:55 |
| TDEE | BMR * activity multiplier | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63 |
| Backend calorie target | int(BMR*multiplier) +/- goal/symptom deltas, then clamp | backend/services/meal_planner.py:1755 |

## Table 3: Activity Level Multipliers

| Activity Level | Multiplier | Source |
| --- | --- | --- |
| Sedentary | 1.2 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:45 |
| Lightly Active | 1.375 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:45 |
| Moderately Active | 1.55 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:45 |
| Very Active | 1.725 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:45 |

## Table 4: Goal-Based Calorie Adjustments

| Goal | Android Adjustment | Backend Adjustment | Source |
| --- | --- | --- | --- |
| Weight Loss | -500 | -500 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63 |
| Symptom Management | 0 | 0 before symptom deltas | backend/services/meal_planner.py:388 |
| General Health | 0 | 0 before symptom deltas | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63 |

## Table 5: Insulin Resistance Macro Ratios

| Insulin Resistance Level | Protein | Carbs | Fats | Source |
| --- | --- | --- | --- | --- |
| Mild/default | 0.25 | 0.40 | 0.35 | backend/services/meal_planner.py:942 |
| Moderate | 0.28 | 0.35 | 0.37 | backend/services/meal_planner.py:942 |
| Severe | 0.30 | 0.30 | 0.40 | backend/services/meal_planner.py:942 |

## Table 6: Sample Manual Computation

| Metric | Manual Result | Source |
| --- | --- | --- |
| BMI | 25.390625 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:29 |
| BMI Category | Overweight | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:35 |
| BMR | 1364.0 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:55 |
| Android TDEE | 1876 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63 |
| Backend TDEE | 1875 | backend/services/meal_planner.py:1755 |
| Android calorie target | 1376 | app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63 |
| Backend calorie target | 1375 | backend/services/meal_planner.py:1755 |
| Backend protein target | 96 g | backend/services/meal_planner.py:1755 |
| Backend carb target | 120 g | backend/services/meal_planner.py:1755 |
| Backend fat target | 56 g | backend/services/meal_planner.py:1755 |

## Table 7: Manual vs System Output Validation

| Metric | Manual Result | System Path | Expected Match Rule |
| --- | --- | --- | --- |
| BMI | 25.390625 | Android HealthMetrics.bmi | Exact or floating-point-equivalent match |
| BMR | 1364.0 | Android and backend BMR formula | Exact integer match |
| TDEE | Android 1876 / backend 1875 | Different rounding rules | Match the code path used |
| Calorie target | Android 1376 / backend 1375 | Different rounding rules | Match the code path used |
| Macro targets | 96 / 120 / 56 | Backend only | Exact integer match |

## Table 8: Recipe Filtering Rules

| Rule | Actual Behavior | Source |
| --- | --- | --- |
| Allergy filter | Exclude recipes with descendant allergen-family tokens | backend/services/meal_planner.py:371 |
| Diet restrictions | Exclude recipes by No Pork/No Beef/Vegetarian/Pescatarian/Lactose Intolerant rules | backend/services/meal_planner.py:868 |
| Cooking time | Exclude recipes beyond maxCookingTimeMinutes | backend/services/meal_planner.py:1088 |
| Profile conflicts | Reject conflicting restriction combinations before solving | backend/services/meal_planner.py:904 |

## Table 9: Pantry Matching Rules

| Layer | Actual Rule | Source |
| --- | --- | --- |
| Backend planner | Token overlap count influences Stage 1 score and Stage 2 soft reward | backend/services/meal_planner.py:637 |
| Android grocery UI | Pantry coverage uses pantry-name matching, not backend token-overlap count | app/src/main/java/com/pcosina/app/ui/screens/GroceryRefinedScreen.kt:1827 |

## Table 10: Grocery Price Estimation Rules

| Layer | Actual Rule | Source |
| --- | --- | --- |
| Android | PriceCatalog rule scan + quantity factor + minimum PHP 5 | app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt:26 |
| Backend | estimate_price_detail + recipe total * 0.75, clamped 30..450 | backend/price_catalog.py:646 |
| Gap note | Android and backend price catalogs are not identical | backend/price_catalog.py:635 |

## Table 11: Optimization Constraints

| Constraint | Hard/Soft | Source |
| --- | --- | --- |
| Exactly one recipe per meal slot | Hard | backend/services/meal_planner.py:1755 |
| No adjacent identical recipe | Hard | backend/services/meal_planner.py:1755 |
| Per-attempt reuse cap | Hard | backend/services/meal_planner.py:1755 |
| Weekly budget cap when budget exists | Hard | backend/services/meal_planner.py:1755 |
| Daily calorie deviation | Soft | backend/services/meal_planner.py:1755 |
| Daily macro deviation | Soft | backend/services/meal_planner.py:1755 |
| Fiber, sodium, sugar penalties | Soft | backend/services/meal_planner.py:1755 |
| Pantry and vegetable diversity rewards | Soft | backend/services/meal_planner.py:1755 |

## Table 12: No-Safe-Plan Conditions

| Condition | Actual Response Path | Source |
| --- | --- | --- |
| Conflicting profile/restrictions | Return no-safe-plan with mapped reason codes | backend/services/plan_response_builder.py:111 |
| No safe candidates after Stage 1 | Return no-safe-plan with diagnostics | backend/services/plan_response_builder.py:111 |
| Solver infeasible/time-out | Return no-safe-plan with solver metadata | backend/services/plan_response_builder.py:111 |

## Table 12A: Adaptive Constraint Handling Actually Implemented

| Attempt Dimension | Implemented Behavior | Not Relaxed |
| --- | --- | --- |
| Nutrition tolerance | Tries configured daily tolerance and wider derived tolerance bands | Allergy, restriction, slot validity, and budget hard cap |
| Recipe repeat limits | Tries configured repeat caps adjusted by variety preference | Adjacent duplicate block and meal-slot admissibility |
| Budget | Hard weekly cap when budget exists; no warning-band fallback | Budget ceiling |
| Pantry overlap | Stage 1 score/optional threshold and Stage 2 reward/slack | No final pantry-overlap relaxation level or partial plan |

## Table 12B: Trigger Conditions and Evidence Indicators

| Trigger Condition | Operational Indicator | Evidence Signal | Fallback Action |
| --- | --- | --- | --- |
| Profile conflict | `validate_profile()` returns a conflict message | Reason codes and guidance in no-safe-plan response | Return structured no-safe-plan |
| Insufficient safe candidates | Candidate count below `stage1.minimum_candidates_required` | Candidate counts and exclusion summaries when available | Return structured no-safe-plan |
| CP-SAT infeasible | All tolerance/repeat attempts fail | Solve-pair diagnostics with tolerance and repeat cap values | Return structured no-safe-plan |
| Planner timeout | Total planner deadline reached | `budget_exceeded_stage`, phase timings, solver budget metadata | Return structured no-safe-plan with retry guidance |
| Service/network interruption | Backend request or queued polling cannot complete | Android error state while local artifacts remain available | Preserve saved local plans/groceries/progress and provide retry guidance |

## Table 13: Actual Recipe Dataset Summary

- Total bundled recipes found in `backend/recipes.json`: `1130`
- Raw bundled recipes with complete nutrition: `76`
- Average calories among raw complete nutrition rows: `397.63`
- Average protein among raw complete nutrition rows: `17.25`
- Average minutes across bundled recipes: `77.81`
- Minimum calories among raw complete nutrition rows: `280`
- Maximum calories among raw complete nutrition rows: `550`
- Average ingredient count across bundled recipes: `11.05`

| Meal Type | Recipe Count |
| --- | --- |
| Breakfast | 357 |
| Dinner | 369 |
| Lunch | 358 |
| Universal | 46 |

## Table 14: Actual Backend Test Coverage

| Metric | Actual Inventory Result |
| --- | --- |
| Backend test files discovered | 68 |
| Backend test functions discovered | 265 |
| Planner-specific files observed | test_meal_planner.py, test_no_safe_plan_contract.py, test_planner_response_contract.py, test_worker_plan_jobs.py, test_policy_config.py, test_schema_contract.py |
| Coverage percentage | NOT FOUND IN CURRENT DEV BRANCH |

## Table 15: Actual Android Test Coverage

| Metric | Actual Inventory Result |
| --- | --- |
| Android test files discovered | 101 |
| Android test methods discovered | 277 |
| Unit/instrumented split | 218 unit tests, 59 instrumented tests |
| Coverage percentage | NOT FOUND IN CURRENT DEV BRANCH |

## Table 16: ISO 25010 Evaluation Instrument Mapping

| ISO 25010 Area | Actual System Feature To Evaluate |
| --- | --- |
| Functional suitability | Profile-based meal plan generation, restrictions, grocery output, no-safe-plan handling |
| Performance efficiency | Plan-generation wait time and UI responsiveness |
| Compatibility / offline support | Saved plan, grocery, and progress availability without network |
| Usability | Navigation clarity, profile wizard, progress screen, grocery list readability |
| Reliability | No-safe-plan handling, cached-plan continuity, feedback queue retry |
| Security / privacy | Authentication path, privacy-safe logging, local encrypted reflection storage |
| Maintainability (IT experts only) | Separation of UI, repository, policy, planner, and ML guardrails |

## Table 17: Expert Validation Instrument Mapping

| Expert Area | Actual Feature To Review |
| --- | --- |
| Nutrition appropriateness | Calorie target explanation and backend macro targets |
| PCOS relevance | Goal/symptom criteria and Filipino meal dataset |
| Allergy/restriction safety | Allergen-family filtering and restriction exclusions |
| Meal plan clarity | Explainability fields and user-facing messages |
| Grocery usefulness | Aggregated ingredient list and estimated pricing |

## Table 18: Recommended Statistical Treatment

| Data Type | Recommended Treatment | Status |
| --- | --- | --- |
| Computational validation pass/fail | Frequency and percentage | Recommended only |
| ISO 25010 Likert responses | Weighted mean, standard deviation, overall mean | Recommended only |
| Questionnaire reliability | Cronbach's alpha if required | Recommended only |
| Expert instrument validity | Content validity index if required | Recommended only |
| Multiple-expert agreement | Inter-rater agreement if needed | Recommended only |
