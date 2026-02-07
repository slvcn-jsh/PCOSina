package com.pcosina.app.domain

import kotlin.math.roundToInt

object UnitConverter {
    const val HEIGHT_CM = "cm"
    const val HEIGHT_FT_IN = "ft_in"
    const val WEIGHT_KG = "kg"
    const val WEIGHT_LB = "lb"

    fun kgToLb(kg: Int): Int = (kg * 2.20462).roundToInt()
    fun lbToKg(lb: Int): Int = (lb / 2.20462).roundToInt()
    fun kgToLb(kg: Float): Float = (kg * 2.20462f)
    fun lbToKg(lb: Float): Float = (lb / 2.20462f)

    fun cmToFeetInches(cm: Int): Pair<Int, Int> {
        if (cm <= 0) return 0 to 0
        val totalInches = cm / 2.54
        val feet = (totalInches / 12).toInt()
        val inches = (totalInches - (feet * 12)).roundToInt()
        return feet to inches.coerceIn(0, 11)
    }

    fun feetInchesToCm(feet: Int, inches: Int): Int {
        val safeFeet = feet.coerceAtLeast(0)
        val safeInches = inches.coerceIn(0, 11)
        val totalInches = (safeFeet * 12) + safeInches
        return (totalInches * 2.54).roundToInt()
    }
}
