package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerPollingPolicyTest {

    @Test
    fun asyncPlannerClient_usesIdempotencyAndLongRunningPollingContract() {
        val api = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "api", "PcosinaApiService.kt"
            )
        )
        val repo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "MealPlanRepository.kt"
            )
        )
        val screen = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "MealPlanScreen.kt"
            )
        )

        assertTrue(
            "Async planner endpoint should expose Idempotency-Key support.",
            api.contains("@Header(\"Idempotency-Key\") idempotencyKey: String? = null")
        )
        assertTrue(
            "MealPlanRepository should create a per-attempt token for fresh generations.",
            repo.contains("data class GeneratePlanAttempt(")
                && repo.contains("token = UUID.randomUUID().toString()")
                && repo.contains("buildGeneratePlanIdempotencyKey(request, attempt.token)")
                && repo.contains("apiService.generatePlanAsync(request, idempotencyKey = idempotencyKey)")
        )
        assertTrue(
            "MealPlanViewModel should preserve the same attempt only while a request is still pending.",
            viewModelContainsPendingRequestState()
        )
        assertTrue(
            "Queued-plan polling timeout should allow long-running planner jobs.",
            repo.contains("private val plannerPollTimeoutMs = 600_000L")
        )
        assertTrue(
            "Timeout guidance should tell the user that Retry resumes the same request.",
            repo.contains("Tap Retry to keep waiting for the same request.")
        )
        assertTrue(
            "Loading copy should no longer promise ~30s for long-running planner jobs.",
            screen.contains("This can take a few minutes on the current server setup. Please keep the app open.")
        )
    }

    private fun viewModelContainsPendingRequestState(): Boolean {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "MealPlanViewModel.kt"
            )
        )
        return viewModel.contains("private var pendingGenerateRequest: PendingGenerateRequest? = null") &&
            viewModel.contains("val activeAttempt = pendingGenerateRequest?.attempt ?: repository.createGeneratePlanAttempt().also") &&
            viewModel.contains("shouldKeepPendingGenerateRequest")
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallback = Paths.get(parts.drop(1).first(), *parts.drop(2).toTypedArray())
        if (Files.exists(fallback)) return fallback
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
