# PCOSina PhilFCT Manual Computation Guide

## Main Formula

Ingredient nutrient contribution = (Portion in grams / 100) x PhilFCT nutrient value per 100g

Meal total = sum of all ingredient contributions in the same meal

Daily total = breakfast total + lunch total + dinner total

## Worked Example: Cooked Rice

Example only. Replace PhilFCT values with the actual values selected by the team.

- Portion: 150g
- Multiplier: 150 / 100 = 1.5
- If PhilFCT calories = 130 kcal/100g, calories = 1.5 x 130 = 195 kcal
- If PhilFCT protein = 2.7g/100g, protein = 1.5 x 2.7 = 4.05g
- If PhilFCT carbs = 28.2g/100g, carbs = 1.5 x 28.2 = 42.30g
- If PhilFCT fat = 0.3g/100g, fat = 1.5 x 0.3 = 0.45g
- If PhilFCT fiber = 0.4g/100g, fiber = 1.5 x 0.4 = 0.60g

## Spreadsheet Formulas

If C = portion grams, E = kcal/100g, F = protein/100g, G = carbs/100g, H = fat/100g, I = fiber/100g:

```excel
Calories contribution = C2/100*E2
Protein contribution = C2/100*F2
Carbs contribution = C2/100*G2
Fat contribution = C2/100*H2
Fiber contribution = C2/100*I2
```

## Checking Rules

- Use correct cooked/raw PhilFCT item.
- Use edible portion weight.
- If exact food is unavailable, use closest PhilFCT match and write it in remarks.
- Round kcal to whole numbers and grams to one decimal place unless instructed otherwise.
- Water contributes 0 kcal, 0 protein, 0 carbs, 0 fat, and 0 fiber.
