package com.pcosina.app.ui.util

import java.util.Locale

fun normalizeMealLabel(label: String?): String {
    val normalized = label
        ?.trim()
        ?.lowercase(Locale.ENGLISH)
        ?.replace("_", " ")
        ?.replace("-", " ")
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()

    return when {
        normalized.isBlank() -> ""
        normalized.contains("breakfast") || normalized.contains("morning") -> "breakfast"
        normalized.contains("lunch") || normalized.contains("noon") -> "lunch"
        normalized.contains("dinner") || normalized.contains("supper") || normalized.contains("evening") -> "dinner"
        normalized.contains("snack") -> "snack"
        else -> normalized
    }
}
