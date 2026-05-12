package com.pcosina.app.data.repository

import com.pcosina.app.BuildConfig
import com.pcosina.app.data.api.AdminPriceRuleDto
import com.pcosina.app.data.api.AdminPriceRuleUpsertDto
import com.pcosina.app.data.api.AdminRecipeUpsertDto
import com.pcosina.app.data.api.GeneratePlanRequest
import com.pcosina.app.data.api.MlClientEventRequestDto
import com.pcosina.app.data.api.PcosinaApiService
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.SwapOptionsRequestDto
import com.pcosina.app.data.api.toPlannerPlanResponse
import com.pcosina.app.data.api.toPlannerRecipeDetail
import com.pcosina.app.data.api.toPlannerRecipeSummary
import com.pcosina.app.data.model.PlannerPlanResponse
import com.pcosina.app.data.model.PlannerRecipeDetail
import com.pcosina.app.data.model.PlannerRecipeSummary
import com.pcosina.app.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MealPlanRepository {

    data class GeneratePlanAttempt(
        val startDate: String,
        val token: String
    )

    private data class QueuedPlanStart(
        val service: PcosinaApiService,
        val jobId: String? = null,
        val immediateResponse: PlannerPlanResponse? = null
    )

    private val apiService: PcosinaApiService
    private val fallbackApiService: PcosinaApiService?
    private val primaryBaseUrl: String
    private val fallbackBaseUrl: String?
    private val gson = Gson()
    @Volatile private var cachedAuthToken: String? = null
    @Volatile private var cachedAppCheckToken: String? = null
    private val authTokenRefreshInFlight = AtomicBoolean(false)
    private val appCheckTokenRefreshInFlight = AtomicBoolean(false)
    private val plannerPollTimeoutMs = 600_000L
    private val plannerInitialPollIntervalMs = 1_500L
    private val plannerWarmPollIntervalMs = 3_000L
    private val plannerSlowPollIntervalMs = 5_000L
    private val recipeCache = object : LinkedHashMap<String, PlannerRecipeDetail>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlannerRecipeDetail>?): Boolean {
            return size > 200
        }
    }
    private val summaryCache = object : LinkedHashMap<String, List<PlannerRecipeSummary>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlannerRecipeSummary>>?): Boolean {
            return size > 50
        }
    }

    init {
        val firebaseAuth = FirebaseAuth.getInstance()
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAuth.addIdTokenListener(FirebaseAuth.IdTokenListener { auth ->
            refreshAuthTokenAsync(auth.currentUser, forceRefresh = false)
        })
        refreshAuthTokenAsync(firebaseAuth.currentUser, forceRefresh = false)
        refreshAppCheckTokenAsync(firebaseAppCheck, forceRefresh = false)
        val normalizedBaseUrl = normalizeBaseUrl(BuildConfig.BASE_URL)
        val normalizedReleaseBaseUrl = normalizeBaseUrl(RELEASE_BACKEND_URL)
        primaryBaseUrl = normalizedBaseUrl
        fallbackBaseUrl = if (
            isAutoFallbackCandidate(normalizedBaseUrl) &&
            normalizedBaseUrl != normalizedReleaseBaseUrl
        ) {
            normalizedReleaseBaseUrl
        } else {
            null
        }
        apiService = buildApiService(
            baseUrl = primaryBaseUrl,
            firebaseAuth = firebaseAuth,
            firebaseAppCheck = firebaseAppCheck
        )
        fallbackApiService = fallbackBaseUrl?.let { baseUrl ->
            buildApiService(
                baseUrl = baseUrl,
                firebaseAuth = firebaseAuth,
                firebaseAppCheck = firebaseAppCheck
            )
        }
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
            executeWithBackendFallback("planner warmup") { service ->
                service.health()
            }
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
    ): Result<PlannerPlanResponse> {
        return try {
            validateGeneratePlanProfile(profile)
            val request = GeneratePlanRequest(
                profile = profile,
                startDate = attempt.startDate
            )
            val idempotencyKey = buildGeneratePlanIdempotencyKey(request, attempt.token)
            val queuedPlanStart = executeWithBackendFallback("plan generation request") { apiService ->
                try {
                    val queued = apiService.generatePlanAsync(request, idempotencyKey = idempotencyKey)
                    QueuedPlanStart(service = apiService, jobId = queued.jobId)
                } catch (e: Exception) {
                    if (shouldFallbackToSyncPlanner(e)) {
                        QueuedPlanStart(
                            service = apiService,
                            immediateResponse = apiService.generatePlan(request).toPlannerPlanResponse()
                        )
                    } else {
                        throw e
                    }
                }
            }
            val response = queuedPlanStart.immediateResponse
                ?: awaitQueuedPlan(
                    service = queuedPlanStart.service,
                    jobId = requireNotNull(queuedPlanStart.jobId)
                )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(mapGeneratePlanException(e))
        }
    }

    private suspend fun awaitQueuedPlan(
        service: PcosinaApiService,
        jobId: String
    ): PlannerPlanResponse {
        val normalizedJobId = jobId.trim()
        require(normalizedJobId.isNotBlank()) { "Planner queue did not return a job ID." }

        val startedAt = System.currentTimeMillis()
        var lastStatus = "queued"
        while (System.currentTimeMillis() - startedAt < plannerPollTimeoutMs) {
            val job = service.getPlanJob(normalizedJobId)
            lastStatus = job.status.trim().lowercase()
            when (lastStatus) {
                "done", "success" -> {
                    return job.result
                        ?.toPlannerPlanResponse()
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

        val finalJob = runCatching { service.getPlanJob(normalizedJobId) }.getOrNull()
        if (finalJob != null) {
            lastStatus = finalJob.status.trim().lowercase()
            if (lastStatus == "done" || lastStatus == "success") {
                return finalJob.result
                    ?.toPlannerPlanResponse()
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
            executeWithBackendFallback("ML event emission") { service ->
                service.postMlEvent(
                    MlClientEventRequestDto(
                        eventName = normalizedName,
                        requestId = requestId?.trim()?.takeIf { it.isNotBlank() },
                        payload = payload
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecipeDetails(recipeId: String): Result<PlannerRecipeDetail> {
        return try {
            synchronized(recipeCache) {
                recipeCache[recipeId]?.let { return Result.success(it) }
            }
            val response = executeWithBackendFallback("recipe details") { service ->
                service.getRecipe(recipeId)
            }.toPlannerRecipeDetail()
            synchronized(recipeCache) {
                recipeCache[recipeId] = response
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecipeSummaries(mealType: String, limit: Int = 50): Result<List<PlannerRecipeSummary>> {
        return try {
            val key = "${mealType.lowercase()}_$limit"
            synchronized(summaryCache) {
                summaryCache[key]?.let { return Result.success(it) }
            }
            val response = executeWithBackendFallback("recipe summaries") { service ->
                service.getRecipeSummaries(mealType = mealType, limit = limit)
            }.map { it.toPlannerRecipeSummary() }
            synchronized(summaryCache) {
                summaryCache[key] = response
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAdminRecipes(limit: Int = 250): Result<List<RecipeDetailDto>> {
        return try {
            val response = executeWithBackendFallback("admin recipe review") { service ->
                service.getAdminRecipes(limit = limit)
            }
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(mapAdminException(e, "recipe dataset"))
        }
    }

    suspend fun saveAdminRecipe(request: AdminRecipeUpsertDto): Result<RecipeDetailDto> {
        return try {
            val recipeId = request.id?.trim().orEmpty()
            val saved = executeWithBackendFallback("admin recipe save") { service ->
                if (recipeId.isBlank()) {
                    service.createAdminRecipe(request.copy(id = null))
                } else {
                    service.updateAdminRecipe(recipeId, request.copy(id = recipeId))
                }
            }
            synchronized(recipeCache) {
                recipeCache.remove(saved.id)
            }
            synchronized(summaryCache) {
                summaryCache.clear()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(mapAdminException(e, "recipe dataset"))
        }
    }

    suspend fun deleteAdminRecipe(recipeId: String): Result<Unit> {
        return try {
            val normalized = recipeId.trim()
            require(normalized.isNotBlank()) { "Recipe ID is required." }
            executeWithBackendFallback("admin recipe delete") { service ->
                service.deleteAdminRecipe(normalized)
            }
            synchronized(recipeCache) {
                recipeCache.remove(normalized)
            }
            synchronized(summaryCache) {
                summaryCache.clear()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapAdminException(e, "recipe dataset"))
        }
    }

    suspend fun getAdminPriceRules(limit: Int = 250): Result<List<AdminPriceRuleDto>> {
        return try {
            val response = executeWithBackendFallback("admin price-rule review") { service ->
                service.getAdminPriceRules(limit = limit)
            }
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(mapAdminException(e, "ingredient and price dataset"))
        }
    }

    suspend fun saveAdminPriceRule(request: AdminPriceRuleUpsertDto): Result<AdminPriceRuleDto> {
        return try {
            val ruleId = request.id?.trim().orEmpty()
            val saved = executeWithBackendFallback("admin price-rule save") { service ->
                if (ruleId.isBlank()) {
                    service.createAdminPriceRule(request.copy(id = null))
                } else {
                    service.updateAdminPriceRule(ruleId, request.copy(id = ruleId))
                }
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(mapAdminException(e, "ingredient and price dataset"))
        }
    }

    suspend fun deleteAdminPriceRule(ruleId: String): Result<Unit> {
        return try {
            val normalized = ruleId.trim()
            require(normalized.isNotBlank()) { "Price rule ID is required." }
            executeWithBackendFallback("admin price-rule delete") { service ->
                service.deleteAdminPriceRule(normalized)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapAdminException(e, "ingredient and price dataset"))
        }
    }

    suspend fun getSwapOptions(
        profile: UserProfile,
        mealLabel: String,
        currentRecipeId: String?,
        activeRecipeIds: List<String>,
        limit: Int = 30
    ): Result<List<PlannerRecipeSummary>> {
        return try {
            val normalizedMealLabel = mealLabel.trim()
            val normalizedCurrentRecipeId = currentRecipeId?.trim()?.takeIf { it.isNotBlank() }
            val normalizedActiveRecipeIds = activeRecipeIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            val response = executeWithBackendFallback("recipe swap options") { service ->
                try {
                    service.getSwapOptions(
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
                        service.getRecipeSummaries(mealType = normalizedMealLabel, limit = limit)
                    } else {
                        throw e
                    }
                }
            }.map { it.toPlannerRecipeSummary() }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> executeWithBackendFallback(
        operation: String,
        block: suspend (PcosinaApiService) -> T
    ): T {
        return try {
            block(apiService)
        } catch (primaryError: Exception) {
            val fallbackService = fallbackApiService
            if (fallbackService != null && shouldRetryAgainstFallback(primaryError)) {
                val fallbackHost = backendHost(fallbackBaseUrl)
                android.util.Log.w(
                    "MealPlanRepository",
                    "Primary backend ${backendHost(primaryBaseUrl)} failed during $operation. " +
                        "Retrying against hosted backend $fallbackHost."
                )
                block(fallbackService)
            } else {
                throw primaryError
            }
        }
    }

    private fun validateGeneratePlanProfile(profile: UserProfile) {
        val problems = mutableListOf<String>()
        if (profile.age !in 18..60) problems.add("Age must be between 18 and 60.")
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
            val host = backendHost(primaryBaseUrl)
            return IllegalStateException(
                "Cannot reach backend host ($host). DNS lookup failed. " +
                    "Check Private DNS/VPN/adblock settings, then try again."
            )
        }
        if (isBackendConnectFailure(error)) {
            val host = backendHost(primaryBaseUrl)
            val fallbackHost = fallbackBaseUrl?.let(::backendHost)
            return IllegalStateException(
                if (fallbackHost != null) {
                    "Cannot reach the local debug backend ($host). " +
                        "The app also retried the hosted backend ($fallbackHost), but that connection was unavailable. " +
                        "Start the local backend on your laptop or retry on a network that allows HTTPS access."
                } else {
                    "Cannot reach the PCOSina backend over HTTPS from this network. " +
                        "The app tried $host and its Render fallback edge, but the connection was blocked or unavailable. " +
                        "Check emulator internet access, VPN/Private DNS/adblock settings, or try again later."
                }
            )
        }
        if (error !is HttpException) {
            val message = error.message.orEmpty()
            return when {
                message.contains("app check", ignoreCase = true) ||
                    message.contains("firebase", ignoreCase = true) && message.contains("token", ignoreCase = true) ->
                    IllegalStateException("Secure planner connection is still preparing. Please wait a moment and try again.")
                message.contains("sign-in session", ignoreCase = true) ||
                    message.contains("sign in", ignoreCase = true) ->
                    IllegalStateException("Your sign-in session is still preparing. Please wait a moment and try again.")
                else -> error
            }
        }
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
            403 -> IllegalStateException(
                when {
                    detail.contains("app check", ignoreCase = true) ||
                        detail.contains("firebase", ignoreCase = true) && detail.contains("token", ignoreCase = true) ->
                        "Secure planner connection is still preparing. Please wait a moment and try again."
                    detail.isNotBlank() -> "Planner access could not be verified. Please sign in again, then retry."
                    else -> "Planner access could not be verified. Please sign in again, then retry."
                }
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

    private fun mapAdminException(error: Exception, datasetName: String): Exception {
        if (error is HttpException) {
            return when (error.code()) {
                401, 403 -> IllegalStateException(
                    "Admin $datasetName review was rejected by the backend. " +
                        "Sign out, sign in with an allowlisted admin Google account, and try again."
                )
                404 -> IllegalStateException(
                    "Admin $datasetName endpoint is not available on the configured backend."
                )
                else -> IllegalStateException(
                    "Admin $datasetName review failed with HTTP ${error.code()}."
                )
            }
        }
        return mapGeneratePlanException(error)
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

    private fun backendHost(baseUrl: String? = primaryBaseUrl): String {
        val normalized = normalizeBaseUrl(baseUrl ?: BuildConfig.BASE_URL)
        return runCatching { URI(normalized).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: RELEASE_BACKEND_HOST
    }

    private fun requiresFirebaseAuth(encodedPath: String): Boolean {
        val normalized = encodedPath.trim().trim('/')
        if (normalized.isBlank()) return false
        return normalized != "health" &&
            normalized != "health/ready" &&
            normalized != "feedback"
    }

    private fun refreshAuthTokenAsync(user: FirebaseUser?, forceRefresh: Boolean) {
        if (user == null) {
            cachedAuthToken = null
            authTokenRefreshInFlight.set(false)
            return
        }
        if (!authTokenRefreshInFlight.compareAndSet(false, true)) return
        user.getIdToken(forceRefresh)
            .addOnSuccessListener { result ->
                cachedAuthToken = result.token?.takeIf { it.isNotBlank() }
            }
            .addOnFailureListener { error ->
                android.util.Log.w("MealPlanRepository", "Auth token refresh failed: ${error.message}")
            }
            .addOnCompleteListener {
                authTokenRefreshInFlight.set(false)
            }
    }

    private fun awaitAuthToken(
        user: FirebaseUser,
        forceRefresh: Boolean,
        timeoutMs: Long = 15_000L
    ): String? {
        cachedAuthToken?.takeIf { it.isNotBlank() }?.let { return it }

        val latch = CountDownLatch(1)
        val tokenRef = AtomicReference<String?>(null)
        val errorRef = AtomicReference<Exception?>(null)

        user.getIdToken(forceRefresh)
            .addOnSuccessListener { result ->
                val token = result.token?.takeIf { it.isNotBlank() }
                cachedAuthToken = token
                tokenRef.set(token)
            }
            .addOnFailureListener { error ->
                android.util.Log.w("MealPlanRepository", "Auth token sync wait failed: ${error.message}")
                errorRef.set(error)
            }
            .addOnCompleteListener {
                latch.countDown()
            }

        val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            android.util.Log.w("MealPlanRepository", "Timed out waiting for Firebase auth token.")
            return cachedAuthToken?.takeIf { it.isNotBlank() }
        }

        return tokenRef.get()
            ?: cachedAuthToken?.takeIf { it.isNotBlank() }
            ?: run {
                errorRef.get()?.let { throw IOException("Couldn't verify your sign-in session right now.", it) }
                null
            }
    }

    private fun refreshAppCheckTokenAsync(
        firebaseAppCheck: FirebaseAppCheck,
        forceRefresh: Boolean
    ) {
        if (!appCheckTokenRefreshInFlight.compareAndSet(false, true)) return
        val task = if (forceRefresh) {
            firebaseAppCheck.getAppCheckToken(true)
        } else {
            firebaseAppCheck.getAppCheckToken(false)
        }
        task
            .addOnSuccessListener { result ->
                cachedAppCheckToken = result.token?.takeIf { it.isNotBlank() }
            }
            .addOnFailureListener { error ->
                if (!BuildConfig.DEBUG) {
                    android.util.Log.w("MealPlanRepository", "App Check token refresh failed: ${error.message}")
                }
            }
            .addOnCompleteListener {
                appCheckTokenRefreshInFlight.set(false)
            }
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

    private fun shouldRetryAgainstFallback(error: Exception): Boolean {
        return error is UnknownHostException || isBackendConnectFailure(error)
    }

    private fun isAutoFallbackCandidate(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl).host.orEmpty() }.getOrDefault("")
        if (host.isBlank()) return false
        return host.equals("10.0.2.2", ignoreCase = true) ||
            host.equals("127.0.0.1", ignoreCase = true) ||
            host.equals("localhost", ignoreCase = true)
    }

    private fun buildApiService(
        baseUrl: String,
        firebaseAuth: FirebaseAuth,
        firebaseAppCheck: FirebaseAppCheck
    ): PcosinaApiService {
        val backendHost = runCatching { URI(baseUrl).host.orEmpty() }.getOrDefault("")
        val dns = if (backendHost.equals(RELEASE_BACKEND_HOST, ignoreCase = true)) {
            ResilientBackendDns(
                backendHost = backendHost,
                fallbackIps = RELEASE_BACKEND_FALLBACK_IPS
            )
        } else {
            Dns.SYSTEM
        }
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

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
                    val authToken = cachedAuthToken?.takeIf { it.isNotBlank() }
                        ?: if (requiresFirebaseAuth) {
                            awaitAuthToken(currentUser, forceRefresh = false)
                        } else {
                            refreshAuthTokenAsync(currentUser, forceRefresh = false)
                            null
                        }
                    if (!authToken.isNullOrBlank()) {
                        requestBuilder.addHeader("Authorization", "Bearer $authToken")
                    } else {
                        if (requiresFirebaseAuth) {
                            throw IOException(
                                "Couldn't verify your sign-in session right now. Please retry in a moment."
                            )
                        }
                    }
                } else if (requiresFirebaseAuth) {
                    cachedAuthToken = null
                    throw IOException("You must sign in before using the planner.")
                }
                val appCheckToken = cachedAppCheckToken
                if (!appCheckToken.isNullOrBlank()) {
                    requestBuilder.addHeader("X-Firebase-AppCheck", appCheckToken)
                } else {
                    refreshAppCheckTokenAsync(firebaseAppCheck, forceRefresh = false)
                    if (!BuildConfig.DEBUG) {
                        throw IOException("Firebase App Check token is still preparing. Please retry in a moment.")
                    }
                }

                val request = requestBuilder.build()
                val response = chain.proceed(request)
                val responseSchema = response.header("X-PCOSINA-Schema-Version")
                if (!responseSchema.isNullOrBlank() && responseSchema != BuildConfig.SCHEMA_VERSION) {
                    android.util.Log.w(
                        "MealPlanRepository",
                        "Schema version mismatch. Client=${BuildConfig.SCHEMA_VERSION}, Server=$responseSchema"
                    )
                }
                if (response.code == 401) {
                    refreshAuthTokenAsync(currentUser, forceRefresh = true)
                }
                if (response.code == 401 || response.code == 403) {
                    refreshAppCheckTokenAsync(firebaseAppCheck, forceRefresh = true)
                }
                response
            }
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(PcosinaApiService::class.java)
    }

    companion object {
        private const val RELEASE_BACKEND_URL = "https://pcosina-backend.onrender.com/"
        private const val RELEASE_BACKEND_HOST = "pcosina-backend.onrender.com"
        private val RELEASE_BACKEND_FALLBACK_IPS = listOf("216.24.57.7", "216.24.57.251")
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
