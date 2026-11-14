# Grocery Pricing Contract

Updated: 2026-09-09

## Purpose

PCOSina estimates the cost of the ingredients required by a generated plan. It is a planning estimate, not a live checkout quote or a claim that every quantity can be bought as a pre-packed retail item.

The backend is the authority for generated-plan grocery identities, quantities, item estimates, and totals. Android may adapt presentation and apply pantry or bought-state deductions, but it must not silently replace the backend price catalog or allow displayed row totals to diverge from the authoritative total.

## Quantity Model

Each grocery item can expose these separate concepts:

- `requiredQuantity`: the aggregated amount required by the recipes.
- `purchaseQuantity`: a verified market-facing amount when PCOSina has a supported purchase rule.
- `purchaseMode`: how the item is expected to be obtained.
- `estimatedCostPhp`: the item estimate produced by the backend price authority.
- `unitPricePhp` and `priceUnit`: the reference rate and unit used to calculate the estimate.

Supported purchase modes are:

- `count_purchase`: currently used for eggs, where required grams are converted to whole pieces and rounded up.
- `weighed_to_order`: used for verified weighed items such as fresh boneless bangus fillet.
- `household_not_purchased`: used for recipe companion tap water; it is displayed in liters and contributes zero to the grocery budget.
- `required_quantity_retail_equivalent`: the recipe requirement is priced against a unit reference, but PCOSina does not claim a verified package size.

Package rounding must be evidence-backed. PCOSina must not invent tray, bottle, can, pack, or sachet sizes merely to make a row look purchasable. Additional purchase rules require a reviewed package size, unit price, source date, market type, and location.

## Price Authority

For a mapped canonical ingredient, active references are selected in this order:

1. A non-expired override owned by the current Firebase user.
2. An active administrator override.
3. A reference for the requested market scope.
4. A canonical baseline.
5. A reviewed or static keyword rule when no canonical reference resolves.
6. An offline category-average fallback when no more specific rule matches.

Within a tier, newer source dates and updates win. User overrides are isolated by Firebase UID and expire after 180 days. An unusual user-entered price produces a warning and confirmation path instead of an absolute rejection because legitimate local, sale, wholesale, and seasonal prices can fall outside a global range.

The generated plan stores a price snapshot. Changing a personal price affects newly generated plans; it does not rewrite the historical estimate of an already-generated plan.

## Evidence Labels

- **Personal price**: the user's own recent observation. Most representative for that user, but not independently audited by PCOSina.
- **DA-AMAS market evidence**: dated government market monitoring for the stated commodity, location, and unit.
- **Named retail observation**: dated evidence from a named supermarket or retailer. It may represent a higher-cost channel and a more specific processed form, such as boneless fillet.
- **Reviewed market rule**: an administrator-reviewed reference with source metadata.
- **Canonical/static baseline**: a maintained PCOSina reference used when dated observations are unavailable.
- **Offline category average**: the least-specific fallback. It is deterministic and traceable, but it is not audited SRP and must be labeled accordingly.

## Required Invariants

- `estimatedTotalPhp == sum(items[].estimatedCostPhp)` in every backend grocery output.
- Water resolves as water, is displayed in liters, uses `household_not_purchased`, and costs `0`.
- Water spinach resolves as kangkong and must never collapse into water.
- Android rows, remaining-to-buy total, pantry deductions, and bought deductions share one aligned item-cost basis.
- A user price must never leak to another user or enter a cache key shared by different users.
- Allergies, exclusions, pantry feasibility, budget ceilings, nutrition bounds, and MILP constraints remain deterministic hard constraints regardless of price personalization.

## Current Evidence Boundary

The 2026-09-09 canonical price-readiness audit reports paired wet-market plus grocery evidence for `0/27` priority ingredients. Administrator and personal override mechanisms are implemented, but the monthly paired-market gate is not ready.

Therefore PCOSina may describe current prices as transparent estimates with identified sources. It must not claim comprehensive real-time pricing, complete SRP coverage, or verified package availability. Expanding the purchase-unit registry and claiming stronger price accuracy remain blocked on reviewed market evidence, not on solver or UI code.

## Verified Examples

With the current seeded references:

- Household tap water, `2.1 L`: `PHP 0`, high-confidence household baseline.
- Fresh boneless bangus fillet, `110 g`: `PHP 43`, based on a dated Metro Retail reference of `PHP 388/kg`.
- Whole bangus and boneless bangus fillet are distinct price identities and must not share an assumed reference.
