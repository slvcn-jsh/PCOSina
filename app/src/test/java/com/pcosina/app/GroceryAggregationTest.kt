package com.pcosina.app

import com.pcosina.app.data.model.DummyData
import com.pcosina.app.domain.buildGroceryListEntries
import com.pcosina.app.domain.householdSizeLabel
import com.pcosina.app.domain.scaleQuantityText
import com.pcosina.app.domain.scaleNutritionPerMeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryAggregationTest {

    @Test
    fun scaleQuantityText_multipliesSimpleQuantitiesByHouseholdSize() {
        assertEquals("8 kg", scaleQuantityText("2 kg", 4))
        assertEquals("6 pcs", scaleQuantityText("3 pcs", 2))
    }

    @Test
    fun buildGroceryListEntries_groupsRepeatedIngredientsAndScalesTotals() {
        val entries = buildGroceryListEntries(
            items = listOf(
                DummyData.GroceryItem("Eggs", "2 pcs", 0, "Eggs & Dairy"),
                DummyData.GroceryItem("Eggs", "1 pc", 0, "Eggs & Dairy"),
                DummyData.GroceryItem("Rice", "500 g", 0, "Dry Goods"),
            ),
            householdSize = 2
        )

        val eggs = entries.first { it.name == "Eggs" }
        val rice = entries.first { it.name == "Rice" }

        assertEquals("6 pcs", eggs.quantityDisplay)
        assertEquals("1 kg", rice.quantityDisplay)
        assertTrue(eggs.estimatedCostPhp > 0)
        assertTrue(rice.estimatedCostPhp > 0)
    }

    @Test
    fun householdSizeLabel_formatsSinglesCouplesAndFamilies() {
        assertEquals("1 person", householdSizeLabel(1))
        assertEquals("2 people", householdSizeLabel(2))
        assertEquals("family of 5", householdSizeLabel(5))
    }

    @Test
    fun scaleNutritionPerMeal_multipliesPerPersonNutritionByHouseholdSize() {
        assertEquals(450, scaleNutritionPerMeal(450, 1))
        assertEquals(900, scaleNutritionPerMeal(450, 2))
        assertEquals(1800, scaleNutritionPerMeal(450, 4))
        assertEquals(2700, scaleNutritionPerMeal(450, 6))
    }

    @Test
    fun scaleQuantityText_handlesSupportedHouseholdBands() {
        assertEquals("1 pc", scaleQuantityText("1 pc", 1))
        assertEquals("2 pcs", scaleQuantityText("1 pc", 2))
        assertEquals("4 pcs", scaleQuantityText("1 pc", 4))
        assertEquals("6 pcs", scaleQuantityText("1 pc", 6))
    }
}
