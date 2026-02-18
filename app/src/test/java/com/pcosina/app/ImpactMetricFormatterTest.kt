package com.pcosina.app

import com.pcosina.app.ui.util.formatFiberProgressShort
import com.pcosina.app.ui.util.formatKcalProgressShort
import com.pcosina.app.ui.util.formatProteinProgressShort
import com.pcosina.app.ui.util.formatTodayKcalDeltaShort
import org.junit.Assert.assertEquals
import org.junit.Test

class ImpactMetricFormatterTest {

    @Test
    fun formatters_returnExpectedShortLabels() {
        assertEquals("≈320/1800 kcal", formatKcalProgressShort(320, 1800))
        assertEquals("Protein ≈18/85g", formatProteinProgressShort(18, 85))
        assertEquals("Fiber ≈6/25g", formatFiberProgressShort(6, 25))
        assertEquals("Today Δ 160 kcal left", formatTodayKcalDeltaShort(160))
        assertEquals("Today Δ 40 kcal over", formatTodayKcalDeltaShort(-40))
        assertEquals("Today Δ on target", formatTodayKcalDeltaShort(0))
    }
}
