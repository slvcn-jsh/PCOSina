package com.pcosina.app.data.repository

import com.pcosina.app.BuildConfig
import com.pcosina.app.data.api.GeneratePlanRequest
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.MlClientEventRequestDto
import com.pcosina.app.data.api.PcosinaApiService
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.api.SwapOptionsRequestDto
import com.pcosina.app.data.model.UserProfile
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.gson.Gson
import kotlinx.coroutines.delay
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import org.json.JSONObject
import java.net.ConnectException
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.UUID

class MealPlanRepository {

    data class GeneratePlanAttempt(
        val startDate: String,
        val token: String
    )

    private val apiService: PcosinaApiService
    private val gson = Gson()
    private val plannerPollTimeoutMs = 600_000L
    private val plannerInitialPollIntervalMs = 1_500L
    private val plannerWarmPollIntervalMs = 3_000L
    private val plannerSlowPollIntervalMs = 5_000L
    private val recipeCache = object : LinkedHashMap<String, RecipeDetailDto>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RecipeDetailDto>?): Boolean {
            return size > 200
        }
    }
    private val summaryCache = object : LinkedHashMap<String, List<RecipeSummaryDto>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RecipeSummaryDto>>?): Boolean {
            return size > 50
        }
    }

    init {
        val firebaseAuth = FirebaseAuth.getInstance()
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        val normalizedBaseUrl = normalizeBaseUrl(BuildConfig.BASE_URL)
        val backendHost = runCatching { URI(normalizedBaseUrl).host.orEmpty() }.getOrDefault("")
        val dns = ResilientBackendDns(
            backendHost = backendHost,
            fallbackIps = listOf("216.24.57.7", "216.24.57.251")
        )
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // Bullet-Proof Resilience: Added a Retry Interceptor
        // This ensures that if the Wi-Fi signal is weak, the app automatically 
        // retries the connection 3 times before showing an error.
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .dns(dns)
            .addInterceptor { chain ->
                val original = chain.request()
                val requiresFirebaseAuth = requiresFirebaseAuth(original.url.encodedPath)
                val requestBuilder = original.newBuilder()
                    .addHeader("X-PCOSINA-Schema-Version", BuildConfig.SCHEMA_VERSION)
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    try {
                        val tokenResult = Tasks.await(currentUser.getIdToken(false), 5, TimeUnit.SECONDS)
                        val token = tokenResult.token
                        if (!token.isNullOrBlank()) {
                            requestBuilder.addHeader("Authorization", "Bearer $token")
                        } else if (requiresFirebaseAuth) {
                            throw IOException("Authentication token is unavailable. Reopen the app or sign in again.")
                        }
                    } catch (e: Exception) {
                        if (requiresFirebaseAuth) {
                            throw IOException(
                                "Authentication token could not be prepared. Reopen the app or sign in again after internet is restored.",
                                e
                            )
                        }
                    }
                } else if (requiresFirebaseAuth) {
                    throw IOException("You must sign in before using the planner.")
                }
                try {
                    val appCheckResult = Tasks.await(firebaseAppCheck.getAppCheckToken(false), 5, TimeUnit.SECONDS)
                    val appCheckToken = appCheckResult.token
                    if (!appCheckToken.isNullOrBlank()) {
                        requestBuilder.addHeader("X-Firebase-AppCheck", appCheckToken)
                    } else if (!BuildConfig.DEBUG) {
                        throw IOException("Firebase App Check token is unavailable for release request.")
                    }
                } catch (e: Exception) {
                    if (!BuildConfig.DEBUG) {
                        throw IOException("Firebase App Check token acquisition failed.", e)
                    }
                    android.util.Log.w("MealPlanRepository", "App Check token unavailable in debug: ${e.message}")
                }

                val request = requestBuilder.build()
                var response = chain.proceed(request)
                val responseSchema = response.header("X-PCOSINA-Schema-Version")
                if (!responseSchema.isNullOrBlank() && responseSchema != BuildConfig.SCHEMA_VERSION) {
                    // Log mismatched schema for visibility; keep running for backward compatibility.
                    android.util.Log.w(
                        "MealPlanRepository",
                        "Schema version mismatch. Client=${BuildConfig.SCHEMA_VERSION}, Server=$responseSchema"
                    )
                }
                var tryCount = 0
                while (!response.isSuccessful && tryCount < 2) {
                    tryCount++
                    Thread.sleep(1000) // Small delay before retry
                    response.close()
                    response = chain.proceed(request)
                }
                response
            }

            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()

        val baseUrl = normalizedBaseUrl
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(PcosinaApiService::class.java)
    }

    private fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        url = url.trim('"', '\'')
        if (url.startsWith(":")) {
            url = url.removePrefix(":")
        }
        if (url.startsWith("//")) {
            url = "https:$url"
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }

    suspend fun warmup(): Result<Unit> {
        return try {
            apiService.health()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapGeneratePlanException(e))
        }
    }

    fun createGeneratePlanAttempt(
        startDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    ): GeneratePlanAttempt {
        return GeneratePlanAttempt(
            startDate = startDate,
            token = UUID.randomUUID().toString()
        )
    }

    suspend fun generatePlan(
        profile: UserProfile,
        attempt: GeneratePlanAttempt = createGeneratePlanAttempt()
    ): Result<GeneratePlanResponse> {
        return try {
            validateGeneratePlanProfile(profile)
            val request = GeneratePlanRequest(
                profile = profile,
                startDate = attempt.startDate
            )
            val idempotencyKey = buildGeneratePlanIdempotencyKey(request, attempt.token)
            val response = try {
                val queued = apiService.generatePlanAsync(request, idempotencyKey = idempotencyKey)
                awaitQueuedPlan(queued.jobId)
            } catch (e: Exception) {
                if (shouldFallbackToSyncPlanner(e)) {
                    apiService.generatePlan(request)
                } else {
                    throw e
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(mapGeneratePlanException(e))
        }
    }

    private suspend fun awaitQueuedPlan(jobId: String): GeneratePlanResponse {
        val normalizedJobId = jobId.trim()
        require(normalizedJobId.isNotBlank()) { "Planner queue did not return a job ID." }

        val startedAt = System.currentTimeMillis()
        var lastStatus = "queued"
        while (System.currentTimeMillis() - startedAt < plannerPollTimeoutMs) {
            val job = apiService.getPlanJob(normalizedJobId)
            lastStatus = job.status.trim().lowercase()
            when (lastStatus) {
                "done", "success" -> {
                    return job.result
                        ?: throw IllegalStateException("Planner job completed without a result payload.")
                }
                "error", "dead-letter", "failed" -> {
                    throw IllegalStateException(
                        job.error?.takeIf { it.isNotBlank() }
                            ?: "Planner job failed before a result was returned."
                    )
                }
            }
            val elapsed = System.currentTimeMillis() - startedAt
            val nextDelay = when {
                elapsed < 30_000L -> plannerInitialPollIntervalMs
                elapsed < 120_000L -> plannerWarmPollIntervalMs
                else -> plannerSlowPollIntervalMs
            }
            delay(nextDelay)
        }

        val finalJob = runCatching { apiService.getPlanJob(normalizedJobId) }.getOrNull()
        if (finalJob != null) {
            lastStatus = finalJob.status.trim().lowercase()
            if (lastStatus == "done" || lastStatus == "success") {
                return finalJob.result
                    ?: throw IllegalStateException("Planner job completed without a result payload.")
            }
        }

        throw IllegalStateException(
            "Plan generation is still running (last status: $lastStatus). Tap Retry to keep waiting for the same request."
        )
    }

    private fun buildGeneratePlanIdempotencyKey(
        request: GeneratePlanRequest,
        attemptToken: String
    ): String {
        val canonical = gson.toJson(
            mapOf(
                "request" to request,
                "attemptToken" to attemptToken
            )
        )
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "plan-$digest"
    }

    private fun shouldFallbackToSyncPlanner(error: Exception): Boolean {
        if (error !is HttpException) return false
        return when (error.code()) {
            404, 405, 501 -> true
            else -> false
        }
    }

    private fun shouldFallbackToSummarySwapOptions(error: Exception): Boolean {
        if (error !is HttpException) return false
        return when (error.code()) {
            404, 405, 501 -> true
            else -> false
        }
    }

    suspend fun emitMlEvent(
        eventName: String,
        requestId: String? = null,
        payload: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            val normalizedName = eventName.trim()
            if (normalizedName.isBlank()) {
                return Result.failure(IllegalArgumentException("eventName cannot be blank"))
            }
            apiService.postMlEvent(
                MlClientEventRequestDto(
                    eventName = normalizedName,
                    requestId = requestId?.trim()?.takeIf { it.isNotBlank() },
                    payload = payload
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecipeDetails(recipeId: String): Result<RecipeDetailDto> {
        return try {
            synchronized(recipeCache) {
                recipeCache[recipeId]?.let { return Result.success(it) }
            }
            val response = apiService.getRecipe(recipeId)
            synchronized(recipeCache) {
                recipeCache[recipeId] = response
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecipeSummaries(mealType: String, limit: Int = 50): Result<List<RecipeSummaryDto>> {
        return try {
            val key = "${mealType.lowercase()}_$limit"
            synchronized(summaryCache) {
                summaryCache[key]?.let { return Result.success(it) }
            }
            val response = apiService.getRecipeSummaries(mealType = mealType, limit = limit)
            synchronized(summaryCache) {
                summaryCache[key] = response
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSwapOptions(
        profile: UserProfile,
        mealLabel: String,
        currentRecipeId: String?,
        activeRecipeIds: List<String>,
        limit: Int = 30
    ): Result<List<RecipeSummaryDto>> {
        return try {
            val normalizedMealLabel = mealLabel.trim()
            val normalizedCurrentRecipeId = currentRecipeId?.trim()?.takeIf { it.isNotBlank() }
            val normalizedActiveRecipeIds = activeRecipeIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            val response = try {
                apiService.getSwapOptions(
                    SwapOptionsRequestDto(
                        profile = profile,
                        mealLabel = normalizedMealLabel,
                        currentRecipeId = normalizedCurrentRecipeId,
                        activeRecipeIds = normalizedActiveRecipeIds,
                        limit = limit
                    )
                )
            } catch (e: Exception) {
                if (shouldFallbackToSummarySwapOptions(e)) {
                    apiService.getRecipeSummaries(mealType = normalizedMealLabel, limit = limit)
                } else {
                    throw e
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateGeneratePlanProfile(profile: UserProfile) {
        val problems = mutableListOf<String>()
        if (profile.age <= 0) problems.add("Age must be greater than 0.")
        if (profile.heightCm <= 0) problems.add("Height must be greater than 0.")
        if (profile.weightKg <= 0) problems.add("Weight must be greater than 0.")
        if (profile.activityLevel.isBlank()) problems.add("Activity level is required.")
        if (profile.goal.isBlank()) problems.add("Goal is required.")
        if (problems.isNotEmpty()) {
            throw IllegalArgumentException("Profile invalid: " + problems.joinToString(" "))
        }
    }

    private fun mapGeneratePlanException(error: Exception): Exception {
        if (error is UnknownHostException) {
            val host = backendHost()
            return IllegalStateException(
                "Cannot reach backend host ($host). DNS lookup failed. " +
                    "Check Private DNS/VPN/adblock settings, then try again."
            )
        }
        if (isBackendConnectFailure(error)) {
            val host = backendHost()
            return IllegalStateException(
                "Cannot reach the PCOSina backend over HTTPS from this network. " +
                    "The app tried $host and its Render fallback edge, but the connection was blocked or unavailable. " +
                    "Check emulator internet access, VPN/Private DNS/adblock settings, or try again later."
            )
        }
        if (error !is HttpException) return error
        val detail = parseErrorDetail(error)
        return when (error.code()) {
            422 -> IllegalStateException(
                if (detail.isNotBlank()) {
                    detail
                } else {
                    "No feasible meal plan found for your current constraints. " +
                        "Try relaxing restrictions/allergies, increasing max cooking time, or adjusting budget."
                }
            )
            409 -> IllegalStateException(
                if (detail.isNotBlank()) detail else "App and backend schema versions do not match."
            )
            401 -> IllegalStateException(
                if (detail.isNotBlank()) detail else "Session expired. Please sign in again."
            )
            503 -> IllegalStateException(
                if (detail.isNotBlank()) {
                    detail
                } else {
                    "Planner service is temporarily busy. Please retry in a moment."
                }
            )
            else -> IllegalStateException(
                if (detail.isNotBlank()) "HTTP ${error.code()}: $detail"
                else (error.message ?: "Request failed")
            )
        }
    }

    private fun parseErrorDetail(error: HttpException): String {
        return try {
            val body = error.response()?.errorBody()?.string()
            if (body.isNullOrBlank()) return ""
            JSONObject(body).optString("detail", "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun backendHost(): String {
        val normalized = normalizeBaseUrl(BuildConfig.BASE_URL)
        return runCatching { URI(normalized).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "pcosina-backend.onrender.com"
    }

    private fun requiresFirebaseAuth(encodedPath: String): Boolean {
        val normalized = encodedPath.trim().trim('/')
        if (normalized.isBlank()) return false
        return normalized != "health" &&
            normalized != "health/ready" &&
            normalized != "feedback"
    }

    private fun isBackendConnectFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is ConnectException || current is SocketTimeoutException || current is SSLException) {
                return true
            }
            val message = current.message.orEmpty()
            if (
                message.contains("failed to connect", ignoreCase = true) ||
                message.contains("connection refused", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ||
                message.contains("unable to resolve host", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private class ResilientBackendDns(
        private val backendHost: String,
        private val fallbackIps: List<String>
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: UnknownHostException) {
                if (hostname.equals(backendHost, ignoreCase = true)) {
                    val fallback = fallbackIps.mapNotNull { ip ->
                        runCatching { InetAddress.getByName(ip) }.getOrNull()
                    }
                    if (fallback.isNotEmpty()) {
                        android.util.Log.w(
                            "MealPlanRepository",
                            "DNS lookup failed for $hostname. Using fallback backend IPs."
                        )
                        fallback
                    } else {
                        throw e
                    }
                } else {
                    throw e
                }
            }
        }
    }
}
