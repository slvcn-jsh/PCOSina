package com.pcosina.app.data.repository

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.gson.Gson
import com.pcosina.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class FeedbackRepository(
    baseUrl: String,
    private val firebaseAppCheck: FirebaseAppCheck = FirebaseAppCheck.getInstance(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = normalizeBaseUrl(baseUrl)
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class SendResult(val ok: Boolean, val error: String? = null)

    suspend fun sendFeedback(message: String): SendResult = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/feedback"
        val payload = mapOf("message" to message)
        val body = gson.toJson(payload).toRequestBody(jsonType)
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("X-PCOSINA-Schema-Version", BuildConfig.SCHEMA_VERSION)
            .post(body)
        if (BuildConfig.PCOSINA_SEND_APP_CHECK) {
            val appCheckToken = getOptionalAppCheckToken()
            if (!appCheckToken.isNullOrBlank()) {
                requestBuilder.addHeader("X-Firebase-AppCheck", appCheckToken)
            } else if (!BuildConfig.DEBUG) {
                return@withContext SendResult(ok = false, error = "Firebase App Check token unavailable")
            }
        }
        val request = requestBuilder.build()
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

    private suspend fun getOptionalAppCheckToken(): String? {
        return try {
            firebaseAppCheck.getAppCheckToken(false).await().token?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w("FeedbackRepository", "App Check token unavailable: ${e.message}")
            null
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
}
