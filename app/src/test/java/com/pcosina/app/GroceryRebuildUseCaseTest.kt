package com.pcosina.app

import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.domain.GroceryRebuildUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryRebuildUseCaseTest {

    private val groceryRebuildUseCase = GroceryRebuildUseCase()

    @Test
    fun rebuild_groupsMealSourcesByNormalizedNameAndAggregatesQuantities() {
        val rebuilt = groceryRebuildUseCase(
            mapOf(
                "breakfast" to listOf(
                    GroceryItemSource(name = " Eggs ", quantity = "2 pcs"),
                    GroceryItemSource(name = "Rice", quantity = "500 g"),
                ),
                "lunch" to listOf(
                    GroceryItemSource(name = "eggs", quantity = "1 pc"),
                ),
            )
        )

        val eggs = rebuilt.first { it.name == "Eggs" }
        val rice = rebuilt.first { it.name == "Rice" }

        assertEquals("2 pcs, 1 pc", eggs.quantity)
        assertEquals("500 g", rice.quantity)
        assertTrue(eggs.price > 0)
        assertTrue(rice.price > 0)
    }

    @Test
    fun rebuild_skipsBlankSourceNames() {
        val rebuilt = groceryRebuildUseCase(
            mapOf(
                "breakfast" to listOf(
                    GroceryItemSource(name = " ", quantity = "1 pc"),
                    GroceryItemSource(name = "Tomato", quantity = "2 pcs"),
                )
            )
        )

        assertEquals(1, rebuilt.size)
        assertEquals("Tomato", rebuilt.single().name)
    }
}
