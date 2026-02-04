package com.pcosina.app.data.model

data class DailyLog(
    val date: String, // YYYY-MM-DD
    val completedMealIds: List<String> = emptyList(),
    val weightKg: Float? = null,
    val journalText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
