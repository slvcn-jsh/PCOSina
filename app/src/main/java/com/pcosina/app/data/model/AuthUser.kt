package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class AuthUser(
    val email: String,
    val passwordHash: String,
    val createdAt: Long = System.currentTimeMillis()
)
