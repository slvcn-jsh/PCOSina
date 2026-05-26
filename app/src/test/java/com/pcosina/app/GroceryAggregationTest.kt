package com.pcosina.app

import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.domain.PantryCoverageStatus
import com.pcosina.app.domain.buildGroceryListEntries
import com.pcosina.app.domain.buildPantryCoverage
import com.pcosina.app.domain.canonicalGroceryKey
import com.pcosina.app.domain.canonicalGroceryName
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
