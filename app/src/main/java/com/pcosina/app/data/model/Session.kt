package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Session(
    val isLoggedIn: Boolean = false,
    val currentUserEmail: String? = null,
    val currentUserUid: String? = null
)
