# Rule-Based Decision Trees

These are decision-flow diagrams based on actual code paths. They are **not** ML decision trees.

## A. Profile Validation Decision Tree

```mermaid
flowchart TD
    A[Generate plan request received] --> B{Android profile has age, height, weight, activity, goal?}
    B -- No --> C[Client rejects request before backend call]
    B -- Yes --> D[Backend validate_profile()]
    D --> E{householdSize 1..6 and maxCookingTime 10..240?}
    E -- No --> F[Return no-safe-plan or validation error]
    E -- Yes --> G{conflicting restrictions or priorities?}
    G -- Yes --> F
    G -- No --> H[Proceed to Stage 1 candidate shaping]
```

- Source files used: `app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt:34`, `backend/services/meal_planner.py:762`
- Input variables: age, heightCm, weightKg, activityLevel, goal, householdSize, maxCookingTimeMinutes, dietaryRestrictions, allergies, planningPriority, weeklyBudgetPhp
- Output variables: valid request or failure message
- Validation approach: manual invalid-profile cases plus backend unit tests for conflicting restrictions
- What to show during demo: incomplete profile handling and one conflicting-profile example

## B. Health Computation Decision Tree

```mermaid
flowchart TD
    A[Profile values entered] --> B[Android BMI and BMR calculations]
    B --> C[Android activity multiplier lookup]
    C --> D[Android calorie target preview]
    D --> E{Generate plan requested?}
    E -- No --> F[Show preview only]
    E -- Yes --> G[Backend recomputes BMR, TDEE, target]
    G --> H[Backend derives macro targets and tolerances]
```

- Source files used: `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:29`, `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63`, `backend/services/meal_planner.py:800`, `backend/services/meal_planner.py:1474`
- Input variables: age, heightCm, weightKg, activityLevel, goal, insulinResistanceLevel, symptoms
- Output variables: BMI, BMI category, calorie target preview, backend macro targets
- Validation approach: manual step-by-step math with one shared sample profile
- What to show during demo: Android preview values and note backend authoritative recalculation

## C. Recipe Filtering Decision Tree

```mermaid
flowchart TD
    A[All recipes loaded] --> B[Normalize ingredient tokens and tags]
    B --> C{Allergy exposure found?}
    C -- Yes --> D[Exclude recipe]
    C -- No --> E{Restriction rule fails?}
    E -- Yes --> D
    E -- No --> F{Minutes exceed max cooking time?}
    F -- Yes --> D
    F -- No --> G[Compute cost, pantry match, Stage 1 boosts]
    G --> H[Send recipe into shortlist buckets]
```

- Source files used: `backend/services/meal_planner.py:243`, `backend/services/meal_planner.py:729`, `backend/services/meal_planner.py:872`
- Input variables: recipe ingredients, tags, allergies, dietaryRestrictions, maxCookingTimeMinutes, householdSize, budget
- Output variables: kept/excluded recipe and shortlist diagnostics
- Validation approach: allergy/restriction test cases using actual recipes from `backend/recipes.json`
- What to show during demo: one excluded recipe and one kept recipe

## D. Allergy And Dietary Restriction Decision Tree

```mermaid
flowchart TD
    A[User allergy and restriction strings] --> B[Normalize allergies]
    B --> C[Derive recipe allergen exposures]
    C --> D{Normalized allergy intersects exposures?}
    D -- Yes --> E[Exclude recipe]
    D -- No --> F{Restriction-specific rule fails?}
    F -- Yes --> E
    F -- No --> G[Recipe remains eligible]
```

- Source files used: `backend/services/meal_planner.py:272`, `backend/services/meal_planner.py:307`, `backend/services/meal_planner.py:729`
- Input variables: allergies, dietaryRestrictions, recipe tokens, recipe tags
- Output variables: exclusion reason or pass-through
- Validation approach: fish/shellfish/dairy/egg/no-pork cases
- What to show during demo: fish allergy blocking `bangus`, shellfish allergy blocking `shrimp`

## E. Pantry Matching Decision Tree

```mermaid
flowchart TD
    A[Pantry items entered] --> B[Backend normalize_pantry()]
    B --> C[Normalize recipe ingredient tokens]
    C --> D[Count token overlap]
    D --> E[Add Stage 1 pantry score bonus]
    E --> F[Carry pantry reward/slack into Stage 2 objective]
```

- Source files used: `backend/services/meal_planner.py:262`, `backend/services/meal_planner.py:539`, `backend/services/meal_planner.py:1474`
- Input variables: pantryItems, recipe tokens, stage1 policy
- Output variables: pantryMatch count and score contribution
- Validation approach: manual token-overlap table using sample pantry and sample recipe
- What to show during demo: one recipe with high overlap and explain that pantry is rewarded, not hard-enforced

## F. Budget/Cost Decision Tree

```mermaid
flowchart TD
    A[Budget fields received] --> B[resolve_budget_weekly()]
    B --> C{Budget value exists?}
    C -- No --> D[No weekly hard cap]
    C -- Yes --> E[Estimate recipe costs]
    E --> F[Filter shortlist for budget-aware keep ratio]
    F --> G[Apply weekly budget hard cap in Stage 2]
    G --> H{Planning priority contains budget?}
    H -- Yes --> I[Add cost term to objective]
    H -- No --> J[Skip soft cost objective]
```

- Source files used: `backend/services/meal_planner.py:790`, `backend/services/meal_planner.py:529`, `backend/services/meal_planner.py:868`, `backend/services/meal_planner.py:1474`
- Input variables: weeklyBudgetPhp, budgetWeekly, budgetMonthly, householdSize, planningPriority
- Output variables: resolved weekly budget, cost-aware shortlist behavior, budget hard-cap enforcement
- Validation approach: compare budget and non-budget-priority runs/tests
- What to show during demo: budget priority versus balanced priority behavior

## G. Meal Plan Optimization Decision Tree

```mermaid
flowchart TD
    A[Validated profile and shortlist] --> B[Build daily targets and macro targets]
    B --> C[Construct CP-SAT slot variables]
    C --> D[Add hard constraints]
    D --> E[Add soft deviation variables]
    E --> F[Minimize weighted objective]
    F --> G{Solver finds feasible plan?}
    G -- Yes --> H[Return success response with explanation]
    G -- No --> I[Return no-safe-plan response]
```

- Source files used: `backend/services/meal_planner.py:1474`, `backend/services/plan_response_builder.py:79`
- Input variables: shortlisted recipes, targets, policy, budget, pantry, restrictions
- Output variables: `GeneratePlanResponse`
- Validation approach: planner contract tests plus manual reading of constraints/objective
- What to show during demo: solver metadata fields and explanation payload

## H. No-Safe-Plan Decision Tree

```mermaid
flowchart TD
    A[Failure occurs] --> B{Profile conflict?}
    B -- Yes --> C[Map to reason codes and guidance]
    B -- No --> D{No safe candidates or solver infeasible/time-out?}
    D -- Yes --> C
    D -- No --> E[Unknown infeasibility code]
    C --> F[Build no-safe-plan response]
    F --> G[Android shows guidance and cached-plan continuity if available]
```

- Source files used: `backend/services/plan_response_builder.py:9`, `backend/services/plan_response_builder.py:79`, `app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt:997`
- Input variables: message text, diagnostics, profile, cached plan availability
- Output variables: structured no-safe-plan response and Android presentation state
- Validation approach: backend no-safe-plan contract tests plus UI tests
- What to show during demo: reason codes, guidance text, and cached-plan fallback

## I. Grocery List Generation Decision Tree

```mermaid
flowchart TD
    A[Saved meal plan exists] --> B[Collect meal-source ingredients]
    B --> C[Aggregate grocery entries]
    C --> D[Estimate item prices]
    D --> E[Load pantry names]
    E --> F[UI marks covered items by pantry-name matching]
    F --> G[Display grocery list and snapshots]
```

- Source files used: `app/src/main/java/com/pcosina/app/domain/GroceryAggregation.kt:64`, `app/src/main/java/com/pcosina/app/ui/screens/GroceryRefinedScreen.kt:1500`, `app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt:35`
- Input variables: recipe ingredient strings, household size, pantry entries, checked state
- Output variables: grocery items, prices, pantry-covered state, saved snapshots
- Validation approach: manual grocery rebuild check using one sample recipe
- What to show during demo: generated grocery items and pantry-covered behavior

## J. Offline/Local Cache Decision Tree

```mermaid
flowchart TD
    A[App needs profile, plan, grocery, or progress state] --> B[Read local DataStore/ReflectionStore first]
    B --> C{Local artifact exists?}
    C -- Yes --> D[Use local data immediately]
    C -- No --> E[Fall back to empty/default state]
    D --> F{Optional cloud sync available?}
    F -- Yes --> G[Update local state from cloud-safe path]
    F -- No --> H[Continue offline-first behavior]
```

- Source files used: `app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt:35`, `app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt:17`, `app/src/main/java/com/pcosina/app/ui/ProgressViewModel.kt:127`, `app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt:68`
- Input variables: user ID, local artifact keys, cloud sync availability
- Output variables: loaded local state, optional sync update
- Validation approach: manual airplane-mode/load-from-cache demo
- What to show during demo: saved plan still appears without backend response

## K. Plan History And Active Plan Decision Tree

```mermaid
flowchart TD
    A[Plan response received or loaded] --> B[Persist last plan]
    B --> C[Append to plan history]
    C --> D[Load active plan on next app visit]
    D --> E{Swap or feedback updates plan?}
    E -- Yes --> F[Persist updated plan and recalculate metrics]
    E -- No --> G[Keep current active plan]
```

- Source files used: `app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt:35`, `app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt:68`
- Input variables: plan response, plan history, active user ID
- Output variables: active plan, plan history, metrics
- Validation approach: save/load/swap walkthrough plus repository persistence tests where present
- What to show during demo: reopening the app and seeing the saved plan

## L. Swap Meal Decision Tree

```mermaid
flowchart TD
    A[User taps swap meal] --> B[Request swap options from backend]
    B --> C{Options available?}
    C -- No --> D[Show no-swap or no-safe feedback]
    C -- Yes --> E[User selects replacement recipe]
    E --> F[Replace local slot]
    F --> G[Recalculate metrics]
    G --> H[Persist updated active plan]
```

- Source files used: `app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt:34`, `app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt:68`, `backend/main.py:4506`
- Input variables: current recipe ID, mealLabel, activeRecipeIds, profile
- Output variables: swap option list or updated plan
- Validation approach: swap-path UI test plus backend candidate restrictions
- What to show during demo: swap one breakfast and observe metric update

## M. Progress Tracking Decision Tree

```mermaid
flowchart TD
    A[User opens progress screen] --> B[Load per-user logs and weekly reflections]
    B --> C{Selected date is today?}
    C -- No --> D[Block editing]
    C -- Yes --> E[Allow meal check-in, weight, notes, and feedback]
    E --> F[Persist locally in ReflectionStore/DataStore]
    F --> G{Feedback upload fails?}
    G -- Yes --> H[Queue feedback for retry]
    G -- No --> I[Mark feedback synced]
```

- Source files used: `app/src/main/java/com/pcosina/app/ui/ProgressViewModel.kt:127`, `app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt:17`
- Input variables: selected date, meal check-ins, reflections, feedback payload
- Output variables: saved log state, queued feedback state
- Validation approach: same-day logging test and offline feedback retry check
- What to show during demo: today-only logging lock and local persistence after reopening the app
