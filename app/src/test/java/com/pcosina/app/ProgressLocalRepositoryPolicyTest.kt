package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressLocalRepositoryPolicyTest {

    @Test
    fun progressViewModel_usesProgressLocalRepositoryBoundary() {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "ProgressViewModel.kt"
            )
        )

        assertTrue(viewModel.contains("private val progressLocalRepository: ProgressLocalRepository"))
        assertTrue(viewModel.contains("progressLocalRepository.getFeedbackQueueJson(userId).first()"))
        assertTrue(!viewModel.contains("private val userPrefsRepository: UserPreferencesRepository"))
    }

    @Test
    fun appNavHost_wiresUserPreferencesProgressLocalRepositoryIntoProgressViewModel() {
        val navHost = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "navigation", "AppNavHost.kt"
            )
        )
        val progressRepo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "ProgressLocalRepository.kt"
            )
        )

        assertTrue(navHost.contains("val progressLocalRepository = remember { UserPreferencesProgressLocalRepository(userPrefsRepository) }"))
        assertTrue(navHost.contains("ProgressViewModel.Factory(progressLocalRepository, reflectionStore, feedbackRepository)"))
        assertTrue(progressRepo.contains("interface ProgressLocalRepository"))
        assertTrue(progressRepo.contains("class UserPreferencesProgressLocalRepository"))
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
