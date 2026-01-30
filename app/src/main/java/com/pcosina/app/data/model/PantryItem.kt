package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PantryItem(
    val ingredientName: String,
    val quantity: String,
    val expiryDate: Long? = null
)
