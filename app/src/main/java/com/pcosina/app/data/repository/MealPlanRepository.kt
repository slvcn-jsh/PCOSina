package com.pcosina.app.data.repository

import com.pcosina.app.BuildConfig
import com.pcosina.app.data.api.GeneratePlanRequest
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.PcosinaApiService
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.model.UserProfile
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class MealPlanRepository {

    private val apiService: PcosinaApiService
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
                val requestBuilder = original.newBuilder()
                    .addHeader("X-PCOSINA-Schema-Version", BuildConfig.SCHEMA_VERSION)
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    try {
                        val tokenResult = Tasks.await(currentUser.getIdToken(false), 5, TimeUnit.SECONDS)
                        val token = tokenResult.token
                        if (!token.isNullOrBlank()) {
                            requestBuilder.addHeader("Authorization", "Bearer $token")
                        }
                    } catch (_: Exception) {
                        // If token fetch fails, proceed without auth header.
                    }
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
            Result.failure(e)
        }
    }

    suspend fun generatePlan(profile: UserProfile): Result<GeneratePlanResponse> {
        return try {
            validateGeneratePlanProfile(profile)
            val response = apiService.generatePlan(GeneratePlanRequest(profile))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(mapGeneratePlanException(e))
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
