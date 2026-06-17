# Canonical Ingredient Layer

Status: shadow/read-only rollout  
Introduced: 2026-06-14

## Purpose

The canonical ingredient layer gives recipe ingredient text a stable identity
for future pantry, allergen, nutrition, price, and grocery calculations.

It does not currently replace:

- planner `ING_SYNONYMS`
- allergen token/family matching
- keyword price rules
- recipe-level nutrition corrections
- `recipes.ingredients_json`

Those paths remain the production fallback until canonical mappings reach
reviewed coverage and parity gates pass.

## Tables

- `canonical_ingredients`: stable ingredient identity and basic classification.
- `ingredient_aliases`: exact normalized aliases with language, confidence, and source.
- `ingredient_unit_conversions`: ingredient-scoped unit conversions.
- `ingredient_nutrition_refs`: sourced per-100g nutrition references.
- `ingredient_price_refs`: sourced price observations by location, market, and date.
- `recipe_ingredient_links`: recipe occurrence mappings and parsed quantities.
- `ingredient_allergen_links`: many-to-many allergen family relationships.

The migration seeds canonical identities, aliases, metric conversions, allergen
relationships, and static baseline price references. It intentionally does not
seed ingredient nutrition values because the current repository does not
contain reviewed ingredient-level per-100g references.

## Resolution Contract

`backend/canonical_ingredients.py` resolves ingredient text deterministically:

1. remove quantity, unit, and preparation noise
2. match the longest normalized alias phrase
3. return `mapped` only when one canonical ID is strongest
4. return `ambiguous` when equally specific IDs remain
5. return `unmapped` when no alias matches

The resolver does not use fuzzy matching and does not guess a specific cut or
food form from a generic term. For example, `manok` maps to
`ing_chicken_generic`, while `chicken breast` maps to
`ing_chicken_breast_raw`.

## Audit

Run:

```powershell
python scripts/audit_ingredients.py --source json
```

The default audit assigns stable provisional IDs to phrases that do not have a
curated alias. Use `--strict-only` to report only curated resolver coverage.

Generated files under `backend/output/ingredient_audit/`:

- `raw_ingredient_occurrences.csv`
- `unique_raw_ingredient_strings.csv`
- `ingredient_token_frequency.csv`
- `unmapped_ingredients.csv`
- `ambiguous_ingredients.csv`
- `top_ingredients_by_recipe_count.csv`
- `recipes_by_mapping_coverage.csv`
- `mapping_summary.json`

The output directory is ignored because reports are local generated artifacts.
The audit script does not write `recipe_ingredient_links`.

Persist complete shadow links for the active database catalog with:

```powershell
python scripts/sync_recipe_ingredient_links.py --require-complete
```

This command is idempotent. It replaces each recipe's links and creates stable
provisional ingredient records for unresolved full phrases.

## Initial Baseline

The 2026-06-14 JSON catalog scan found:

- 1,130 recipes
- 12,487 ingredient occurrences
- 7,570 mapped occurrences
- 4,781 unmapped occurrences
- 136 ambiguous occurrences
- 60.62% initial identity coverage

This is a seed baseline, not a production-readiness claim.

## Complete Classification

After the autonomous seed expansion and provisional classification pass on
2026-06-14:

- 12,487/12,487 occurrences have an ingredient ID
- 1,130/1,130 recipes have persisted shadow links
- classification coverage is 100%
- 9,833 occurrences use curated canonical identities
- 2,654 occurrences use stable provisional identities
- curated identity coverage is 78.75%
- no link has a null ingredient ID

Provisional identities are accepted classifications, not verified nutrition or
price references. They preserve full catalog traceability without silently
claiming that compound or alternative phrases are equivalent to a specific raw
food.

The frozen 20-profile planner parity benchmark passed 20/20 after persistence:
average 316 ms, P95 432 ms, and maximum 478 ms. Planner runtime behavior remains
unchanged because canonical links are not yet used by Stage 1 or Stage 2.

## Stage 1 Integration

Stage 1 canonical features are available behind:

```json
{
  "stage1": {
    "canonical_features_enabled": true
  }
}
```

The flag defaults to `true` for development, staging, production, and older
stored policies that do not yet contain the field. Setting it to `false` is an
explicit rollback/diagnostic control, not the normal runtime mode.

When enabled:

- recipe loading attaches canonical IDs, curated names, and allergen families
  using one bulk database query
- static Stage 1 features union curated canonical tokens with legacy tokens
- curated canonical allergen families may strengthen hard allergy filtering
- pantry entries and recipes use canonical ID overlap when both resolve
- legacy token pantry matching remains the deterministic fallback
- provisional ingredient identities never introduce hard allergen claims

The 20-profile, three-run comparison produced:

| Mode | Runs | Success | Average | P95 | Max | Hard violations |
|---|---:|---:|---:|---:|---:|---:|
| Canonical off | 60 | 60/60 | 311 ms | 472 ms | 567 ms | 0 |
| Canonical on | 60 | 60/60 | 312 ms | 471 ms | 568 ms | 0 |

Canonical Stage 1 is runtime-neutral in this benchmark. It can change selected
recipes because pantry, allergen, protein-group, and vegetable evidence is more
specific.

Before default activation, pantry matching was made monotonic: canonical
overlap can add matches, but the planner keeps the greater of canonical and
legacy overlap. Canonical evidence therefore cannot remove an existing legacy
pantry match. Curated allergen evidence supplements rather than replaces the
legacy hard filter, and provisional mappings remain excluded from hard claims.

Canonical Stage 1 is now the normal runtime path. The legacy path remains
available only as a deterministic rollback while coverage and source references
continue to mature.

Loading the complete 1,130-recipe SQLite catalog with attached canonical
metadata averaged approximately 111 ms over ten local runs.

The final sequential default-on validation passed 60/60 frozen-profile runs
with zero hard-constraint violations, average 328 ms, P95 472 ms, and maximum
1,167 ms. The explicit legacy-off comparison passed 60/60 with average 291 ms,
P95 434 ms, and maximum 459 ms. Two default-on solver-search outliers increased
the average but remained below all latency gates.

## Adoption Gates

Canonical IDs may become a preferred runtime path only after:

1. high-frequency aliases are reviewed
2. safety-sensitive ambiguities are resolved
3. recipe-link generation is idempotent and reviewable
4. allergy and restriction parity tests pass
5. pantry and grocery parity tests pass
6. price and nutrition references have explicit provenance
7. deterministic keyword/token fallback remains available

## Phase 3: Nutrition Validation

The ingredient-level computation pipeline is implemented:

- recipe links persist parsed quantities and normalized grams when conversion is
  available
- canonical nutrition references store per-100g nutrients, source identity,
  confidence, and review status
- `scripts/validate_canonical_recipe_nutrition.py` computes per-serving recipe
  nutrition and reports quantity, reference, and reviewed-reference coverage
- estimated local reference profiles remain `pending_review`; they are useful
  for gap analysis but do not count as validated nutrition

The 2026-06-14 audit reports:

- 1,130 recipes checked
- 84.98% average quantity coverage
- 56.36% average nutrition-reference coverage
- 4 recipes fully computable from current estimates
- 0 recipes fully computable from reviewed/source-verified references

Recipe expansion therefore remains closed. The gate requires at least 100
recipes with complete quantities and reviewed/source-verified references.

## Phase 4: Price Intelligence

Canonical pricing now supports:

- versioned NCR wet-market references
- explicit grocery estimates
- source dates and confidence
- admin overrides
- owner-scoped user overrides
- deterministic precedence: user, admin, requested market, baseline
- one bulk canonical-price lookup per pricing context, followed by the existing
  keyword/static fallback

All 26 priority ingredients have wet-market and grocery references, so the
price-readiness gate passes. Grocery values currently derived by applying a
documented multiplier are labeled low-confidence estimates, not independent
store observations.

Canonical pricing initially exposed an alias-resolution hotspot because the
default alias index was rebuilt for each ingredient. Reusing the immutable
default index restored planner performance. The final three-run, 20-profile
benchmark passed 60/60 plans with average 345 ms, P95 486 ms, maximum 527 ms,
and no failed plans.

## Phase 5: Recipe Expansion

No recipes were added in this phase. Price readiness passes, but nutrition
validation does not. Expanding the catalog before the nutrition gate passes
would increase the number of recipes requiring correction and would weaken,
rather than improve, solver input quality.
