package com.pcosina.app.data.model

data class FeedbackEntry(
    val id: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "Queued" // Queued, Sent, Failed
)
