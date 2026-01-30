package com.pcosina.app.data.repository

import com.pcosina.app.data.api.GeneratePlanRequest
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.PcosinaApiService
import com.pcosina.app.data.model.UserProfile
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MealPlanRepository {

    private val apiService: PcosinaApiService

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // CHANGE THIS IP to your COMPUTER'S IPv4 Address from Step 1
        // Do not use 10.0.2.2 for physical devices.
        val computerIp = "192.168.1.21" // <--- REPLACE THIS VALUE
        
        val retrofit = Retrofit.Builder()
            .baseUrl("http://$computerIp:8000/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(PcosinaApiService::class.java)
    }

    suspend fun generatePlan(profile: UserProfile): Result<GeneratePlanResponse> {
        return try {
            val response = apiService.generatePlan(GeneratePlanRequest(profile))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
