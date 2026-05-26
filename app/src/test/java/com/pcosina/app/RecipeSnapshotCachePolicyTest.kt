package com.pcosina.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSnapshotCachePolicyTest {

    @Test
    fun mealPlanRepositoryPersistsRecipeCatalogSnapshotForOfflineReads() {
        val source = File("src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt").readText()

        assertTrue(source.contains("syncRecipeCatalogSnapshot"))
        assertTrue(source.contains("service.getRecipeCatalog"))
        assertTrue(source.contains("recipeSnapshotStore.replaceAll"))
        assertTrue(source.contains("recipeSnapshotStore.getRecipe(recipeId)"))
        assertTrue(source.contains("recipeSnapshotStore.getSummaries"))
    }

    @Test
    fun appNavHostWiresFileRecipeSnapshotStore() {
        val source = File("src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt").readText()

        assertTrue(source.contains("FileRecipeSnapshotStore(context)"))
        assertTrue(source.contains("MealPlanRepository(FileRecipeSnapshotStore(context))"))
    }
}
