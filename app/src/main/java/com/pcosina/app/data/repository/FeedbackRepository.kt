package com.pcosina.app.data.repository

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FeedbackRepository(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun sendFeedback(message: String): Boolean {
        val url = baseUrl.trimEnd('/') + "/feedback"
        val payload = mapOf("message" to message)
        val body = gson.toJson(payload).toRequestBody(jsonType)
        val request = Request.Builder().url(url).post(body).build()
        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
