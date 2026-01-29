package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Ingredient(
    val name: String,
    val quantity: String,
)

