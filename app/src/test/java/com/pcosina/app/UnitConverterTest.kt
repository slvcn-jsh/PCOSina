package com.pcosina.app

import com.pcosina.app.domain.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {
    @Test
    fun cmToFeetInches_rolloverInchesToFeet() {
        val (feet, inches) = UnitConverter.cmToFeetInches(182)
        assertEquals(6, feet)
        assertEquals(0, inches)
    }

    @Test
    fun cmToFeetInches_regularRounding() {
        val (feet, inches) = UnitConverter.cmToFeetInches(180)
        assertEquals(5, feet)
        assertEquals(11, inches)
    }

    @Test
    fun cmToFeetInches_nonPositiveReturnsZero() {
        val (feet, inches) = UnitConverter.cmToFeetInches(0)
        assertEquals(0, feet)
        assertEquals(0, inches)
    }
}
