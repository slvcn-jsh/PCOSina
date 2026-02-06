package com.pcosina.app.data.repository

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class FeedbackRepository(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class SendResult(val ok: Boolean, val error: String? = null)

    suspend fun sendFeedback(message: String): SendResult = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/feedback"
        val payload = mapOf("message" to message)
        val body = gson.toJson(payload).toRequestBody(jsonType)
        val request = Request.Builder().url(url).post(body).build()
        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    SendResult(ok = true)
                } else {
                    val err = "HTTP ${response.code}"
                    Log.e("FeedbackRepository", "sendFeedback failed: $err")
                    SendResult(ok = false, error = err)
                }
            }
        } catch (e: Exception) {
            Log.e("FeedbackRepository", "sendFeedback exception: ${e.message}", e)
            SendResult(ok = false, error = e.message ?: "Network error")
        }
    }
}
