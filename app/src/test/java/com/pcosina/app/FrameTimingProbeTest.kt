package com.pcosina.app

import com.pcosina.app.ui.util.summarizeFrameDurations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTimingProbeTest {

    @Test
    fun summarizeFrameDurations_computesExpectedStats() {
        val durationsNs = listOf(
            16_000_000L,
            17_000_000L,
            20_000_000L,
            33_000_000L,
            40_000_000L
        )
        val stats = summarizeFrameDurations(durationsNs, jankThresholdMs = 24)

        assertEquals(5, stats.frames)
        assertEquals(2, stats.jankFrames)
        assertTrue(stats.avgFrameMs > 0.0)
        assertTrue(stats.p95FrameMs >= stats.avgFrameMs)
        assertEquals(40.0, stats.worstFrameMs, 0.01)
    }

    @Test
    fun summarizeFrameDurations_handlesEmptyInput() {
        val stats = summarizeFrameDurations(emptyList(), jankThresholdMs = 24)

        assertEquals(0, stats.frames)
        assertEquals(0, stats.jankFrames)
        assertEquals(0.0, stats.avgFrameMs, 0.0)
        assertEquals(0.0, stats.jankRatio, 0.0)
    }
}
