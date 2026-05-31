package com.pcosina.app.data.model

data class PantryEntry(
    val name: String,
    val quantity: String? = null,
    val expiryDate: String? = null, // YYYY-MM-DD
    val amount: Double? = null,
    val unit: String? = null,
)
