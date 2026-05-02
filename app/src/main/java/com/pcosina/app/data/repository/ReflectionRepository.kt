package com.pcosina.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DailyLog

class ReflectionRepository(private val reflectionStore: ReflectionStore) {
    private val gson = Gson()

    data class DailyLog(
        val mood: String,
        val energy: Int,
        val fullness: Int,
        val date: String
    )

    fun getReflectionHistory(userId: String): List<DailyLog> {
        val json = reflectionStore.getDailyLogsJson(userId) ?: return emptyList()
        val type = object : TypeToken<List<DailyLog>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
