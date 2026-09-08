package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardConnectivityPolicyTest {

    @Test
    fun dashboardTracksConnectivityAsReactiveState() {
        val dashboardPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "DashboardRefinedScreen.kt"
        )
        val connectivityStatePath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "util", "ConnectivityState.kt"
        )
        val source = read(dashboardPath)
        val connectivitySource = read(connectivityStatePath)

        assertTrue(
            "Dashboard should derive connectivity from rememberIsOnline().",
            source.contains("val observedOnline by rememberIsOnline(context)")
        )
        assertTrue(
            "Dashboard should support deterministic online-state override in UI tests.",
            source.contains("val isOnline = onlineStateOverride ?: observedOnline")
        )
        assertTrue(
            "Shared connectivity utility should register a network callback for live updates.",
            connectivitySource.contains("registerDefaultNetworkCallback(callback)")
        )
        assertTrue(
            "Shared connectivity utility should require Android's validated-internet bit before showing online sync readiness.",
            connectivitySource.contains("NET_CAPABILITY_VALIDATED")
        )
        assertFalse(
            "Dashboard must avoid one-shot connectivity snapshot for plan gating.",
            source.contains("remember(context) { isNetworkAvailable(context) }")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
