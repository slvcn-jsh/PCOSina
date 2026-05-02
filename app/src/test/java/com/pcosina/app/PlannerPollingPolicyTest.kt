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
                "ui", "screens", "MealPlanRefinedScreen.kt"
            )
        )

        assertTrue(
            "Async planner endpoint should expose Idempotency-Key support.",
            api.contains("@Header(\"Idempotency-Key\") idempotencyKey: String? = null")
        )
        assertTrue(
            "MealPlanRepository should create a per-attempt token for fresh generations.",
            repo.contains("data class GeneratePlanAttempt(") &&
                repo.contains("token = UUID.randomUUID().toString()") &&
                repo.contains("buildGeneratePlanIdempotencyKey(request, attempt.token)") &&
                repo.contains("apiService.generatePlanAsync(request, idempotencyKey = idempotencyKey)")
        )
        assertTrue(
            "MealPlanViewModel should preserve the same attempt only while a request is still pending.",
            viewModelContainsPendingRequestState()
        )
        assertTrue(
            "MealPlanViewModel should leave authoritative plan_generated telemetry to the backend and only emit plan_viewed on success.",
            viewModelDoesNotEmitDuplicatePlanGenerated()
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
            "Loading copy should acknowledge backend queue delay in the refined shell.",
            screen.contains("This can take a while if the backend queue is busy.")
        )
    }

    @Test
    fun repository_transportPath_avoidsBlockingWaits() {
        val repo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "MealPlanRepository.kt"
            )
        )

        assertTrue(
            "Planner transport should avoid blocking Task.await calls in the OkHttp path.",
            !repo.contains("Tasks.await(")
        )
        assertTrue(
            "Planner transport should avoid sleep-based HTTP retries in the OkHttp path.",
            !repo.contains("Thread.sleep(")
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

    private fun viewModelDoesNotEmitDuplicatePlanGenerated(): Boolean {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "MealPlanViewModel.kt"
            )
        )
        return !viewModel.contains("eventName = \"plan_generated\"") &&
            viewModel.contains("eventName = \"plan_viewed\"")
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
