package com.pcosina.app

import com.pcosina.app.data.model.PlannerGroceryOutputItem
import com.pcosina.app.data.model.PlannerGroceryOutput
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.domain.PantryCoverageStatus
import com.pcosina.app.domain.PriceCatalog
import com.pcosina.app.domain.GroceryListEntry
import com.pcosina.app.domain.alignGroceryEntriesWithAuthority
import com.pcosina.app.domain.alignGroceryEstimateWithAuthority
import com.pcosina.app.domain.buildGroceryListEntries
import com.pcosina.app.domain.buildGroceryListEntriesFromPlanner
import com.pcosina.app.domain.buildPantryCoverage
import com.pcosina.app.domain.canonicalGroceryKey
import com.pcosina.app.domain.canonicalGroceryName
import com.pcosina.app.domain.correctedAuthoritativeGroceryEstimate
import com.pcosina.app.domain.shouldTrustBackendGroceryPricing
import com.pcosina.app.domain.normalizeGroceryOutputPricingForDisplay
import com.pcosina.app.domain.resolveDisplayGroceryEstimate
import com.pcosina.app.domain.requiresGroceryPurchase
import com.pcosina.app.domain.estimateGroceryCostAfterPantry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GroceryAggregationTest {

    @Test
    fun alignGroceryEstimateWithAuthority_preservesBackendBaselineAndPantryShare() {
        assertEquals(
            180,
            alignGroceryEstimateWithAuthority(
                localAmountPhp = 200,
                localFullEstimatePhp = 200,
                authoritativeFullEstimatePhp = 180,
            )
        )
        assertEquals(
            45,
            alignGroceryEstimateWithAuthority(
                localAmountPhp = 50,
                localFullEstimatePhp = 200,
                authoritativeFullEstimatePhp = 180,
            )
        )
    }

    @Test
    fun alignGroceryEstimateWithAuthority_keepsFullyCoveredListAtZero() {
        assertEquals(
            0,
            alignGroceryEstimateWithAuthority(
                localAmountPhp = 0,
                localFullEstimatePhp = 200,
                authoritativeFullEstimatePhp = 180,
            )
        )
    }

    @Test
    fun alignGroceryEstimateWithAuthority_usesLocalEstimateWithoutBackendTotal() {
        assertEquals(
            50,
            alignGroceryEstimateWithAuthority(
                localAmountPhp = 50,
                localFullEstimatePhp = 200,
                authoritativeFullEstimatePhp = null,
            )
        )
    }

    @Test
    fun alignGroceryEstimateWithAuthority_honorsZeroBackendTotal() {
        assertEquals(
            0,
            alignGroceryEstimateWithAuthority(
                localAmountPhp = 50,
                localFullEstimatePhp = 200,
                authoritativeFullEstimatePhp = 0,
            )
        )
    }

    @Test
    fun alignGroceryEntriesWithAuthority_makesRowsSumToBackendTotalAndKeepsWaterFree() {
        val entries = listOf(
            GroceryListEntry("egg", "Eggs", "Eggs & Dairy", "6 pcs", 42, 1),
            GroceryListEntry("rice", "Rice", "Dry Goods", "1 kg", 60, 1),
            GroceryListEntry("water", "Water", "Beverages", "7 L", 0, 7),
        )

        val aligned = alignGroceryEntriesWithAuthority(entries, authoritativeFullEstimatePhp = 150)

        assertEquals(150, aligned.sumOf { it.estimatedCostPhp })
        assertEquals(0, aligned.first { it.key == "water" }.estimatedCostPhp)
    }

    @Test
    fun buildGroceryListEntries_groupsRepeatedIngredientsForPrimaryUserTotals() {
        val entries = buildGroceryListEntries(
            items = listOf(
                DummyData.GroceryItem("Eggs", "2 pcs", 0, "Eggs & Dairy"),
                DummyData.GroceryItem("Eggs", "1 pc", 0, "Eggs & Dairy"),
                DummyData.GroceryItem("Rice", "500 g", 0, "Dry Goods"),
            )
        )

        val eggs = entries.first { it.name == "Eggs" }
        val rice = entries.first { it.name == "Rice" }

        assertEquals("3 pcs", eggs.quantityDisplay)
        assertEquals("500 g", rice.quantityDisplay)
        assertTrue(eggs.estimatedCostPhp > 0)
        assertTrue(rice.estimatedCostPhp > 0)
    }

    @Test
    fun buildGroceryListEntries_doesNotCapWeeklyQuantitiesAtTwoUnits() {
        val entries = buildGroceryListEntries(
            items = listOf(DummyData.GroceryItem("Eggs", "12 pcs", 0, "Eggs & Dairy"))
        )

        assertEquals("12 pcs", entries.single().quantityDisplay)
        assertEquals(96, entries.single().estimatedCostPhp)
    }

    @Test
    fun buildGroceryListEntries_canonicalizesSynonymsAndPreparationWords() {
        val entries = buildGroceryListEntries(
            items = listOf(
                DummyData.GroceryItem("bawang", "3 cloves", 0, "Produce"),
                DummyData.GroceryItem("minced garlic", "2 tbsp", 0, "Produce"),
                DummyData.GroceryItem("garlic cloves", "1 clove", 0, "Produce"),
            )
        )

        assertEquals(1, entries.size)
        assertEquals("Garlic", entries.single().name)
        assertEquals("50 g", entries.single().quantityDisplay)
    }

    @Test
    fun buildGroceryListEntriesFromPlanner_preservesBackendKeysCostsAndWaterSpinachIdentity() {
        val entries = buildGroceryListEntriesFromPlanner(
            listOf(
                PlannerGroceryOutputItem(
                    key = "kangkong",
                    name = "Kangkong",
                    quantity = "180 g",
                    estimatedCostPhp = 14,
                    category = "Produce",
                    originalNames = listOf("water spinach"),
                ),
                PlannerGroceryOutputItem(
                    key = "water",
                    name = "Water",
                    quantity = "7 L",
                    estimatedCostPhp = 0,
                    category = "Beverages",
                    originalNames = listOf("water"),
                ),
            )
        )

        assertEquals(setOf("kangkong", "water"), entries.map { it.key }.toSet())
        assertEquals("Kangkong", entries.first { it.key == "kangkong" }.name)
        assertEquals(14, entries.first { it.key == "kangkong" }.estimatedCostPhp)
        val water = entries.first { it.key == "water" }
        assertEquals(0, water.estimatedCostPhp)
        assertEquals(0.0, water.unitPricePhp)
        assertEquals("l", water.priceUnit)
        assertEquals("Household tap water baseline", water.priceSourceLabel)
        assertEquals("high", water.priceConfidence)
        assertEquals("household_not_purchased", water.purchaseMode)
        assertFalse(water.requiresGroceryPurchase())
        assertTrue(entries.first { it.key == "kangkong" }.requiresGroceryPurchase())
    }

    @Test
    fun buildGroceryListEntriesFromPlanner_preservesBangusFilletPriceEvidence() {
        val entry = buildGroceryListEntriesFromPlanner(
            listOf(
                PlannerGroceryOutputItem(
                    key = "bangus fillet",
                    name = "Bangus Fillet",
                    quantity = "110 g",
                    requiredQuantity = "110 g",
                    purchaseQuantity = "110 g",
                    purchaseMode = "weighed_to_order",
                    estimatedCostPhp = 43,
                    unitPricePhp = 388.0,
                    priceUnit = "kg",
                    category = "Meat/Seafood",
                    sourceLabel = "Metro Retail fresh boneless bangus listing (2026-09-08)",
                    confidence = "medium",
                )
            )
        ).single()

        assertEquals("bangus fillet", entry.key)
        assertEquals("110 g", entry.quantityDisplay)
        assertEquals(43, entry.estimatedCostPhp)
        assertEquals(388.0, entry.unitPricePhp!!, 0.0)
        assertEquals("kg", entry.priceUnit)
        assertEquals("weighed_to_order", entry.purchaseMode)
        assertTrue(entry.priceSourceLabel.orEmpty().contains("fresh boneless bangus"))
    }

    @Test
    fun buildGroceryListEntriesFromPlanner_repricesLegacyBangusFillet() {
        val entry = buildGroceryListEntriesFromPlanner(
            items = listOf(
                PlannerGroceryOutputItem(
                    key = "bangus fillet",
                    name = "Bangus Fillet",
                    quantity = "110 g",
                    estimatedCostPhp = 27,
                    category = "Meat/Seafood",
                )
            ),
            trustBackendPrices = false,
        ).single()

        assertEquals(43, entry.estimatedCostPhp)
        assertEquals(388.0, entry.unitPricePhp!!, 0.0)
        assertEquals("weighed_to_order", entry.purchaseMode)
    }

    @Test
    fun buildGroceryListEntriesFromPlanner_repricesLegacyRowsFromCurrentOfflineCatalog() {
        val legacyItems = listOf(
            PlannerGroceryOutputItem(
                key = "pechay",
                name = "Pechay",
                quantity = "1.39 kg",
                estimatedCostPhp = 139,
                category = "Produce",
                originalNames = listOf("pechay"),
            ),
            PlannerGroceryOutputItem(
                key = "string beans",
                name = "String Beans",
                quantity = "1.08 kg",
                estimatedCostPhp = 148,
                category = "Produce",
                originalNames = listOf("sitaw"),
            ),
            PlannerGroceryOutputItem(
                key = "tomato",
                name = "Tomato",
                quantity = "1.06 kg",
                estimatedCostPhp = 78,
                category = "Produce",
                originalNames = listOf("kamatis"),
            ),
        )

        val entries = buildGroceryListEntriesFromPlanner(legacyItems, trustBackendPrices = false)

        assertEquals(282, entries.first { it.key == "pechay" }.estimatedCostPhp)
        assertEquals(200, entries.first { it.key == "string beans" }.estimatedCostPhp)
        assertEquals(116, entries.first { it.key == "tomato" }.estimatedCostPhp)
        assertEquals(598, entries.sumOf { it.estimatedCostPhp })
    }

    @Test
    fun backendPricingTrust_requiresCurrentVersionOrNonOlderReferenceDate() {
        assertFalse(shouldTrustBackendGroceryPricing(null, null))
        assertFalse(shouldTrustBackendGroceryPricing("legacy-v1", "2026-08-31"))
        assertTrue(
            shouldTrustBackendGroceryPricing(
                PriceCatalog.CURRENT_CATALOG_VERSION,
                PriceCatalog.CURRENT_REFERENCE_DATE,
            )
        )
        assertTrue(shouldTrustBackendGroceryPricing("future-v2", "2026-09-13"))
    }

    @Test
    fun normalizeGroceryOutputPricingForDisplay_updatesLegacyTotalsAndBudgetState() {
        val output = PlannerGroceryOutput(
            estimatedTotalPhp = 2_267,
            finalGroceryEstimatePhp = 2_267,
            weeklyBudgetPhp = 2_200,
            withinBudget = false,
            budgetDeltaPhp = -67,
            items = listOf(
                PlannerGroceryOutputItem(
                    key = "pechay",
                    name = "Pechay",
                    quantity = "1.39 kg",
                    estimatedCostPhp = 139,
                    category = "Produce",
                    originalNames = listOf("pechay"),
                ),
                PlannerGroceryOutputItem(
                    key = "water",
                    name = "Water",
                    quantity = "2.1 kg",
                    estimatedCostPhp = 185,
                    category = "Beverages",
                    originalNames = listOf("water"),
                ),
            ),
        )

        val normalized = normalizeGroceryOutputPricingForDisplay(output)!!

        assertEquals(282, resolveDisplayGroceryEstimate(output))
        assertEquals(282, normalized.estimatedTotalPhp)
        assertEquals(282, normalized.finalGroceryEstimatePhp)
        assertTrue(normalized.withinBudget == true)
        assertEquals(1_918, normalized.budgetDeltaPhp)
        assertEquals(1_918, normalized.budgetGapPhp)
        assertEquals(null, normalized.pricingCatalogVersion)
    }

    @Test
    fun buildGroceryListEntriesFromPlanner_repairsLegacyCompanionWaterOnly() {
        val water = PlannerGroceryOutputItem(
            key = "water",
            name = "Water",
            quantity = "2.1 kg",
            estimatedCostPhp = 185,
            category = "Beverages",
            originalNames = listOf("water"),
        )
        val waterSpinach = PlannerGroceryOutputItem(
            key = "kangkong",
            name = "Water Spinach",
            quantity = "180 g",
            estimatedCostPhp = 22,
            category = "Produce",
            originalNames = listOf("water spinach"),
        )

        val entries = buildGroceryListEntriesFromPlanner(listOf(water, waterSpinach))

        assertEquals("2.1 L", entries.first { it.key == "water" }.quantityDisplay)
        assertEquals(0, entries.first { it.key == "water" }.estimatedCostPhp)
        assertEquals(22, entries.first { it.key == "kangkong" }.estimatedCostPhp)
        assertEquals(2_082, correctedAuthoritativeGroceryEstimate(listOf(water, waterSpinach), 2_267))
    }

    @Test
    fun buildGroceryListEntriesFromPlanner_keepsCompoundWaterProductsPriced() {
        val coconutWater = PlannerGroceryOutputItem(
            key = "water",
            name = "Coconut Water",
            quantity = "1 L",
            estimatedCostPhp = 75,
            category = "Beverages",
            originalNames = listOf("coconut water"),
        )

        val entry = buildGroceryListEntriesFromPlanner(listOf(coconutWater)).single()

        assertEquals(75, entry.estimatedCostPhp)
        assertEquals(900, correctedAuthoritativeGroceryEstimate(listOf(coconutWater), 900))
    }

    @Test
    fun buildGroceryListEntriesFromPlanner_convertsGlassToVolume() {
        val water = PlannerGroceryOutputItem(
            key = "water",
            name = "Water",
            quantity = "1 glass",
            estimatedCostPhp = 0,
            category = "Beverages",
            originalNames = listOf("water"),
        )

        val entry = buildGroceryListEntriesFromPlanner(listOf(water)).single()

        assertEquals("240 ml", entry.quantityDisplay)
        assertEquals(0, entry.estimatedCostPhp)
    }

    @Test
    fun offlinePriceCatalog_usesDatedRetailPricesWithoutDoubleAdjustment() {
        val tomato = PriceCatalog.estimatePriceExplanation("Tomato", "1 kg", monthIndex = 9)

        assertEquals(109, tomato.pricePhp)
        assertEquals(1.0, tomato.marketMultiplier, 0.0)
        assertEquals(1.0, tomato.tingiMultiplier, 0.0)
        assertTrue(PriceCatalog.estimatePriceDetail("Water Spinach", "1 kg").first > 0)
        assertTrue(PriceCatalog.estimatePriceDetail("Canned tuna in water", "1 can").first > 0)
        assertEquals(0, PriceCatalog.estimatePriceDetail("Water", "1 glass").first)
    }

    @Test
    fun offlinePriceCatalog_prefersSpecificPackagedRuleOverBroadFishRule() {
        val cannedTuna = PriceCatalog.estimatePriceExplanation("Canned tuna", "1 can", monthIndex = 9)

        assertEquals("Canned/Packaged", cannedTuna.category)
        assertEquals(28, cannedTuna.pricePhp)
    }

    @Test
    fun offlinePriceCatalog_coversRemainingDatedDaReferencesAndPluralMetricUnits() {
        assertEquals(468, PriceCatalog.estimatePriceDetail("Pusit", "1 kilogram").first)
        assertEquals(133, PriceCatalog.estimatePriceDetail("White onions", "1 kilograms").first)
        assertEquals(100, PriceCatalog.estimatePriceDetail("Canola oil", "1 litre").first)
        assertEquals(81, PriceCatalog.estimatePriceDetail("Granulated white sugar", "1 kilogram").first)
        assertEquals(0, PriceCatalog.estimatePriceDetail("Tap water", "2.1 liters").first)
    }

    @Test
    fun buildGroceryListEntries_extractsQuantityFromIngredientNameWhenQuantityIsBlank() {
        val entries = buildGroceryListEntries(
            items = listOf(
                DummyData.GroceryItem("3 cloves garlic, minced", "", 0, "Produce"),
                DummyData.GroceryItem("bawang", "2 cloves", 0, "Produce"),
            )
        )

        assertEquals(1, entries.size)
        assertEquals("Garlic", entries.single().name)
        assertEquals("25 g", entries.single().quantityDisplay)
    }

    @Test
    fun buildGroceryListEntries_pricesGarlicFromAggregatedWeightNotRepeatedCloves() {
        val entries = buildGroceryListEntries(
            items = listOf(
                DummyData.GroceryItem("garlic", "26 cloves", 0, "Produce"),
            )
        )

        val garlic = entries.single()
        val aggregatedEstimate = PriceCatalog.estimatePriceDetail("Garlic", "130 g").first

        assertEquals("Garlic", garlic.name)
        assertEquals("130 g", garlic.quantityDisplay)
        assertEquals(aggregatedEstimate.coerceAtLeast(5), garlic.estimatedCostPhp)
        assertTrue("Garlic 130 g should not price like many generic produce pieces.", garlic.estimatedCostPhp < 80)
    }

    @Test
    fun buildGroceryListEntries_doesNotParseLargeAsLiters() {
        val entries = buildGroceryListEntries(
            items = listOf(
                DummyData.GroceryItem("1 large bangus milkfish, cleaned and sliced", "", 0, "Meat/Seafood"),
            )
        )

        assertEquals("Bangus", entries.single().name)
        assertEquals("450 g", entries.single().quantityDisplay)
    }

    @Test
    fun canonicalGroceryKey_mapsFilipinoSynonymsToSameBaseIngredient() {
        assertEquals("garlic", canonicalGroceryKey("bawang"))
        assertEquals("garlic", canonicalGroceryKey("2 cloves garlic, minced"))
        assertEquals("Garlic", canonicalGroceryName("minced bawang"))
        assertEquals("banana", canonicalGroceryKey("saging"))
        assertEquals("Banana", canonicalGroceryName("ripe saging"))
        assertEquals("kangkong", canonicalGroceryKey("water spinach"))
        assertEquals("water", canonicalGroceryKey("tap water"))
    }

    @Test
    fun buildPantryCoverage_marksFullOnlyWhenQuantityCoversNeed() {
        val entries = buildGroceryListEntries(
            items = listOf(
                DummyData.GroceryItem("Eggs", "3 pcs", 0, "Eggs & Dairy"),
                DummyData.GroceryItem("Rice", "500 g", 0, "Dry Goods"),
            )
        )

        val coverage = buildPantryCoverage(
            groceryEntries = entries,
            pantryEntries = listOf(
                PantryEntry(name = "eggs", quantity = "6 pcs"),
                PantryEntry(name = "bigas", quantity = "250 g"),
            ),
            today = LocalDate.of(2026, 5, 26)
        )

        assertEquals(PantryCoverageStatus.Full, coverage["Eggs"]?.status)
        assertEquals(PantryCoverageStatus.Partial, coverage["Rice"]?.status)
        assertEquals("250 g", coverage["Rice"]?.remainingQuantityDisplay)
    }

    @Test
    fun buildPantryCoverage_interpretsGarlicPieceAsSmallPartialWeight() {
        val entries = buildGroceryListEntries(
            items = listOf(DummyData.GroceryItem("garlic", "100 g", 0, "Produce"))
        )

        val coverage = buildPantryCoverage(
            groceryEntries = entries,
            pantryEntries = listOf(PantryEntry(name = "garlic", quantity = "1 piece")),
            today = LocalDate.of(2026, 5, 26)
        )

        assertEquals(PantryCoverageStatus.Partial, coverage["Garlic"]?.status)
        assertEquals("5 g", coverage["Garlic"]?.pantryQuantityDisplay)
        assertEquals("95 g", coverage["Garlic"]?.remainingQuantityDisplay)
    }

    @Test
    fun buildPantryCoverage_usesStructuredPantryAmountAndUnit() {
        val entries = buildGroceryListEntries(
            items = listOf(DummyData.GroceryItem("garlic", "100 g", 0, "Produce"))
        )

        val coverage = buildPantryCoverage(
            groceryEntries = entries,
            pantryEntries = listOf(PantryEntry(name = "garlic", amount = 80.0, unit = "g")),
            today = LocalDate.of(2026, 5, 26)
        )

        assertEquals(PantryCoverageStatus.Partial, coverage["Garlic"]?.status)
        assertEquals("80 g", coverage["Garlic"]?.pantryQuantityDisplay)
        assertEquals("20 g", coverage["Garlic"]?.remainingQuantityDisplay)
    }

    @Test
    fun buildPantryCoverage_prefersStructuredPantryAmountOverLegacyQuantity() {
        val entries = buildGroceryListEntries(
            items = listOf(DummyData.GroceryItem("garlic", "100 g", 0, "Produce"))
        )

        val coverage = buildPantryCoverage(
            groceryEntries = entries,
            pantryEntries = listOf(
                PantryEntry(name = "garlic", quantity = "1 g", amount = 120.0, unit = "g")
            ),
            today = LocalDate.of(2026, 5, 26)
        )

        assertEquals(PantryCoverageStatus.Full, coverage["Garlic"]?.status)
        assertEquals("120 g", coverage["Garlic"]?.pantryQuantityDisplay)
    }

    @Test
    fun estimateGroceryCostAfterPantry_pricesOnlyRemainingPartialQuantity() {
        val entries = buildGroceryListEntries(
            items = listOf(DummyData.GroceryItem("Rice", "500 g", 0, "Dry Goods"))
        )
        val rice = entries.single()
        val coverage = buildPantryCoverage(
            groceryEntries = entries,
            pantryEntries = listOf(PantryEntry(name = "bigas", quantity = "250 g")),
            today = LocalDate.of(2026, 5, 26)
        )

        val remainingCost = estimateGroceryCostAfterPantry(
            entry = rice,
            pantryCoverage = coverage["Rice"],
            coveredOrBought = false
        )

        assertTrue(remainingCost < rice.estimatedCostPhp)
        assertEquals(0, estimateGroceryCostAfterPantry(rice, coverage["Rice"], coveredOrBought = true))
    }

    @Test
    fun buildPantryCoverage_keepsNameOnlyMatchesFromAutoCoverage() {
        val entries = buildGroceryListEntries(
            items = listOf(DummyData.GroceryItem("Tomato", "2 pcs", 0, "Produce"))
        )

        val coverage = buildPantryCoverage(
            groceryEntries = entries,
            pantryEntries = listOf(PantryEntry(name = "kamatis")),
            today = LocalDate.of(2026, 5, 26)
        )

        assertEquals(PantryCoverageStatus.NameOnly, coverage["Tomato"]?.status)
        assertTrue(coverage["Tomato"]?.autoCovered == false)
    }

    @Test
    fun buildPantryCoverage_ignoresExpiredPantryEntries() {
        val entries = buildGroceryListEntries(
            items = listOf(DummyData.GroceryItem("Eggs", "2 pcs", 0, "Eggs & Dairy"))
        )

        val coverage = buildPantryCoverage(
            groceryEntries = entries,
            pantryEntries = listOf(PantryEntry(name = "eggs", quantity = "6 pcs", expiryDate = "2026-05-20")),
            today = LocalDate.of(2026, 5, 26)
        )

        assertTrue(coverage.isEmpty())
    }
}
