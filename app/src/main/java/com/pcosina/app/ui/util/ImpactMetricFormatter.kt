package com.pcosina.app.ui.util

fun formatKcalProgressShort(estimated: Int, target: Int): String {
    return "≈$estimated/$target kcal"
}

fun formatProteinProgressShort(estimated: Int, target: Int): String {
    return "Protein ≈$estimated/${target}g"
}

fun formatFiberProgressShort(estimated: Int, target: Int): String {
    return "Fiber ≈$estimated/${target}g"
}

fun formatTodayKcalDeltaShort(delta: Int): String {
    return when {
        delta > 0 -> "Today Δ $delta kcal left"
        delta < 0 -> "Today Δ ${kotlin.math.abs(delta)} kcal over"
        else -> "Today Δ on target"
    }
}
