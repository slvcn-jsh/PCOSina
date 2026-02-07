package com.pcosina.app.data.model

import com.pcosina.app.data.api.GeneratePlanResponse

data class PlanInstance(
    val id: String,
    val weekStart: String,
    val weekEnd: String,
    val generatedAt: Long,
    val response: GeneratePlanResponse
)
