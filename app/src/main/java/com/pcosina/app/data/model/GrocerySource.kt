package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class GroceryItemSource(
    val name: String,
    val quantity: String
)
