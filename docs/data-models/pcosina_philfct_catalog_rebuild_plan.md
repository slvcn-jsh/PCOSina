# PCOSina PhilFCT-Aligned Meal Catalog Rebuild Plan

## Purpose

This plan converts the existing PCOSina Filipino recipe catalog into a nutritionist-reviewable meal catalog. The current recipe library is retained as the base, but each usable system meal must be standardized into a one-person complete meal plate with gram-based portions, PhilFCT ingredient matching, computed nutrition, realistic pricing, and clear meal categorization.

The goal is not to replace the Filipino recipe dataset entirely. The goal is to adapt it into the same evidence format used for the RND-reviewed meals.

## Current Baseline

- Active runtime catalog: `backend/recipes.json`
- Current recipe count: 1,130
- Backup created: `backups/recipe_catalog_20260716_205215`
- RND-style seed catalog: `backend/seed_data/pcosina_rnd_complete_meal_seed_v1.json`
- Computation checker: `scripts/compute_rnd_meal_seed.py`

## Reference Source

Primary nutrition reference:

- DOST-FNRI Philippine Food Composition Tables Online Database
- URL: `https://i.fnri.dost.gov.ph/fct/library`

PhilFCT is used because it is the official local Philippine food composition reference. The system should store the exact PhilFCT item name/code used for each ingredient so nutrition values are traceable.

## Target Meal Record

Each final system meal should contain:

- `id`
- `name`
- `mealType`: Breakfast, Lunch, Dinner, or Universal
- `tags`: Filipino, complete plate, PhilFCT-computable, allergy/restriction tags
- `baseRecipe`: original recipe name and source when adapted from the existing dataset
- `mainComponents`: plain-language plate components
- `pinggangPinoyGroups`
- `ingredients`
- `nonNutritiveItems`: water, salt/pepper to taste, optional seasonings with negligible nutrition
- `nutrition`: computed meal totals
- `pricing`: per-serving cost computation
- `reviewStatus`: draft, needs_match_review, needs_portion_review, RND_reviewed, approved

## Ingredient Record

Each ingredient should contain:

- `name`: user-facing ingredient name
- `portion_g`: one-person edible portion in grams
- `household_measure`: optional display measure, such as 1 pc or 1 tsp
- `philfct_name`: exact PhilFCT selected item
- `philfct_code`: exact PhilFCT code when available
- `per_100g`: kcal, protein, carbs, fat, fiber
- `match_status`: confirmed, needs_exact_philfct_code, review_preparation_form, review_raw_vs_cooked, review_edible_portion
- `remarks`: why the match was selected

## Nutrition Formula

For each ingredient:

```text
ingredient nutrient = (portion_g / 100) * PhilFCT value per 100g
```

For each meal:

```text
meal total = sum of all ingredient nutrient contributions
```

For each day:

```text
daily total = breakfast total + lunch total + dinner total
```

Water is listed for completeness but excluded from calorie and macronutrient totals. Salt and pepper may be listed as seasoning but should not be counted unless sodium tracking is added.

## Complete Meal Rules

A meal should not be treated as complete only because it is a Filipino dish. A final PCOSina meal should be structured as a plate:

- Go/carbohydrate: rice, oats, root crop, or equivalent
- Grow/protein: fish, chicken, egg, legumes, tofu, lean meat, or equivalent
- Glow/vegetables: vegetables in the dish or side component
- Fruit/additional glow: fruit where applicable
- Water: listed but excluded from nutrient totals

If the base recipe is incomplete, the adaptation process may add culturally familiar plate components. For example, a vegetable dish may be paired with rice and a protein component; a meat dish may be paired with vegetables and fruit.

## Automation Workflow

1. Read `backend/recipes.json`.
2. Preserve original recipe ID, name, ingredients, source dataset, and source serving count.
3. Parse source ingredient quantities.
4. Convert batch recipe quantities into one-person edible portions.
5. Normalize units into grams.
6. Add missing complete-plate components when the source recipe is not a complete meal.
7. Search PhilFCT for each ingredient.
8. Select a likely PhilFCT match using name, food form, cooking state, and edible portion context.
9. Compute nutrition using the PhilFCT formula.
10. Compute per-serving price using the reviewed market price rules.
11. Save the meal as draft if any portion or PhilFCT match is uncertain.
12. Export a review workbook for human/RND validation.
13. Import only approved meals into the runtime planner.

## Review Flags

A meal or ingredient must be flagged when:

- cooked food is matched to raw PhilFCT data
- fruit is matched to leaves, petioles, peel, or other non-target part
- munggo bean is matched to mung bean sprout without review
- meat/fish weight is unclear as purchased vs edible portion
- source recipe serving count is missing or inconsistent
- ingredient quantity is written only as "to taste"
- price is copied from whole recipe instead of per serving
- meal lacks a protein, vegetable, carbohydrate, or practical plate component
- meal violates its dietary tag, such as vegetarian meal containing pork or fish

## Runtime Import Rule

Do not overwrite `backend/recipes.json` directly during conversion. Create a new generated file first:

```text
backend/seed_data/pcosina_philfct_meal_catalog_v2.json
```

Then validate:

```text
python scripts/compute_rnd_meal_seed.py
```

Future validation scripts should check:

- no null nutrition values for approved meals
- every approved ingredient has `portion_g`
- every approved ingredient has `philfct_name`
- every approved meal has kcal, protein, carbs, fat, and fiber totals
- every approved meal has complete-plate grouping
- allergy/restriction tags agree with ingredient list
- cost is computed per serving

## Initial RND Seed Output

The first seed file contains three complete Filipino meal plates:

- Tortang Talong Breakfast Plate
- Grilled Chicken Pinakbet Plate
- Bangus with Monggo-Malunggay Plate

These are the reference examples for converting the wider recipe catalog.

The seed computation intentionally includes all listed nutritive ingredients. This corrects the earlier draft issue where lunch cooking oil appeared in the ingredient list but was not counted in the lunch nutrition total.

Computed daily result from the seed file:

| Nutrient | Target | Computed |
| --- | ---: | ---: |
| Energy | 1,800 kcal | 1,798.55 kcal |
| Protein | 90 g | 89.57 g |
| Carbohydrates | 225 g | 224.66 g |
| Fat | 60 g | 59.77 g |
| Fiber | 25-30 g | 26.67 g |

## Human Review Still Required

Automation can compute and prefill the catalog, but the following must still be reviewed:

- exact PhilFCT match where multiple entries exist
- cooked vs raw food form
- fruit vs leaves/petioles/sprouts
- edible portion vs as-purchased portion
- one-person portion realism
- final meal adequacy under RND judgment

This protects the thesis from claiming full professional diet prescription while still making the system nutrition data traceable, auditable, and much stronger than generic recipe estimates.
