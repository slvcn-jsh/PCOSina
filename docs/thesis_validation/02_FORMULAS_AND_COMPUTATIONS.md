# Actual Formulas And Computations

This file separates what is directly implemented from what is recommended for thesis validation but not yet implemented in the current `dev` branch.

## ACTUALLY IMPLEMENTED IN SYSTEM

### 1. BMI
- Purpose: Compute body mass index for Android-side profile feedback.
- Actual formula or pseudocode: `bmi = weightKg / ((heightCm / 100.0) ^ 2)`; if `heightCm <= 0` or `weightKg <= 0`, return `0.0`.
- Input variables: `weightKg`, `heightCm`
- Output variables: `bmi`
- Units: kilograms, centimeters, kg/m^2
- Source file path: `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:29` (`HealthMetrics.bmi`)
- Example manual computation using sample profile: `65 / (1.60^2) = 25.390625`
- Implemented in: Android
- Manual validation recommended: Yes
- Suggested validation table format: `profile_id | weightKg | heightCm | manual_bmi | app_bmi | match_yes_no`

### 2. BMI Category Thresholds
- Purpose: Convert the numeric BMI into a human-readable category in Android UI.
- Actual formula or pseudocode:
  - `<= 0 -> "—"`
  - `< 18.5 -> Underweight`
  - `< 25 -> Normal`
  - `< 30 -> Overweight`
  - `else -> Obese`
- Input variables: `bmi`
- Output variables: `bmiCategory`
- Units: category label
- Source file path: `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:35` (`HealthMetrics.bmiCategory`)
- Example manual computation using sample profile: `25.390625 -> Overweight`
- Implemented in: Android
- Manual validation recommended: Yes
- Suggested validation table format: `profile_id | manual_bmi | expected_category | app_category | match_yes_no`

### 3. BMR (Mifflin-St Jeor, female constant)
- Purpose: Estimate baseline energy needs for calorie-target preview in Android and authoritative planning in backend.
- Actual formula or pseudocode:
  - Android: `bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161`; if invalid values are provided, Android falls back to `weight=60`, `height=155`, `age=25`.
  - Backend: same core formula, but inside planner startup it falls back to `weight=65`, `height=160`, `age=25` when inputs are invalid.
- Input variables: `weightKg`, `heightCm`, `age`
- Output variables: `bmr`
- Units: kcal/day
- Source file path: Android `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:55`, backend `backend/services/meal_planner.py:1474`
- Example manual computation using sample profile: `(10*65) + (6.25*160) - (5*25) - 161 = 1364`
- Implemented in: Android and backend
- Manual validation recommended: Yes
- Suggested validation table format: `profile_id | age | weightKg | heightCm | manual_bmr | android_bmr | backend_bmr | match_yes_no`

### 4. Activity Multiplier Mapping
- Purpose: Convert activity level text into the multiplier used for TDEE.
- Actual formula or pseudocode:
  - `Sedentary -> 1.2`
  - `Lightly Active -> 1.375`
  - `Moderately Active -> 1.55`
  - `Very Active -> 1.725`
  - default/unrecognized -> `1.375`
- Input variables: `activityLevel`
- Output variables: `activityMultiplier`
- Units: multiplier
- Source file path: Android `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:45`, backend `backend/services/meal_planner.py:809`
- Example manual computation using sample profile: `Lightly Active -> 1.375`
- Implemented in: Android and backend
- Manual validation recommended: Yes
- Suggested validation table format: `activity_level | expected_multiplier | system_multiplier | match_yes_no`

### 5. TDEE
- Purpose: Derive total daily energy expenditure from BMR and activity.
- Actual formula or pseudocode:
  - Android: `tdee = round(bmr * activityMultiplier)`
  - Backend: `tdee = int(bmr * activityMultiplier)`
- Input variables: `bmr`, `activityMultiplier`
- Output variables: `tdee`
- Units: kcal/day
- Source file path: Android `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63`, backend `backend/services/meal_planner.py:1474`
- Example manual computation using sample profile:
  - Android preview: `round(1364 * 1.375) = 1876`
  - Backend planner: `int(1364 * 1.375) = 1875`
- Implemented in: Android and backend
- Manual validation recommended: Yes
- Suggested validation table format: `profile_id | manual_tdee_android | manual_tdee_backend | app_tdee | backend_tdee | match_yes_no`

### 6. Daily Calorie Target
- Purpose: Produce the calorie target used in Android preview and backend planning.
- Actual formula or pseudocode:
  - Android:
    - `target = round(bmr * multiplier) + adjustment`
    - `Weight Loss -> -500`
    - `Symptom Management -> 0`
    - `General Health -> 0`
  - Backend:
    - `target = int(bmr * multiplier)`
    - `Weight Loss -> target -= 500`
    - `target += symptom_state["calorieTargetDelta"]`
    - `target = clamp(target, nutrition.calorie_min, nutrition.calorie_max)`
- Input variables: `bmr`, `activityLevel`, `goal`, backend `symptom_state`
- Output variables: `targetCalories`
- Units: kcal/day
- Source file path: Android `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63`, backend `backend/services/meal_planner.py:1474`
- Example manual computation using sample profile:
  - Android preview: `1876 - 500 = 1376`
  - Backend authoritative target: `1875 - 500 = 1375`
- Implemented in: Android and backend
- Manual validation recommended: Yes
- Suggested validation table format: `profile_id | goal | manual_target_android | manual_target_backend | app_target | backend_target | match_yes_no`

### 7. Goal-Based Calorie Adjustment
- Purpose: Modify daily calories based on the stated goal.
- Actual formula or pseudocode:
  - Android explicit branches: `Weight Loss = -500`, `Symptom Management = 0`, `General Health = 0`
  - Backend explicit branch: `Weight Loss = -500`; symptom-related deltas are applied through `symptom_adjustments()`
- Input variables: `goal`, `symptoms`
- Output variables: calorie delta
- Units: kcal/day
- Source file path: Android `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63`, backend `backend/services/meal_planner.py:324`
- Example manual computation using sample profile: `Weight Loss -> -500`, no additional symptom delta because no symptoms were supplied.
- Implemented in: Android and backend
- Manual validation recommended: Yes
- Suggested validation table format: `goal | symptoms | expected_delta | system_delta | match_yes_no`

### 8. Macro Ratio Mapping By Insulin Resistance
- Purpose: Set backend daily macro ratios before gram conversion.
- Actual formula or pseudocode:
  - `Severe -> (protein=0.30, carbs=0.30, fats=0.40)`
  - `Moderate -> (protein=0.28, carbs=0.35, fats=0.37)`
  - default (`Mild` and unrecognized) -> `(0.25, 0.40, 0.35)`
- Input variables: `insulinResistanceLevel`
- Output variables: `proteinRatio`, `carbRatio`, `fatRatio`
- Units: ratio
- Source file path: `backend/services/meal_planner.py:800` (`macro_ratios`)
- Example manual computation using sample profile: `Moderate -> (0.28, 0.35, 0.37)`
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `profile_id | insulinResistanceLevel | expected_ratios | backend_ratios | match_yes_no`

### 9. Macro Gram Conversion
- Purpose: Convert the backend calorie target into daily protein, carbohydrate, and fat gram targets.
- Actual formula or pseudocode:
  - `proteinGrams = clamp(int(targetCalories * proteinRatio / 4), protein_min, protein_max)`
  - `carbsGrams = clamp(int(targetCalories * carbRatio / 4), carb_min, carb_max)`
  - `fatsGrams = clamp(int(targetCalories * fatRatio / 9), fat_min, fat_max)`
  - then add any symptom-based deltas before the clamp is finalized.
- Input variables: `targetCalories`, `macroRatios`, policy min/max, symptom deltas
- Output variables: `targetProtein`, `targetCarbs`, `targetFats`
- Units: grams/day
- Source file path: `backend/services/meal_planner.py:1474` (`solve_meal_plan`)
- Example manual computation using sample profile:
  - Protein: `int(1375 * 0.28 / 4) = 96`
  - Carbs: `int(1375 * 0.35 / 4) = 120` then clamp stays `120`
  - Fats: `int(1375 * 0.37 / 9) = 56`
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `profile_id | targetCalories | manual_protein | manual_carbs | manual_fats | backend_targets | match_yes_no`

### 10. Unit Conversions
- Purpose: Convert weight and height units for Android data entry.
- Actual formula or pseudocode:
  - `kgToLb = kg * 2.20462`
  - `lbToKg = lb / 2.20462`
  - `cmToFeetInches`: convert cm to inches using `2.54`, split into feet and inches, round inches, roll over when inches reaches 12
  - `feetInchesToCm = (feet * 12 + inches) * 2.54`
- Input variables: `kg`, `lb`, `cm`, `feet`, `inches`
- Output variables: converted weight or height
- Units: kg, lb, cm, ft/in
- Source file path: `app/src/main/java/com/pcosina/app/domain/UnitConverter.kt:5` (`UnitConverter`)
- Example manual computation using sample profile: `160 cm -> about 5 ft 3 in`
- Implemented in: Android
- Manual validation recommended: Yes
- Suggested validation table format: `input_value | source_unit | expected_value | app_value | match_yes_no`

### 11. Pantry Normalization
- Purpose: Normalize pantry strings to tokens for backend Stage 1 matching.
- Actual formula or pseudocode:
  - lowercase input
  - split on non-alphanumeric separators
  - normalize token format
  - map through `ING_SYNONYMS`
  - de-duplicate
- Input variables: `pantryItems`
- Output variables: normalized pantry token set
- Units: normalized tokens
- Source file path: `backend/services/meal_planner.py:262` (`normalize_pantry`)
- Example manual computation using sample profile: `egg, rice, tomato, onion -> egg, onion, rice, tomato`
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `raw_pantry_item | expected_token | backend_token | match_yes_no`

### 12. Pantry Match Scoring
- Purpose: Count backend token overlap between a recipe and pantry tokens.
- Actual formula or pseudocode:
  - `pantryMatch = len(normalized_recipe_tokens intersect normalized_pantry_tokens)`
  - Stage 1 base score adds `pantryMatch * 1.5`
  - CP-SAT objective later adds pantry reward and pantry-minimum slack penalties
- Input variables: normalized pantry tokens, normalized recipe tokens
- Output variables: `pantryMatch`, score contribution
- Units: token count and weighted score
- Source file path: `backend/services/meal_planner.py:872` and `backend/services/meal_planner.py:539`
- Example manual computation using sample recipe `ph_qk_052 Tortang Talong Lite with Rice`:
  - Pantry tokens: `egg, onion, rice, tomato`
  - Normalized ingredient tokens include: `chopped, cooked, egg, onion, rice, roasted, talong, tomato, white`
  - Overlap count: `4`
  - Base-score pantry contribution: `4 * 1.5 = 6.00`
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `recipe_id | pantry_tokens | recipe_tokens | overlap_count | expected_score_bonus | backend_value`

### 13. Allergy Normalization
- Purpose: Convert free-text allergy entries into normalized families.
- Actual formula or pseudocode:
  - lowercase input
  - normalize token
  - map through `ALLERGEN_SYNONYMS`
  - return unique normalized family set
- Input variables: `allergies`
- Output variables: normalized allergies
- Units: normalized family labels
- Source file path: `backend/services/meal_planner.py:272` (`normalize_allergies`)
- Example manual computation using sample profile: no allergies supplied, so normalized set is empty.
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `raw_allergy | expected_family | backend_family | match_yes_no`

### 14. Allergen Family Matching
- Purpose: Detect whether recipe ingredient tokens expose an allergen family.
- Actual formula or pseudocode:
  - normalize recipe ingredient tokens
  - check whether any token belongs to `ALLERGEN_FAMILY_TOKENS[family]`
  - mark the family as exposed if a token matches
- Input variables: recipe ingredient tokens, allergen-family token map
- Output variables: detected exposure families
- Units: family labels
- Source file path: `backend/services/meal_planner.py:307` (`derive_allergen_exposures`)
- Example manual computation: token `bangus` maps to fish-family exposure; token `shrimp` maps to shellfish-family exposure.
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `ingredient_token | expected_family | backend_family | match_yes_no`

### 15. Dietary Restriction Filtering
- Purpose: Exclude recipes that violate recognized diet restrictions.
- Actual formula or pseudocode:
  - `No Pork` excludes recipes with normalized `pork` token
  - `No Beef` excludes recipes with normalized `beef` token
  - `Vegetarian` excludes `contains_meat` or `contains_seafood`
  - `Pescatarian` excludes `contains_meat`
  - `Lactose Intolerant` excludes `contains_dairy`
- Input variables: `dietaryRestrictions`, recipe tags, normalized ingredient tokens
- Output variables: keep/exclude decision and failure reasons
- Units: rule decision
- Source file path: `backend/services/meal_planner.py:729` (`restriction_failure_reasons`)
- Example manual computation using sample profile: no dietary restrictions supplied, so the sample recipe is not excluded by this path.
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `restriction | recipe_id | expected_keep_or_exclude | backend_result | reason`

### 16. Symptom-Based Adjustments
- Purpose: Modify nutrition targets and Stage 1 bonuses for specific goals or symptom tags.
- Actual formula or pseudocode:
  - `Symptom Management -> fiber +4, sugar -8, carbs -10, stage1 steady-carb/high-fiber bonuses`
  - `Weight Loss -> stage1 lower-calorie bonus +0.75, high-protein bonus +0.5`
  - `Weight Gain -> calorie +120, fiber +2, stage1 energy-density boosts`
  - `Irregular Periods -> fiber +2`
  - `Acne -> sugar -6, dairy penalty +2.0, steady-carb bonus +0.5`
  - `Hair Loss -> protein +8, high-protein bonus +1.25`
- Input variables: `goal`, normalized `symptoms`, recipe nutrition/tags
- Output variables: `calorieTargetDelta`, macro deltas, fiber/sugar adjustments, Stage 1 bonus flags
- Units: kcal/day, grams/day, score bonuses
- Source file path: `backend/services/meal_planner.py:324` and `backend/services/meal_planner.py:417`
- Example manual computation using sample profile: because no symptom tags were supplied, symptom-specific deltas remain zero; only the weight-loss lower-calorie recipe bonus can apply to eligible recipes.
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `goal | symptoms | expected_delta_or_bonus | backend_value | match_yes_no`

### 17. Recipe Cost Estimation
- Purpose: Estimate recipe-level cost for backend screening and budget handling.
- Actual formula or pseudocode:
  - `estimate_recipe_cost(ingredients)` sums per-ingredient `estimate_price_detail`
  - scale total by `0.75`
  - clamp to `30..450`
  - `meal_planner.estimate_cost()` multiplies by household size
- Input variables: ingredients, price rules, household size
- Output variables: estimated recipe cost
- Units: Philippine pesos
- Source file path: `backend/price_catalog.py:347` and `backend/services/meal_planner.py:529`
- Example manual computation using sample recipe `ph_qk_052`: backend estimated cost = `55` PHP for household size `1`
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `recipe_id | ingredient_list | manual_cost | backend_cost | household_size | match_yes_no`

### 18. Price Rule Matching
- Purpose: Match grocery or ingredient names against code-defined price rules.
- Actual formula or pseudocode:
  - Android and backend both scan rule keywords in normalized text
  - when a rule matches, unit/quantity factor is applied to the rule price
  - when no exact rule matches, category-based fallback logic is used
- Input variables: ingredient/grocery name, quantity text, price rules
- Output variables: matched rule, unit, estimated price
- Units: Philippine pesos
- Source file path: Android `app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt:13`, backend `backend/price_catalog.py:328`
- Example manual computation using Android grocery estimate: `egg` rule is `PHP 7 per piece`, so `2 pieces -> PHP 14`
- Implemented in: Android and backend, but with **different** code rule tables and fallback maps
- Manual validation recommended: Yes
- Suggested validation table format: `item_name | quantity | matched_rule | manual_price | system_price | android_or_backend`

### 19. Grocery Item Aggregation
- Purpose: Combine ingredient strings into a saved grocery list and estimate cost in Android.
- Actual formula or pseudocode:
  - normalize display key
  - scale quantity text by household size
  - merge matching segments
  - sum estimated prices for segments using `PriceCatalog.estimatePriceDetail`
- Input variables: ingredient strings, household size, saved plan sources
- Output variables: grocery list entries with quantity display and estimated price
- Units: text quantities and Philippine pesos
- Source file path: `app/src/main/java/com/pcosina/app/domain/GroceryAggregation.kt:64` (`buildGroceryListEntries`)
- Example manual computation using one item: `egg (2 pieces)` becomes one grocery entry with estimated price `PHP 14`
- Implemented in: Android
- Manual validation recommended: Yes
- Suggested validation table format: `source_ingredients | normalized_key | expected_quantity | expected_price | app_output`

### 20. Daily Total Calorie Calculation
- Purpose: Summarize current meal-plan calories in Android after load or swap.
- Actual formula or pseudocode: sum `recipe.calories` for all planned meals that have recipe details, together with the equivalent macro sums.
- Input variables: selected recipes with detail data
- Output variables: calories, protein, carbs, fats, fiber totals
- Units: kcal/day or plan totals; grams for macros/fiber
- Source file path: `app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt:195` (`MealPlanViewModel.calculateMetrics`)
- Example manual computation: if the current plan only contained `ph_qk_052`, the meal would contribute `390` kcal, `17` g protein, `46` g carbs, `14` g fats, `6` g fiber.
- Implemented in: Android
- Manual validation recommended: Yes
- Suggested validation table format: `plan_id | recipe_ids | manual_totals | app_totals | match_yes_no`

### 21. Stage 1 Base Score
- Purpose: Produce the deterministic pre-optimization score used to shortlist candidates.
- Actual formula or pseudocode:
  - `_base_score = (protein * 2.0) - (cost_est * 0.05) - (abs(calories - 500) * 0.15) + (pantryMatch * 1.5) + stage1Boost`
- Input variables: recipe protein, cost estimate, calories, pantryMatch, stage1Boost
- Output variables: `_base_score`
- Units: weighted score
- Source file path: `backend/services/meal_planner.py:539` (`_base_score`)
- Example manual computation using `ph_qk_052`:
  - `(17 * 2.0) - (55 * 0.05) - (|390 - 500| * 0.15) + (4 * 1.5) + 0.75`
  - `= 21.500`
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `recipe_id | manual_base_score | backend_base_score | match_yes_no`

### 22. Shadow ML Score
- Purpose: Produce deterministic fallback ranking features when no live ML score is applied.
- Actual formula or pseudocode:
  - `macro = min(1, (protein/45)*0.5 + (fiber/15)*0.5)`
  - `calorie = 1 - min(1, abs(calories - 500)/500)`
  - `prep = 1 - min(1, minutes/90)`
  - `pantry = min(1, pantryMatch/5)`
  - `budget = 1 - min(1, recipeCost / (budget/21))` when budget exists
  - `shadow = 0.30*macro + 0.25*calorie + 0.15*prep + 0.20*pantry + 0.10*budget`
- Input variables: recipe nutrition, prep time, pantry match, budget, cost
- Output variables: `shadowMlScore`
- Units: 0..1 score
- Source file path: `backend/services/meal_planner.py:593` (`_shadow_ml_score`)
- Example manual computation using `ph_qk_052`: shadow score ≈ `0.6147`
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `recipe_id | manual_shadow_score | backend_shadow_score | match_yes_no`

### 23. Final Stage 1 Boost After Shadow/ML Weighting
- Purpose: Apply bounded ML or shadow score influence without replacing hard filters.
- Actual formula or pseudocode:
  - `effectiveBoost = preservedBoost + (boundedMlScore * effectiveWeight * 10.0) - prepPenalty`
  - where `boundedMlScore` is capped by `ML_score_cap`
- Input variables: preserved boost, ML or shadow score, weight, cap, prep penalty
- Output variables: updated `_stage1_boost`
- Units: weighted score
- Source file path: `backend/services/meal_planner.py:665` (`_apply_stage1_scoring`)
- Example manual computation using `ph_qk_052`: updated boost with shadow contribution ≈ `1.200`, giving a revised base score ≈ `21.950`
- Implemented in: Backend
- Manual validation recommended: Yes
- Suggested validation table format: `recipe_id | preserved_boost | shadow_or_ml_score | manual_final_boost | backend_final_boost`

### 24. Stage 2 Optimization Constraints
- Purpose: Build the authoritative solver model for weekly meal planning.
- Actual formula or pseudocode:
  - define boolean variable `x[slot, recipe]`
  - enforce exactly one recipe per slot
  - forbid disallowed meal-slot assignments
  - forbid adjacent identical recipe reuse
  - cap recipe reuse by current repeat limit
  - enforce weekly budget cap when budget exists
  - add soft deviation variables for daily calories, macros, fiber, sodium, sugar, meal distribution, pantry, and diversity
  - minimize weighted objective across those deviations
- Input variables: shortlisted recipes, targets, policy config, budget, pantry, restrictions
- Output variables: selected recipe assignments and explanation payload
- Units: plan assignments and diagnostics
- Source file path: `backend/services/meal_planner.py:1474` (`solve_meal_plan`)
- Example manual computation using sample profile: the model would build `7 days * 3 meals = 21` meal slots, use the backend target of `1375` kcal/day, and enforce the weekly budget cap of `PHP 1500`.
- Implemented in: Backend
- Manual validation recommended: Yes, by tracing one solved case and one no-safe-plan case
- Suggested validation table format: `profile_id | candidate_count | hard_constraints_checked | soft_objective_terms | solver_result_status`

### 25. No-Safe-Plan Decision Logic
- Purpose: Return a structured failure response when no safe plan can be produced.
- Actual formula or pseudocode:
  - if profile validation fails -> return structured `no-safe-plan`
  - if Stage 1 yields too few safe candidates -> return structured `no-safe-plan`
  - if CP-SAT proves infeasible or times out -> return structured `no-safe-plan`
  - builder attaches `machineReasonCodes`, `humanGuidance`, `suggestedRelaxations`, diagnostics, solver metadata, and timestamps
- Input variables: validation error, diagnostics, profile, solver status
- Output variables: `GeneratePlanResponse` with `status="no-safe-plan"`
- Units: response contract
- Source file path: `backend/services/plan_response_builder.py:79` and `backend/services/plan_response_builder.py:9`
- Example manual computation using sample profile: not triggered by the sample profile itself, but it would trigger if the budget or restrictions made the candidate set or solver model infeasible.
- Implemented in: Backend and Android consumer path
- Manual validation recommended: Yes
- Suggested validation table format: `profile_id | failure_condition | expected_reason_codes | backend_response_status | guidance_present_yes_no`

## RECOMMENDED FOR THESIS VALIDATION BUT NOT YET IMPLEMENTED

### A. Unified single-source grocery price engine
- Status: NOT FOUND IN CURRENT DEV BRANCH
- Evidence: Android uses `app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt:13` while backend uses `backend/price_catalog.py:328` with separate rule tables and fallback maps.

### B. Hard pantry-feasibility constraint
- Status: NOT FOUND IN CURRENT DEV BRANCH
- Evidence: pantry is a Stage 1 score/reward path and a Stage 2 soft reward/slack path, not a hard all-ingredients-in-pantry rule in current planner code.

### C. Android-side macro target computation and display parity with backend planner
- Status: NOT FOUND IN CURRENT DEV BRANCH
- Evidence: Android `HealthMetrics` computes calorie preview only; macro ratio and gram targets are computed in backend planner code.
