package com.pcosina.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
    companion object {
        private const val TAG = "ReflectionStore"
        private const val PREFS_NAME = "pcosina_reflections"
    }

    private val gson = Gson()
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun buildPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun resetCorruptedStore() {
        cachedPrefs = null
        Log.w(TAG, "Resetting unreadable local reflection storage.")
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        runCatching { File(sharedPrefsDir, "$PREFS_NAME.xml").delete() }
        runCatching { File(sharedPrefsDir, "$PREFS_NAME.xml.bak").delete() }
    }

    private fun prefsOrNull(): SharedPreferences? {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val resolved = runCatching { buildPrefs() }
                .recoverCatching { firstFailure ->
                    Log.e(TAG, "Reflection store unreadable on first access. Retrying after reset.", firstFailure)
                    resetCorruptedStore()
                    buildPrefs()
                }
                .getOrElse { secondFailure ->
                    Log.e(TAG, "Reflection store remains unavailable after reset.", secondFailure)
                    null
                }
            cachedPrefs = resolved
            return resolved
        }
    }

    private inline fun <T> readOrDefault(default: T, block: (SharedPreferences) -> T): T {
        return runCatching {
            val prefs = prefsOrNull() ?: return default
            block(prefs)
        }.getOrElse { error ->
            Log.e(TAG, "Reflection store read failed. Returning safe default.", error)
            resetCorruptedStore()
            default
        }
    }

    private inline fun writeSafely(block: (SharedPreferences.Editor) -> Unit) {
        val prefs = prefsOrNull() ?: return
        runCatching {
            prefs.edit().also(block).apply()
        }.onFailure { error ->
            Log.e(TAG, "Reflection store write failed. Resetting local encrypted store.", error)
            resetCorruptedStore()
        }
    }

    private fun dailyLogsKey(userId: String) = "daily_logs_$userId"
    private fun weeklyKey(userId: String, weekStart: String) = "weekly_journal_${userId}_$weekStart"
    private fun weeklySpendKey(userId: String, weekStart: String) = "weekly_spend_${userId}_$weekStart"
    private fun artifactKey(userId: String, name: String) = "artifact_${name}_$userId"

    fun getDailyLogsJson(userId: String): String? =
        readOrDefault<String?>(null) { prefs -> prefs.getString(dailyLogsKey(userId), null) }

    fun saveDailyLogsJson(userId: String, json: String) {
        writeSafely { it.putString(dailyLogsKey(userId), json) }
    }

    fun getWeeklyJournal(userId: String, weekStart: String): String? =
        readOrDefault<String?>(null) { prefs -> prefs.getString(weeklyKey(userId, weekStart), null) }

    fun saveWeeklyJournal(userId: String, weekStart: String, text: String) {
        writeSafely { it.putString(weeklyKey(userId, weekStart), text) }
    }

    fun getWeeklySpend(userId: String, weekStart: String): Int? {
        return readOrDefault<String?>(null) { prefs ->
            prefs.getString(weeklySpendKey(userId, weekStart), null)
        }?.toIntOrNull()
    }

    fun saveWeeklySpend(userId: String, weekStart: String, value: Int?) {
        writeSafely { editor ->
            if (value == null) editor.remove(weeklySpendKey(userId, weekStart))
            else editor.putString(weeklySpendKey(userId, weekStart), value.toString())
        }
    }

    fun getArtifactJson(userId: String, name: String): String? =
        readOrDefault<String?>(null) { prefs -> prefs.getString(artifactKey(userId, name), null) }

    fun saveArtifactJson(userId: String, name: String, json: String?) {
        writeSafely { editor ->
            if (json.isNullOrEmpty()) editor.remove(artifactKey(userId, name))
            else editor.putString(artifactKey(userId, name), json)
        }
    }

    fun getAllWeeklyJournals(userId: String): Map<String, String> {
        return readOrDefault(emptyMap()) { prefs ->
            prefs.all
            .filterKeys { it.startsWith("weekly_journal_${userId}_") }
            .mapValues { it.value?.toString().orEmpty() }
        }
    }

    fun replaceWeeklyJournals(userId: String, journals: Map<String, String>) {
        writeSafely { editor ->
            readOrDefault(emptySet<String>()) { prefs ->
                prefs.all.keys.filter { it.startsWith("weekly_journal_${userId}_") }.toSet()
            }.forEach { editor.remove(it) }
            journals.forEach { (weekStart, text) ->
                if (weekStart.isNotBlank()) {
                    editor.putString(weeklyKey(userId, weekStart), text)
                }
            }
        }
    }

    fun clearForUser(userId: String) {
        writeSafely { editor ->
            readOrDefault(emptySet<String>()) { prefs -> prefs.all.keys.toSet() }.forEach { key ->
                if (
                    key == dailyLogsKey(userId) ||
                    key.startsWith("weekly_journal_${userId}_") ||
                    key.startsWith("weekly_spend_${userId}_")
                ) {
                    editor.remove(key)
                }
            }
        }
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
            "weeklySpending" to readOrDefault(emptyMap()) { prefs ->
                prefs.all
                    .filterKeys { it.startsWith("weekly_spend_${userId}_") }
                    .mapValues { it.value?.toString().orEmpty() }
            }
        )
        val json = gson.toJson(export)
        val fileName = "pcosina_reflections_${userId}_${System.currentTimeMillis()}.json"
        val outFile = File(context.cacheDir, fileName)
        outFile.writeText(json)
        return outFile
    }
}
