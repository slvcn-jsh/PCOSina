package com.pcosina.app

import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.domain.PantryCoverageStatus
import com.pcosina.app.domain.PriceCatalog
import com.pcosina.app.domain.buildGroceryListEntries
import com.pcosina.app.domain.buildPantryCoverage
import com.pcosina.app.domain.canonicalGroceryKey
import com.pcosina.app.domain.canonicalGroceryName
import com.pcosina.app.domain.estimateGroceryCostAfterPantry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GroceryAggregationTest {

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
    fun canonicalGroceryKey_mapsFilipinoSynonymsToSameBaseIngredient() {
        assertEquals("garlic", canonicalGroceryKey("bawang"))
        assertEquals("garlic", canonicalGroceryKey("2 cloves garlic, minced"))
        assertEquals("Garlic", canonicalGroceryName("minced bawang"))
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
