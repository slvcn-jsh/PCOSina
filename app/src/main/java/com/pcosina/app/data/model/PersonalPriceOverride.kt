package com.pcosina.app.data.model

data class PersonalPriceOverride(
    val canonicalKey: String,
    val ingredientId: String? = null,
    val ingredientName: String,
    val pricePhp: Double,
    val unit: String,
    val marketType: String = "user_observed",
    val location: String = "NCR",
    val observedOn: String,
    val validUntil: String? = null,
    val warning: String? = null,
    val syncStatus: String = "Pending",
)
