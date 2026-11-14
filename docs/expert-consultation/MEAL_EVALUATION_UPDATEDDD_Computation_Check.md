# MEAL EVALUATION UPDATEDDD - Computation Check

## Nutrition Formula

For every ingredient and every nutrient:

```text
Ingredient contribution = (portion in grams / 100) x PhilFCT value per 100g
```

Meal total:

```text
Meal total = sum of all ingredient contributions in that meal
```

Daily total:

```text
Daily total = Breakfast total + Lunch total + Dinner total
```

## Current Computed Nutrition From Entered PhilFCT Values

| Meal | kcal | Protein | Carbs | Fat | Fiber |
|---|---:|---:|---:|---:|---:|
| Breakfast | 879 kcal | 23.8 g | 150.7 g | 20.1 g | 10.2 g |
| Lunch | 607 kcal | 31.7 g | 82.5 g | 16.8 g | 6.0 g |
| Dinner | 590 kcal | 34.4 g | 67.0 g | 20.3 g | 5.8 g |
| **Daily Total** | **2,076 kcal** | **89.9 g** | **300.1 g** | **57.1 g** | **22.1 g** |

## Comparison To Sir Gene Baseline

| Nutrient | Baseline | Current computed result | Status |
|---|---:|---:|---|
| Energy | 1,800 kcal | 2,076 kcal | High by 276 kcal |
| Carbohydrates | 225 g | 300.1 g | High by 75.1 g |
| Protein | 90 g | 89.9 g | Very close |
| Fat | 60 g | 57.1 g | Close |
| Fiber | 25-30 g | 22.1 g | Low |

## Important Data Checks Before Finalizing

1. `Cooked red/brown rice` is currently matched to `Rice, undermilled` at `363 kcal/100g`. This looks like dry/raw rice, not cooked rice. Since the portion is 150g cooked rice, this row should be rechecked. Use a cooked/boiled rice PhilFCT entry if available.
2. `Papaya` is currently matched to `Papaya petioles, boiled`. If the intended item is ripe papaya fruit, this should be changed to a papaya fruit entry, not petioles.
3. `Cooked munggo/mung beans` is currently matched to `Mung bean sprout, boiled`. If the meal is cooked munggo beans, this should be checked because sprouts and cooked mung beans are nutritionally different.
4. `Roasted peanuts, unsalted` is matched to `Mani, walang balok`. This may be acceptable if it refers to edible peanut kernel, but add a remark if it is the closest match.
5. Some ingredients use raw entries while the described ingredient is cooked or grilled. Use the closest cooked item when available and write substitutions in remarks.

## Pricing Formula

The pricing formula depends on the market unit.

### If price is per kilogram

```text
Cost = (portion used in grams / 1000) x price per kg
```

Example:

```text
Tomato used = 160g
Tomato price = PHP 30/kg
Cost = (160 / 1000) x 30 = PHP 4.80
```

### If price is per piece

```text
Cost = pieces used x price per piece
```

Example:

```text
Egg used = 1 pc
Egg price = PHP 10/pc
Cost = 1 x 10 = PHP 10
```

### If price is per bundle/tie/plastic/container

```text
Cost = (portion used / estimated usable amount in that bundle) x bundle price
```

Example:

```text
Malunggay used = 30g
One tie usable leaves = 100g
Tie price = PHP 10
Cost = (30 / 100) x 10 = PHP 3
```

### If price is per bottle of oil

Use milliliters if portion is in teaspoons/tablespoons:

```text
Cost = (portion used in ml / bottle ml) x bottle price
```

Common conversions:

```text
1 tsp oil = about 5 ml
2 tsp oil = about 10 ml
1 tbsp oil = about 15 ml
```

Example:

```text
Oil used = 5 ml
Bottle = 500 ml
Bottle price = PHP 200
Cost = (5 / 500) x 200 = PHP 2
```

## Pricing Workflow Recommendation

For nutrition, keep repeated ingredients separate per meal. For pricing, consolidate the same grocery item first when possible.

Example:

```text
Tomato total = 60g breakfast + 50g lunch + 50g dinner = 160g
Tomato cost = (160 / 1000) x price per kg
```

This is more realistic because the user buys tomato once, not separately for each meal.

## Assurance

The team is using the right computation approach. The formula is correct, and the worksheet structure is correct. The remaining work is not the formula; it is data-quality checking: correct PhilFCT match, cooked/raw state, edible portion, and realistic market unit for pricing.
