package com.pcosina.app

import com.pcosina.app.data.model.PlannerGroceryOutputItem
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
import com.pcosina.app.domain.estimateGroceryCostAfterPantry
import org.junit.Assert.assertEquals
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
        assertEquals(0, entries.first { it.key == "water" }.estimatedCostPhp)
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
