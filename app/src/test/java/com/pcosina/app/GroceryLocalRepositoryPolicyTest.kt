package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryLocalRepositoryPolicyTest {

    @Test
    fun groceryViewModel_usesGroceryLocalRepositoryBoundary() {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "GroceryViewModel.kt"
            )
        )

        assertTrue(viewModel.contains("private val groceryLocalRepository: GroceryLocalRepository"))
        assertTrue(viewModel.contains("groceryLocalRepository.getGrocerySnapshotsJson(userId).first()"))
        assertTrue(viewModel.contains("groceryLocalRepository.clearGrocerySnapshots(currentUserId)"))
        assertTrue(!viewModel.contains("class GroceryViewModel(private val repository: UserPreferencesRepository)"))
    }

    @Test
    fun appNavHost_wiresUserPreferencesGroceryLocalRepositoryIntoGroceryViewModel() {
        val navHost = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "navigation", "AppNavHost.kt"
            )
        )
        val groceryRepo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "GroceryLocalRepository.kt"
            )
        )

        assertTrue(navHost.contains("val groceryLocalRepository = remember { UserPreferencesGroceryLocalRepository(userPrefsRepository) }"))
        assertTrue(navHost.contains("GroceryViewModel.Factory(groceryLocalRepository)"))
        assertTrue(groceryRepo.contains("interface GroceryLocalRepository"))
        assertTrue(groceryRepo.contains("class UserPreferencesGroceryLocalRepository"))
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
