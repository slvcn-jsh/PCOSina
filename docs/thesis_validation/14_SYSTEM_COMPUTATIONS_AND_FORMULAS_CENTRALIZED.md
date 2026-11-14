# PCOSina System Computations And Formulas

Centralized reference for the deterministic computations currently used in PCOSina.

## Scope And Corrections

- The live planner is a 0-1 CP-SAT model in OR-Tools, not a classical MILP solver.
- Pantry use is soft in the current optimizer: it is rewarded and can be thresholded, but it is not a full ingredient-by-ingredient hard feasibility proof.
- `budget_penalty` is currently `0` in the live objective; the hard weekly budget is enforced separately.
- The current adaptive solve path retries nutrition tolerance and recipe-repeat limits only. It does not implement a four-level ladder that relaxes budget caps or pantry overlap.
- Android preview values can differ by 1 kcal from backend planning because Android uses `round()` while backend uses `int()`.
- Android and backend use different fallback defaults for invalid profile inputs.

## 1. Profile And Goal Normalization

### Goal semantics

- `hasGoalSelection(goal)` checks whether the goal string contains any non-empty token.
- `goalTextForApi(goal)` maps known goal aliases to canonical API values.
- `goalTypeFromText(goal)` resolves to:
  - Weight Loss
  - Symptom Management
  - General Health

### Profile tuning

`PlannerProfilePreparationUseCase` applies deterministic feedback adjustments before the planner runs:

- `Too repetitive` -> `varietyPreference = High`
- `Too expensive` -> `weeklyBudgetPhp = floor(0.9 * weeklyBudgetPhp)`, clamped at `>= 0`
- `Too hard to cook` -> `maxCookingTimeMinutes = max(10, maxCookingTimeMinutes - 10)`

Invalid requested profiles can fall back to stored profile data before planning.

### Meal logging policy

- Future-day logging is locked.
- Past-day logging is locked.
- Only today is loggable.
- Meal sequence logging enforces meal order:
  - Breakfast before Lunch
  - Lunch before Dinner

## 2. Health And Nutrition Targets

### BMI

`BMI = weightKg / (heightCm / 100)^2`

Category thresholds:

| Range | Category |
| --- | --- |
| `< 18.5` | Underweight |
| `18.5 - < 25.0` | Normal |
| `25.0 - < 30.0` | Overweight |
| `>= 30.0` | Obese |

### BMR

Female Mifflin-St Jeor:

`BMR = (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161`

Fallbacks for invalid values:

- Android: `60 kg`, `155 cm`, `25 years`
- Backend: `65 kg`, `160 cm`, `25 years`

### Activity multiplier

| Activity | Multiplier |
| --- | --- |
| Sedentary | 1.2 |
| Lightly Active | 1.375 |
| Moderately Active | 1.55 |
| Very Active | 1.725 |

### TDEE and target calories

- Android: `TDEE = round(BMR * activityMultiplier)`
- Backend: `TDEE = int(BMR * activityMultiplier)`

Goal adjustment:

- Weight Loss: `-500 kcal/day`
- Symptom Management: `0`
- General Health: `0`

Backend target flow:

`TargetCalories = clamp(TDEE + goalAdjustment + symptomState.calorieTargetDelta, calorieMin, calorieMax)`

Android preview uses the same base pattern without the backend symptom adjustment layer.

### Fixed macro ratio policy

| Policy | Protein | Carbs | Fat |
| --- | --- | --- | --- |
| Final PCOS wellness policy | 0.25 | 0.40 | 0.35 |
| Legacy severity argument | ignored | ignored | ignored |

### Macro gram conversion

`proteinGrams = int(targetCalories * proteinRatio / 4)`

`carbsGrams = int(targetCalories * carbRatio / 4)`

`fatsGrams = int(targetCalories * fatRatio / 9)`

Then symptom bonuses are applied and the results are clamped to policy bounds.

### Daily tolerance ladder

`toleranceLevels = [dailyTolerance, min(0.8, dailyTolerance + max(0.05, weeklyTolerance)), min(0.8, dailyTolerance + max(0.10, weeklyTolerance * 2.0))]`

### Adaptive infeasibility handling

The implemented retry sequence combines:

- the derived nutrition tolerance levels above
- the configured `planning.recipe_repeat_limits`, adjusted by variety preference

The ordering is controlled by `planning.infeasibility_relaxation_order`; the current supported dimensions are `daily_tolerance_percent` and `recipe_repeat_limits`. Budget is not relaxed by this sequence. If a weekly budget is present, the CP-SAT model keeps `total_cost <= budget` as a hard constraint. Pantry overlap is rewarded and can be thresholded during Stage 1, but there is no final fallback step that lowers pantry overlap and returns a partial plan.

### Budget normalization

`weeklyBudget = weeklyBudgetPhp if present else budgetWeekly else budgetMonthly / 4.33`

### Symptom adjustments

`normalize_symptoms()` maps symptom strings to canonical tokens. The live adjustments are:

| Trigger | Effect |
| --- | --- |
| Symptom Management goal | `fiberMinBonus +4`, `sugarMaxDelta -8`, `carbTargetDelta -10`, `stage1HighFiberBonus +1.5`, `stage1SteadyCarbBonus +1.0` |
| Weight Loss goal | `stage1LowerCalorieBonus +0.75`, `stage1HighProteinBonus +0.5` |
| `weight_gain` | `calorieTargetDelta -120`, `fiberMinBonus +2`, `stage1LowerCalorieBonus +1.25`, `stage1HighFiberBonus +0.75` |
| `irregular_periods` | `fiberMinBonus +2`, `stage1HighFiberBonus +0.75` |
| `acne` | `sugarMaxDelta -6`, `dairyPenalty +2.0`, `stage1SteadyCarbBonus +0.5` |
| `hair_loss` | `proteinTargetBonus +8`, `stage1HighProteinBonus +1.25` |

## 3. Safety, Normalization, And Filtering

### Token normalization

- `normalize_ingredients()` tokenizes ingredient names into lowercase alphanumeric tokens.
- `normalize_pantry()` does the same for pantry strings.
- `normalize_allergies()` maps allergy synonyms to canonical allergy families.
- `derive_allergen_exposures()` infers family exposure from ingredient tokens and recipe tags.
- `normalize_symptoms()` maps symptom aliases to canonical symptom tokens.

### Restriction filtering

Hard exclusions are applied before optimization:

- Allergies that match exposed allergen families
- No Pork
- No Beef
- Vegetarian
- Pescatarian
- Lactose Intolerant

### Profile validation

Backend and Android mirror the same constraint logic:

- Max cooking time must be between 10 and 240 minutes when set
- Vegetarian and Pescatarian cannot both be active
- Pescatarian conflicts with both fish and shellfish allergies
- Budget First requires a weekly budget
- Variety First conflicts with Low variety preference

### Tag inference

`infer_tags()` adds deterministic tags such as:

- `contains_meat`
- `contains_seafood`
- `contains_dairy`
- `contains_egg`

## 4. Stage 1 Candidate Scoring

### Cost estimation

Backend recipe cost:

`estimate_recipe_cost = clamp(sum(ingredient_price_estimates) * 0.90, 30, 450)`

Ingredient estimate:

`price = max(5, base_price * quantity_factor * category_multiplier * seasonal_multiplier * tingi_multiplier * safety_buffer)`

Where:

- `quantity_factor` converts units into the target unit
- `category_multiplier` is category-specific
- `seasonal_multiplier` comes from market / seasonal rules
- `tingi_multiplier` is `1.12` for piece-like conversions into kg/l, or `1.08` for tiny quantities below `0.25`
- `safety_buffer` is `1.10` when enabled

Category multipliers:

| Category | Multiplier |
| --- | --- |
| Produce | 0.75 |
| Meat/Seafood | 0.85 |
| Eggs & Dairy | 0.85 |
| Dry Goods | 0.80 |
| Spices & Condiments | 0.70 |
| Canned/Packaged | 0.80 |
| Beverages | 0.80 |
| Others | 0.75 |

Default piece weights used by the backend and Android helpers when a unit does not convert cleanly:

| Category | Default Piece Weight |
| --- | --- |
| Produce | 0.12 kg |
| Meat/Seafood | 0.15 kg |
| Eggs & Dairy | 0.06 kg |
| Dry Goods | 0.10 kg |
| Spices & Condiments | 0.05 kg |
| Canned/Packaged | 0.18 kg |
| Beverages | 0.25 kg |
| Others | 0.10 kg |

Quantity factor uses the following unit conversions:

- kg -> kg
- g -> kg / 1000
- lb -> kg / 2.2046
- oz -> kg / 35.274
- l -> l
- ml -> l / 1000
- cup -> 0.25 kg or 0.24 L depending on the target unit
- tbsp -> 0.015 kg or 0.015 L depending on the target unit
- tsp -> 0.005 kg or 0.005 L depending on the target unit

Piece-to-weight conversion uses category defaults and small ingredient-specific overrides.

### Android price catalog

The Android `PriceCatalog` follows the same idea with local rules, category averages, category multipliers, seasonal multipliers, and piece weights.

### Total recipe cost

`estimate_cost(recipe) = catalogCost * servingCostMultiplier`

If catalog pricing fails:

`rough = len(ingredients) * 6 + calories * 0.15`

Then:

`estimate_cost = clamp(rough, 30, 450) * servingCostMultiplier`

### Stage 1 base score

`Score_i = 2 * protein_i - 0.05 * cost_i - 0.15 * abs(calories_i - 500) + 1.5 * pantryMatch_i + stage1Boost_i`

### Stage 1 recipe boosts

`stage1RecipeBoost` is deterministic and based on thresholds:

- Fiber `>= 6`
- Protein `>= 25`
- Calories `<= 520`
- Carbs `<= 45`
- Sugar `<= 10` with Symptom Management
- Balanced goal fit for General Health
- Dairy soft penalty when the symptom state says dairy should be reduced

### ML assist score

The ML ranker is optional and assistive only.

Bounded shadow score:

`macro = min(1, (protein / 45) * 0.5 + (fiber / 15) * 0.5)`

`calorie = max(0, 1 - min(1, abs(calories - 500) / 500))`

`prep = max(0, 1 - min(1, minutes / 90))`

`pantry = min(1, pantryMatch / 5)`

`budget = 1 if budget <= 0 else max(0, 1 - min(1, cost / (budget / 21)))`

`shadowScore = clamp(0.30*macro + 0.25*calorie + 0.15*prep + 0.20*pantry + 0.10*budget, 0, 1)`

Applied stage 1 boost:

`stage1ScoreBoost = existingBoost + mlScore * effectiveWeight * 10 - prepPenalty`

`mlWeight` is capped by `mlCap`.

Stage 1 feature rows include deterministic fields such as:

- `restriction_count`
- `allergy_count`
- `budget_weekly_norm = budget_weekly / 7000`
- `max_cooking_time_minutes`
- `recipe_calories`
- `recipe_protein`
- `recipe_carbs`
- `recipe_fats`
- `recipe_fiber`
- `recipe_minutes`
- `recipe_cost_est`
- `pantry_overlap_count`
- `macro_distance_score = abs(protein - targetProtein) + abs(carbs - targetCarbs) + abs(fats - targetFats)`
- `calorie_distance_score = abs(calories - targetCalories)`
- meal availability flags for breakfast, lunch, and dinner
- `meals_per_day`

### Deduplication and pool shaping

- Similarity dedup uses Jaccard overlap: `|A ∩ B| / |A ∪ B|`
- If `restriction_count < 2`, the per-bucket shortlist cap is `stage1_max`; otherwise it is `int(stage1_max * restricted_shortlist_multiplier)`
- When budget filtering is active, the retained count per bucket is:
  - `keep_min = max(budget_keep_min_count, int(len(bucket) * budget_keep_min_ratio))`
  - `keep = max(keep_min, int(len(bucket) * ranking_cutoff))`
- `pantry_match_threshold` can act as an early overlap filter: if pantry exists and overlap is below the threshold, the recipe is removed before ranking
- `varietyWeights()`:
  - High -> repeat 7, group 3, diversity 2, pantry 1
  - Low -> repeat 3, group 1, diversity 1, pantry 1
  - Default -> repeat 5, group 2, diversity 1, pantry 1
- `adjust_max_per_week()` narrows or expands repeat limits based on variety preference
- `priority_overrides()` changes the relative weight of budget, variety, and macro pressure
- `budget_aware_pool_limit()` is:
  - `assignmentBudget = max(normalizedSlots * minimumCandidates, int(max(1, total_time_limit) * 180))`
  - `budgetLimitedPool = max(minimumCandidates, assignmentBudget // normalizedSlots)`
  - `return min(normalizedMaxPool, budgetLimitedPool)`
- `_cap_pool()` keeps a top share by score, then rotates by protein group and calorie bin
- Swap suggestions use the same shortlist, then reject a candidate if:
  - `nextRepeatCount > maxRepeatLimit`
  - `candidateTotalCost = currentTotalCost - currentRecipeCost + candidateCost` exceeds the weekly budget

## 5. Weekly Planning Optimization Model

### Decision variables

- `x_(r,s) = 1` if recipe `r` is assigned to slot `s`, else `0`
- `y_i = 1` if recipe `i` is used, else `0`

### Hard constraints

- `x[s,i] <= y[i]`
- Exactly one recipe per slot
- Non-allowed meal types are forced to `0`
- Adjacent duplicates are forbidden: `x[s,i] + x[s+1,i] <= 1`
- Recipe repetition is bounded: `sum_s x[s,i] <= max_per_week`
- Weekly cost must not exceed the hard budget ceiling when a budget exists
- Allergy and restriction-violating recipes are removed before the model is built

### Soft planning terms

- `repeat_over`
- `group_over`
- `diversity_slack`
- `pantry_reward`
- `pantry_min_slack`
- daily calorie absolute error
- protein / carb / fat absolute deviation
- fiber slack
- sodium overage
- sugar overage
- per-meal calorie error
- preparation time penalty

### Daily target generation

`target = int(BMR * activity_multiplier)`

If Weight Loss:

`target -= 500`

Then:

`target += symptomState.calorieTargetDelta`

Clamp to calorie policy bounds, then generate deterministic jitter:

`dailyTargets[d] = clamp(target + randomInt(-50, 50), calorieMin, calorieMax)`

The random seed is derived from the profile values plus an optional salt.

### Daily macro targets

`targetProtein = clamp(int(target * proteinRatio / 4) + symptomProteinBonus, proteinMin, proteinMax)`

`targetCarbs = clamp(int(target * carbRatio / 4) + symptomCarbDelta, carbMin, carbMax)`

`targetFats = clamp(int(target * fatRatio / 9), fatMin, fatMax)`

### Tolerance bounds

For each day:

`lower = max(minBound, int(targetValue * (1 - tol)))`

`upper = min(maxBound, int(targetValue * (1 + tol)))`

These bounds are built for protein, carbs, and fats.

### Objective function

Current live objective:

```text
Min Z =
  macro_mult * (total_err + total_meal_err + 2*total_dev_pro + total_dev_carb + total_dev_fat
                + total_fiber_slack + total_sodium_over + total_sugar_over)
  + budget_mult * cost_w * cost_objective
  + budget_mult * cost_w * budget_penalty
  + prep_time_w * prep_time_penalty
  + repeat_w * total_repeat_over
  + group_w * total_group_over
  + acceptance_w * total_meal_err
  + diversity_penalty
  - pantry_w * pantry_reward
  - diversity_w * diversity_reward
  + pantry_min_penalty
```

Where:

- `diversity_penalty = 5 * diversity_slack`
- `pantry_min_penalty = 3 * pantry_min_slack`
- `budget_penalty = 0`
- `cost_objective = total_cost` only when budget priority is active, else `0`

### Solver behavior

- The planner tries multiple tolerance and repeat-limit combinations.
- The order of relaxation is controlled by policy.
- The solver uses hints, then CP-SAT search, then structured no-safe-plan handling if no complete safe plan is found.

## 6. Tracking And Derived Metrics

### Weekly meal adherence

`weeklyAdherencePercent = completedMeals / plannedMeals * 100`

### Daily chart ratios

`ratio = completed / planned`, clamped to `[0, 1]`

### Week node status logic

- Future day -> `FUTURE`
- Planned count `0` -> `PENDING`
- Completed `>= planned` -> `COMPLETE`
- Completed `> 0` but below planned -> `PARTIAL`
- Otherwise -> `PENDING`

### Four-week trend

`firstHalfAverage` and `secondHalfAverage` are compared over the 28-day window.

- improving if `secondHalfAverage - firstHalfAverage >= 0.4`
- declining if `firstHalfAverage - secondHalfAverage >= 0.4`
- steady otherwise
- `checkInDays` counts days with any energy, mood, cravings, or meal check-in signal

### Today snapshot

`completionRatio = completedCount / plannedCount`, clamped to `[0, 1]`

### Meal plan metrics

`dayDivisor = response.days.size if non-empty else 7`

`avgProtein = totalProtein / dayDivisor`

`avgCarbs = totalCarbs / dayDivisor`

`avgFiber = totalFiber / dayDivisor`

`avgFats = totalFats / dayDivisor`

Counts are weighted by how many times each recipe appears in the plan.

### Frame timing probe

- `avgFrameMs = average(frameDurationsMs)`
- `p95FrameMs = sortedDurations[((n - 1) * 0.95).toInt()]`
- `worstFrameMs = max(frameDurationsMs)`
- `jankFrames = count(duration >= jankThresholdMs)`
- `jankRatio = jankFrames / frames`

### Meal logging progress policy

The app uses deterministic lock logic to keep tracking sequence valid and same-day only.

## 7. Benchmark And Thesis Validation Metrics

### Benchmark script metrics

`benchmark_milp.py` computes:

- `macro_dev`
- `budget_dev`
- `macro_score = max(0, 100 - (macro_dev / max_macro) * 100)`
- `budget_score = max(0, 100 - (budget_dev / budget_weekly) * 100)` when a budget exists; otherwise `100`
- `variety_score = min(100, unique_recipes / (len(result) * 3) * 100)`
- `ingredient_diversity = min(100, unique_ingredients / (len(result) * 3 * 4) * 100)`
- `daily_compliance = 100 - (macro_dev / (len(result) * 4 * 400)) * 100`
- `quality_index = 0.4*macro_score + 0.2*budget_score + 0.2*variety_score + 0.1*ingredient_diversity + 0.1*daily_compliance`
- `feasible_rate = feasible / total * 100`
- `p95 = sorted_times[int(len(sorted_times) * 0.95) - 1]` when enough samples exist

### Thesis validation formulas

These appear in the validation pack and are useful for chapter writing, but they are not live runtime outputs:

- `AbsoluteError = abs(Actual - System)`
- `PercentError = abs(Actual - System) / Actual * 100`
- `WeightedMean = sum(f * w) / N`
- `CronbachAlpha = (k / (k - 1)) * (1 - sum(item variances) / total score variance)`

## 8. OR-Tools Evidence

The live planner uses `CpModel`, `NewBoolVar`, `Add`, `AddHint`, `Minimize`, and `CpSolver`.

Official OR-Tools docs state that CP-SAT is for integer programming and that all constraints must be integer-based. The exact internal search strategy is not a single formula you can quote as the model. For thesis credibility, the defensible math is the model in `backend/services/meal_planner.py`, plus the exported proto and solver logs.

Official references:

- https://developers.google.com/optimization/cp/cp_solver
- https://github.com/google/or-tools/blob/stable/ortools/sat/docs/troubleshooting.md
- https://or-tools.github.io/docs/pdoc/ortools/sat/python/cp_model.html

## 9. Files To Cite

- `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt`
- `app/src/main/java/com/pcosina/app/domain/PlannerProfilePreparationUseCase.kt`
- `app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt`
- `app/src/main/java/com/pcosina/app/domain/GroceryAggregation.kt`
- `app/src/main/java/com/pcosina/app/domain/GroceryRebuildUseCase.kt`
- `app/src/main/java/com/pcosina/app/domain/ProgressSummaryUseCase.kt`
- `app/src/main/java/com/pcosina/app/domain/MealLoggingPolicyUseCase.kt`
- `app/src/main/java/com/pcosina/app/ui/util/GoalSemantics.kt`
- `app/src/main/java/com/pcosina/app/ui/util/ProfileConstraintSemantics.kt`
- `app/src/main/java/com/pcosina/app/ui/util/TodayLogSnapshot.kt`
- `app/src/main/java/com/pcosina/app/ui/util/FrameTimingProbe.kt`
- `app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt`
- `backend/services/meal_planner.py`
- `backend/price_catalog.py`
- `backend/benchmark_milp.py`
- `docs/thesis_validation/00_EVIDENCE_INDEX.md`
- `docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/optimization_objective_summary.csv`
