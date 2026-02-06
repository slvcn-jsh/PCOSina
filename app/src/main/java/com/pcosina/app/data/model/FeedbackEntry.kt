package com.pcosina.app.data.model

data class FeedbackEntry(
    val id: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "Queued", // Queued, Sending, Sent, Failed
    val attempts: Int = 0,
    val lastError: String? = null,
    val lastTriedAt: Long? = null
)
