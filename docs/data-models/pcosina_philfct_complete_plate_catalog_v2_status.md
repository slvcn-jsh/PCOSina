# PCOSina PhilFCT Complete-Plate Catalog v2 Draft Status

## What Was Generated

The current PCOSina recipe catalog was converted into a non-destructive v2 draft catalog that follows the RND meal-evaluation direction:

- Filipino meal base retained
- complete-plate structure added
- Pinggang Pinoy food groups represented
- one-person add-on portions expressed in grams
- PhilFCT-style nutrition computation used for added plate components
- current source nutrition preserved for each base dish
- review flags added where ingredient-level PhilFCT verification is still required

This output is a draft dataset for review and refinement. It has not replaced the runtime `backend/recipes.json`.

## Files Created

- Draft catalog: `backend/seed_data/pcosina_philfct_complete_plate_catalog_v2_draft.json`
- Review queue: `docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/pcosina_philfct_complete_plate_catalog_v2_review_queue.csv`
- Converter script: `scripts/build_philfct_complete_plate_catalog.py`
- RND seed catalog: `backend/seed_data/pcosina_rnd_complete_meal_seed_v1.json`
- RND computation checker: `scripts/compute_rnd_meal_seed.py`
- Original catalog backup: `backups/recipe_catalog_20260716_205215`

## Generated Count

| Item | Count |
| --- | ---: |
| Original current recipes | 1,130 |
| Added RND-style seed meals | 3 |
| Total v2 draft meals | 1,133 |

Meal type distribution:

| Meal Type | Count |
| --- | ---: |
| Breakfast | 358 |
| Lunch | 359 |
| Dinner | 370 |
| Universal | 46 |

## RND Seed Meals

The three RND-style meals are included as runtime candidates in the v2 draft:

- Tortang Talong Breakfast Plate
- Grilled Chicken Pinakbet Plate
- Bangus with Monggo-Malunggay Plate

Computed one-day nutrition from the seed file:

| Nutrient | Target | Computed |
| --- | ---: | ---: |
| Energy | 1,800 kcal | 1,798.55 kcal |
| Protein | 90 g | 89.57 g |
| Carbohydrates | 225 g | 224.66 g |
| Fat | 60 g | 59.78 g |
| Fiber | 25-30 g | 26.67 g |

## What The Converter Did

For each current recipe, the converter:

1. Preserved the original recipe ID, name, source ingredients, source dataset, and source servings.
2. Used the best currently available base-dish nutrition source:
   - Panlasang Pinoy correction table when available
   - local reference/API estimate table when available
   - runtime recipe nutrition when present
   - median imputation only when no usable nutrition existed
3. Detected whether the base dish already appears to contain:
   - Go/carbohydrate component
   - Grow/protein component
   - Glow/vegetable component
   - Fruit component
4. Added missing complete-plate components where needed:
   - cooked white rice as Go component
   - fruit by meal type, such as banana, papaya, or pineapple
   - pechay side when no vegetable was detected
   - water as a non-nutritive item
5. Computed added components using the same formula used in the RND evaluation packet:

```text
ingredient contribution = portion_g / 100 * PhilFCT value per 100g
```

6. Wrote review flags for uncertain records.

## Why Most Meals Still Need Review

The v2 draft intentionally does not claim that all 1,130 base recipes are already fully PhilFCT-computed. Most current recipes still have base-dish nutrition from older correction or estimate sources. The added plate components are PhilFCT-style, but the base dish still needs ingredient-level PhilFCT verification before the meal can be marked final.

Review status:

| Status | Count |
| --- | ---: |
| Draft ready for review | 23 |
| Draft needs review | 1,110 |

Top review flags:

| Review Flag | Count |
| --- | ---: |
| Base recipe nutrition needs PhilFCT rebuild | 953 |
| Added fruit component | 856 |
| Added Go/carbohydrate component | 568 |
| Complete plate needs RND review | 132 |
| Needs Grow/protein component review | 95 |
| Added Glow/vegetable component | 56 |

This is expected and useful. It gives the team a concrete review queue instead of hiding uncertainty.

## What This Means

The 1,130-meal catalog has now been adapted into a complete-plate draft format, but it is not yet a final dietitian-approved production dataset.

Safe wording:

> The current recipe catalog has been converted into a PhilFCT-aligned complete-plate draft dataset. The conversion preserves the Filipino recipe base, adds missing Pinggang Pinoy-style meal components where needed, includes the three RND-evaluated meals, and computes added components using gram-based PhilFCT values. Meals whose base recipes still rely on previous nutrition estimates are flagged for ingredient-level PhilFCT verification before final approval.

Do not say:

> All 1,130 meals are now fully PhilFCT-verified and RND-approved.

## Recommended Next Step

Use the review queue to prioritize recipes:

1. Start with high-usage or common Filipino meals.
2. Confirm one-person edible portions in grams.
3. Match each base ingredient to the exact PhilFCT food name/code.
4. Recompute base-dish nutrition from PhilFCT.
5. Confirm complete plate structure.
6. Mark as approved only after nutrition and portions are reviewed.

After enough meals are approved, the runtime planner can be switched from `backend/recipes.json` to a finalized file such as:

```text
backend/seed_data/pcosina_philfct_complete_plate_catalog_v2_approved.json
```

