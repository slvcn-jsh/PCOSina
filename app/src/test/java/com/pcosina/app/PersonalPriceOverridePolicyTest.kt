package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalPriceOverridePolicyTest {

    @Test
    fun personalPrices_areLocalFirstUserScopedAndAppliedOnlyToNewPlans() {
        val api = readMain("data", "api", "PcosinaApiService.kt")
        val repository = readMain("data", "repository", "MealPlanRepository.kt")
        val localRepository = readMain("data", "repository", "GroceryLocalRepository.kt")
        val secureStore = readMain("data", "repository", "UserPreferencesRepository.kt")
        val viewModel = readMain("ui", "GroceryViewModel.kt")
        val navigation = readMain("ui", "navigation", "AppNavHost.kt")
        val screen = readMain("ui", "screens", "GroceryRefinedScreen.kt")

        assertTrue(api.contains("@GET(\"prices/personal\")"))
        assertTrue(api.contains("@POST(\"prices/personal\")"))
        assertTrue(api.contains("prices/personal/{ingredientId}"))
        assertTrue(repository.contains("executeWithBackendFallback(\"personal price save\")"))
        assertTrue(localRepository.contains("getPersonalPriceOverridesJson(userId: String)"))
        assertTrue(secureStore.contains("SecureArtifacts.personalPriceOverridesJson"))
        assertTrue(viewModel.contains("syncStatus == \"Pending\""))
        assertTrue(viewModel.contains("savePersonalPriceOverridesJson(userId"))
        assertTrue(viewModel.contains("val loadedUserId: StateFlow<String?>"))
        assertTrue(navigation.contains("pendingPersonalPriceSignature"))
        assertTrue(navigation.contains("groceryLoadedUserId != uid"))
        assertTrue(navigation.contains("failed.copy(syncStatus = \"Pending\")"))
        assertTrue(screen.contains("It will sync when you reconnect."))
        assertTrue(screen.contains("applies to newly generated plans, not this plan's fixed estimate"))
        assertTrue(screen.contains("item.key != \"water\""))
        assertTrue(screen.contains("Unusual values are accepted with a warning"))
    }

    private fun readMain(vararg parts: String): String =
        read(resolve("app", "src", "main", "java", "com", "pcosina", "app", *parts))

    private fun read(path: Path): String = String(Files.readAllBytes(path))

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
