package com.pcosina.app

import com.google.gson.Gson
import com.pcosina.app.data.model.GrocerySnapshot
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryChecklistPersistenceTest {

    @Test
    fun grocerySnapshot_readsLegacyPayloadWithoutChecklistState() {
        val snapshot = Gson().fromJson(
            """{"planId":"legacy","items":[],"sources":{}}""",
            GrocerySnapshot::class.java,
        )

        assertTrue(snapshot.checkedItemNames.orEmpty().isEmpty())
        assertTrue(snapshot.pantryOptOutNames.orEmpty().isEmpty())
    }

    @Test
    fun grocerySnapshot_roundTripsChecklistPerPlan() {
        val expected = GrocerySnapshot(
            planId = "plan-1",
            items = emptyList(),
            sources = emptyMap(),
            checkedItemNames = setOf("Eggs", "Rice"),
            pantryOptOutNames = setOf("Garlic"),
        )

        val restored = Gson().fromJson(Gson().toJson(expected), GrocerySnapshot::class.java)

        assertEquals(expected.checkedItemNames, restored.checkedItemNames)
        assertEquals(expected.pantryOptOutNames, restored.pantryOptOutNames)
    }

    @Test
    fun groceryScreen_routesChecklistChangesThroughLocalSourceOfTruth() {
        val viewModel = readMain("ui", "GroceryViewModel.kt")
        val screen = readMain("ui", "screens", "GroceryRefinedScreen.kt")
        val userViewModel = readMain("ui", "UserViewModel.kt")

        assertTrue(viewModel.contains("fun updateChecklistState("))
        assertTrue(viewModel.contains("private val persistenceMutex = Mutex()"))
        assertTrue(viewModel.contains("val previousPlanId = _activePlanId.value"))
        assertTrue(viewModel.contains("checkedItemNames = _checkedItemNames.value"))
        assertTrue(viewModel.contains("private fun pruneChecklistToCurrentItems()"))
        assertTrue(screen.contains("groceryViewModel.updateChecklistState("))
        assertTrue(screen.contains("hasBudgetComparison = hasBudget && hasShoppingEstimate"))
        assertTrue(userViewModel.contains("}.asReversed()"))
    }

    private fun readMain(vararg parts: String): String =
        String(Files.readAllBytes(resolve("app", "src", "main", "java", "com", "pcosina", "app", *parts)))

    private fun resolve(vararg parts: String): Path {
        val direct = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(parent)) return parent
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
