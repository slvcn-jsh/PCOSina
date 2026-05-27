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
            "Queue polling should stay pinned to the backend that accepted the async job.",
            repo.contains("private data class QueuedPlanStart(") &&
                repo.contains("QueuedPlanStart(service = apiService, jobId = queued.jobId)") &&
                repo.contains("awaitQueuedPlan(") &&
                repo.contains("service = queuedPlanStart.service")
        )
        assertTrue(
            "MealPlanViewModel should preserve the same attempt only while a request is still pending.",
            viewModelContainsPendingRequestState()
        )
        assertTrue(
            "MealPlanViewModel should emit mobile-side plan_generated and plan_viewed telemetry after successful generation.",
            viewModelEmitsMobileGenerationTelemetry()
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
            "Loading copy should acknowledge queue delay without backend-facing wording.",
            screen.contains("This can take a while when many plan requests are running.")
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
        assertTrue(
            "Authenticated planner requests should wait for a usable Firebase token before failing.",
            repo.contains("awaitAuthToken(")
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

    private fun viewModelEmitsMobileGenerationTelemetry(): Boolean {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "MealPlanViewModel.kt"
            )
        )
        return viewModel.contains("eventName = \"plan_generated\"") &&
            viewModel.contains("\"slot_count\"") &&
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
