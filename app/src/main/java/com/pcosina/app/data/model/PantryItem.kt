package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PantryItem(
    val name: String,
    val quantity: String,
    val category: String,
)

