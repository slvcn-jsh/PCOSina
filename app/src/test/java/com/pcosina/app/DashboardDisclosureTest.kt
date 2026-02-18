package com.pcosina.app

import com.pcosina.app.ui.util.DASHBOARD_ADVANCED_MIN_STEP
import com.pcosina.app.ui.util.shouldShowAdvancedMetrics
import com.pcosina.app.ui.util.shouldShowAdvancedTools
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardDisclosureTest {
    @Test
    fun advancedContent_staysLockedBeforeStepSix() {
        assertFalse(shouldShowAdvancedTools(DASHBOARD_ADVANCED_MIN_STEP - 1))
        assertFalse(shouldShowAdvancedMetrics(DASHBOARD_ADVANCED_MIN_STEP - 1, hasTracked = false))
    }

    @Test
    fun advancedContent_unlocksAtStepSix() {
        assertTrue(shouldShowAdvancedTools(DASHBOARD_ADVANCED_MIN_STEP))
        assertTrue(shouldShowAdvancedMetrics(DASHBOARD_ADVANCED_MIN_STEP, hasTracked = false))
    }

    @Test
    fun trackedUser_canSeeAdvancedMetricsEarly() {
        assertTrue(shouldShowAdvancedMetrics(stepIndex = 4, hasTracked = true))
    }
}
