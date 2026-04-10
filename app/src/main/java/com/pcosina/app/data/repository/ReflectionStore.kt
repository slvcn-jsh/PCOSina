package com.pcosina.app.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Local-only encrypted storage for sensitive reflections and spending logs.
 * Data stays on-device and is not synced to any backend.
 */
class ReflectionStore(private val context: Context) {
    private val gson = Gson()
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "pcosina_reflections",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun dailyLogsKey(userId: String) = "daily_logs_$userId"
    private fun weeklyKey(userId: String, weekStart: String) = "weekly_journal_${userId}_$weekStart"
    private fun weeklySpendKey(userId: String, weekStart: String) = "weekly_spend_${userId}_$weekStart"
    private fun artifactKey(userId: String, name: String) = "artifact_${name}_$userId"

    fun getDailyLogsJson(userId: String): String? = prefs.getString(dailyLogsKey(userId), null)

    fun saveDailyLogsJson(userId: String, json: String) {
        prefs.edit().putString(dailyLogsKey(userId), json).apply()
    }

    fun getWeeklyJournal(userId: String, weekStart: String): String? =
        prefs.getString(weeklyKey(userId, weekStart), null)

    fun saveWeeklyJournal(userId: String, weekStart: String, text: String) {
        prefs.edit().putString(weeklyKey(userId, weekStart), text).apply()
    }

    fun getWeeklySpend(userId: String, weekStart: String): Int? {
        return prefs.getString(weeklySpendKey(userId, weekStart), null)?.toIntOrNull()
    }

    fun saveWeeklySpend(userId: String, weekStart: String, value: Int?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(weeklySpendKey(userId, weekStart))
        else editor.putString(weeklySpendKey(userId, weekStart), value.toString())
        editor.apply()
    }

    fun getArtifactJson(userId: String, name: String): String? =
        prefs.getString(artifactKey(userId, name), null)

    fun saveArtifactJson(userId: String, name: String, json: String?) {
        val editor = prefs.edit()
        if (json.isNullOrEmpty()) editor.remove(artifactKey(userId, name))
        else editor.putString(artifactKey(userId, name), json)
        editor.apply()
    }

    fun getAllWeeklyJournals(userId: String): Map<String, String> {
        return prefs.all
            .filterKeys { it.startsWith("weekly_journal_${userId}_") }
            .mapValues { it.value?.toString().orEmpty() }
    }

    fun replaceWeeklyJournals(userId: String, journals: Map<String, String>) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("weekly_journal_${userId}_") }
            .forEach { editor.remove(it) }
        journals.forEach { (weekStart, text) ->
            if (weekStart.isNotBlank()) {
                editor.putString(weeklyKey(userId, weekStart), text)
            }
        }
        editor.apply()
    }

    fun clearForUser(userId: String) {
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (
                key == dailyLogsKey(userId) ||
                key.startsWith("weekly_journal_${userId}_") ||
                key.startsWith("weekly_spend_${userId}_") ||
                key.startsWith("artifact_") && key.endsWith("_$userId")
            ) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun exportReflections(userId: String): File {
        val dailyLogsJson = getDailyLogsJson(userId)
        val dailyLogs = if (!dailyLogsJson.isNullOrBlank()) {
            try { gson.fromJson(dailyLogsJson, Array<Any>::class.java).toList() } catch (_: Exception) { emptyList() }
        } else emptyList()
        val export = mapOf(
            "exportedAt" to LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            "dailyLogs" to dailyLogs,
            "weeklyJournals" to getAllWeeklyJournals(userId),
            "weeklySpending" to prefs.all
                .filterKeys { it.startsWith("weekly_spend_${userId}_") }
                .mapValues { it.value?.toString().orEmpty() }
        )
        val json = gson.toJson(export)
        val fileName = "pcosina_reflections_${userId}_${System.currentTimeMillis()}.json"
        val outFile = File(context.cacheDir, fileName)
        outFile.writeText(json)
        return outFile
    }
}
