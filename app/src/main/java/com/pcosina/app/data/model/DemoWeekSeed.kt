package com.pcosina.app.data.model

data class DemoWeekSeed(
    val planInstance: PlanInstance,
    val dailyLogs: List<DailyLog>,
    val weeklyJournal: String,
    val weeklySpend: Int?
)
