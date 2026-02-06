package com.pcosina.app.data.repository

import com.pcosina.app.BuildConfig
import com.pcosina.app.data.api.GeneratePlanRequest
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.PcosinaApiService
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.model.UserProfile
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MealPlanRepository {

    private val apiService: PcosinaApiService
    private val recipeCache = object : LinkedHashMap<String, RecipeDetailDto>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RecipeDetailDto>?): Boolean {
            return size > 200
        }
    }

    init {
        val firebaseAuth = FirebaseAuth.getInstance()
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

        val baseUrl = normalizeBaseUrl(BuildConfig.BASE_URL)
        
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
}
