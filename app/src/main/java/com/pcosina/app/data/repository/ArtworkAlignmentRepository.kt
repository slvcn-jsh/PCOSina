package com.pcosina.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pcosina.app.data.model.ArtworkAlignmentConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.artworkAlignmentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "artwork_alignment_prefs"
)

class ArtworkAlignmentRepository(context: Context) {
    private val appContext = context.applicationContext

    fun alignmentFlow(
        key: String,
        defaultConfig: ArtworkAlignmentConfig = ArtworkAlignmentConfig.Default,
    ): Flow<ArtworkAlignmentConfig> {
        val safeKey = key.safePreferenceKey()
        val fallback = defaultConfig.clamped()
        return appContext.artworkAlignmentDataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { prefs ->
                ArtworkAlignmentConfig(
                    offsetXDp = prefs[Keys.offsetX(safeKey)] ?: fallback.offsetXDp,
                    offsetYDp = prefs[Keys.offsetY(safeKey)] ?: fallback.offsetYDp,
                    scale = prefs[Keys.scale(safeKey)] ?: fallback.scale,
                ).clamped()
            }
    }

    suspend fun saveAlignment(key: String, config: ArtworkAlignmentConfig) {
        val safeKey = key.safePreferenceKey()
        val clamped = config.clamped()
        appContext.artworkAlignmentDataStore.edit { prefs ->
            prefs[Keys.offsetX(safeKey)] = clamped.offsetXDp
            prefs[Keys.offsetY(safeKey)] = clamped.offsetYDp
            prefs[Keys.scale(safeKey)] = clamped.scale
        }
    }

    suspend fun resetAlignment(key: String) {
        val safeKey = key.safePreferenceKey()
        appContext.artworkAlignmentDataStore.edit { prefs ->
            prefs.remove(Keys.offsetX(safeKey))
            prefs.remove(Keys.offsetY(safeKey))
            prefs.remove(Keys.scale(safeKey))
        }
    }

    private object Keys {
        fun offsetX(key: String) = floatPreferencesKey("artwork_${key}_offset_x_dp")
        fun offsetY(key: String) = floatPreferencesKey("artwork_${key}_offset_y_dp")
        fun scale(key: String) = floatPreferencesKey("artwork_${key}_scale")
    }
}

private fun String.safePreferenceKey(): String =
    replace(Regex("[^A-Za-z0-9_]"), "_").ifBlank { "unknown" }
