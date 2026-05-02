package com.pcosina.app.data.model

data class PlanInstance(
    val id: String,
    val weekStart: String,
    val weekEnd: String,
    val generatedAt: Long,
    val response: PlannerPlanResponse
)
