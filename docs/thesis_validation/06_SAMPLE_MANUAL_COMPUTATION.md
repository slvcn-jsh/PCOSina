# Sample Manual Computation

## Sample Profile

- age: 25
- height: 160 cm
- weight: 65 kg
- activityLevel: Lightly Active
- goal: Weight Loss
- insulinResistanceLevel: Moderate
- weeklyBudgetPhp: 1500
- householdSize: 1
- maxCookingTimeMinutes: 45
- pantryItems: egg, rice, tomato, onion
- allergies: none
- dietaryRestrictions: none

## 1. BMI

- Formula source: `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:29`
- Formula: `BMI = weightKg / ((heightCm / 100)^2)`
- Computation:
  - `heightM = 160 / 100 = 1.60`
  - `BMI = 65 / (1.60^2)`
  - `BMI = 65 / 2.56`
  - `BMI = 25.390625`

## 2. BMI Category

- Threshold source: `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:35`
- `25.390625` is `< 30` but `>= 25`, so the category is **Overweight**.

## 3. BMR

- Formula source: Android `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:55`, backend `backend/services/meal_planner.py:1755`
- `BMR = (10 * 65) + (6.25 * 160) - (5 * 25) - 161`
- `BMR = 650 + 1000 - 125 - 161`
- `BMR = 1364.0`

## 4. TDEE

- Activity multiplier source: `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:45`
- `Lightly Active -> 1.375`
- Android preview TDEE: `round(1364 * 1.375) = 1876`
- Backend planner TDEE: `int(1364 * 1.375) = 1875`

## 5. Daily Calorie Target

- Goal source: `app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt:63`
- Weight Loss uses `-500 kcal/day`
- Android preview target: `1876 - 500 = 1376`
- Backend planner target: `1875 - 500 = 1375`
- Thesis note: the backend target is the authoritative planner target; Android preview differs by 1 kcal here because Android uses `round()` while backend uses `int()`.

## 6. Macro Target

- Source: `backend/services/meal_planner.py:942` and `backend/services/meal_planner.py:1755`
- Moderate insulin resistance ratios: protein `0.28`, carbs `0.35`, fats `0.37`
- Protein target: `int(1375 * 0.28 / 4) = 96 g`
- Carbohydrate target: `int(1375 * 0.35 / 4) = 120 g`
- Fat target: `int(1375 * 0.37 / 9) = 56 g`
- These macro targets are implemented in the backend planner, not in Android `HealthMetrics`.

## 7. Pantry Match For One Actual Recipe

- Actual recipe used: `ph_qk_052` `Tortang Talong Lite with Rice` from `backend/recipes.json`
- Recipe nutrition: `390 kcal`, `17 g protein`, `46 g carbs`, `14 g fat`, `6 g fiber`
- Recipe ingredients:
  - talong roasted (1 medium)
  - egg (2 pieces)
  - onion chopped (2 tbsp)
  - tomato chopped (1/3 cup)
  - white rice cooked (2/3 cup)
- Backend pantry normalization source: `backend/services/meal_planner.py:300`
- Normalized pantry tokens: `egg, onion, rice, tomato`
- Normalized recipe tokens include: `chopped, cooked, egg, onion, rice, roasted, talong, tomato, white`
- Overlap count: `4`
- Result: the backend planner would record a pantry match count of `4` for this recipe.

## 8. Grocery Missing-Items Determination For The Same Recipe

- Source: `app/src/main/java/com/pcosina/app/ui/screens/GroceryRefinedScreen.kt:1827`
- Important actual behavior: the Android grocery screen uses pantry-name matching, not the backend token-overlap algorithm.
- With pantry entries entered exactly as `egg`, `rice`, `tomato`, and `onion`:
  - `egg` can exact-match a grocery item named `egg`
  - `rice` does **not** automatically exact-match `white rice cooked`
  - `tomato` does **not** automatically exact-match `tomato chopped`
  - `onion` does **not** automatically exact-match `onion chopped`
  - `talong roasted` remains uncovered
- Therefore the UI may still show `white rice cooked`, `tomato chopped`, `onion chopped`, and `talong roasted` as still needing purchase unless the pantry entry names are more specific or the user manually checks them off.

## 9. Estimated Price For One Grocery Item

- Source: `app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt:26`
- Android price rule: `egg -> PHP 7 per piece`
- Example quantity: `2 pieces`
- Estimated price: `7 * 2 = PHP 14`

## 10. How The Planner Treats This Profile In Stage 1

- Source: `backend/services/meal_planner.py:1088`, `backend/services/meal_planner.py:637`, `backend/services/meal_planner.py:691`
- The profile passes validation because it has valid age, height, weight, activity, goal, household size, cooking-time limit, and no conflicting restrictions.
- Stage 1 removes recipes that violate allergies/restrictions or exceed 45 minutes.
- For sample recipe `ph_qk_052`, the backend estimated cost is `PHP 82`.
- Deterministic base score:
  - `(protein * 2.0) - (cost * 0.05) - (abs(calories - 500) * 0.15) + (pantryMatch * 1.5) + stage1Boost`
  - `= 20.150`
- Deterministic shadow ranking score for the same recipe: `0.5917`
- After shadow-score weighting, the effective boost becomes about `1.200` and the revised base score becomes about `20.600`.
- ML remains assistive only here. Hard filters still run before any ranking effect.

## 11. How The Planner Treats This Profile In Stage 2

- Source: `backend/services/meal_planner.py:1755`
- The solver would create `21` meal slots (`7 days * 3 meals/day`).
- It would use the backend daily calorie target of `1375` kcal/day.
- It would use macro targets of `96 g protein`, `120 g carbs`, and `56 g fat`.
- It would enforce hard constraints such as:
  - exactly one recipe per meal slot
  - no adjacent identical recipe
  - recipe-repeat cap for the current solve attempt
  - weekly budget cap of `PHP 1500`
- It would then minimize soft deviations such as calorie error, macro error, fiber shortfall, sugar overage, repetition pressure, and preparation burden.

## 12. Actual Generated Output

- Full local generation was **not executed** in this environment because importing `backend/services/meal_planner.py` is blocked here: `ModuleNotFoundError: No module named 'domain'`
- The actual response shape is implemented in:
  - backend `backend/domain/models.py:272`
  - Android `app/src/main/java/com/pcosina/app/data/api/PcosinaApiService.kt:40`
- Actual response fields include:
  - `weekLabel`
  - `days`
  - `status`
  - `message`
  - `explanation`
  - `requestId`
  - `planId`
  - `policyVersion`
  - `machineReasonCodes`
  - `humanGuidance`
  - `suggestedRelaxations`
  - `diagnosticsReference`
  - `timestamps`
- Because execution was blocked, this chapter pack uses the real response contract and real recipe dataset as static evidence instead of fabricating a solver output.
