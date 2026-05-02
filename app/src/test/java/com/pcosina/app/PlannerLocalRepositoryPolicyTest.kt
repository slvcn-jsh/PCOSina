package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerLocalRepositoryPolicyTest {

    @Test
    fun mealPlanViewModel_usesPlannerLocalRepositoryBoundary() {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "MealPlanViewModel.kt"
            )
        )

        assertTrue(viewModel.contains("private val plannerLocalRepository: PlannerLocalRepository"))
        assertTrue(viewModel.contains("plannerLocalRepository.getSavedPlanJson(userId).first()"))
        assertTrue(!viewModel.contains("private val userPrefsRepository: UserPreferencesRepository"))
    }

    @Test
    fun appNavHost_wiresUserPreferencesPlannerLocalRepositoryIntoMealPlanViewModel() {
        val navHost = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "navigation", "AppNavHost.kt"
            )
        )
        val plannerRepo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "PlannerLocalRepository.kt"
            )
        )

        assertTrue(navHost.contains("val plannerLocalRepository = remember { UserPreferencesPlannerLocalRepository(userPrefsRepository) }"))
        assertTrue(navHost.contains("plannerLocalRepository = plannerLocalRepository"))
        assertTrue(plannerRepo.contains("interface PlannerLocalRepository"))
        assertTrue(plannerRepo.contains("class UserPreferencesPlannerLocalRepository"))
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
