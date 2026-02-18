package com.pcosina.app.ui.util

import androidx.compose.runtime.withFrameNanos

data class FrameTimingStats(
    val frames: Int,
    val avgFrameMs: Double,
    val p95FrameMs: Double,
    val worstFrameMs: Double,
    val jankFrames: Int,
    val jankRatio: Double
)

suspend fun sampleFrameTiming(
    windowMs: Int,
    jankThresholdMs: Int
): FrameTimingStats {
    val durationsNs = mutableListOf<Long>()
    val startNs = withFrameNanos { it }
    var previousNs = startNs
    while (true) {
        val currentNs = withFrameNanos { it }
        durationsNs += (currentNs - previousNs).coerceAtLeast(0L)
        previousNs = currentNs
        if ((currentNs - startNs) >= windowMs * 1_000_000L) break
    }
    return summarizeFrameDurations(durationsNs, jankThresholdMs)
}

fun summarizeFrameDurations(
    durationsNs: List<Long>,
    jankThresholdMs: Int
): FrameTimingStats {
    if (durationsNs.isEmpty()) {
        return FrameTimingStats(
            frames = 0,
            avgFrameMs = 0.0,
            p95FrameMs = 0.0,
            worstFrameMs = 0.0,
            jankFrames = 0,
            jankRatio = 0.0
        )
    }

    val durationsMs = durationsNs.map { it / 1_000_000.0 }
    val sorted = durationsMs.sorted()
    val p95Index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
    val jankFrames = durationsMs.count { it >= jankThresholdMs }
    val frames = durationsMs.size
    return FrameTimingStats(
        frames = frames,
        avgFrameMs = durationsMs.average(),
        p95FrameMs = sorted[p95Index],
        worstFrameMs = sorted.last(),
        jankFrames = jankFrames,
        jankRatio = if (frames == 0) 0.0 else jankFrames.toDouble() / frames.toDouble()
    )
}
