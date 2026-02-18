package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLifecycleReschedulePolicyTest {

    @Test
    fun appNavHost_reschedulesOnSessionOrPreferenceChanges_notEveryForeground() {
        val navHostPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "navigation", "AppNavHost.kt"
        )
        val source = read(navHostPath)
        assertTrue(
            "AppNavHost should still trigger scheduler through session/preference effect.",
            source.contains("LaunchedEffect(session.currentUserUid, notificationPrefs)")
        )
        assertTrue(
            "AppNavHost should call NotificationScheduler.rescheduleAll when inputs change.",
            source.contains("NotificationScheduler.rescheduleAll(")
        )
        assertFalse(
            "AppNavHost should not reschedule notifications on every foreground ON_START.",
            source.contains("Lifecycle.Event.ON_START")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallbackParts = parts.drop(1).toTypedArray()
        val second = Paths.get(fallbackParts.first(), *fallbackParts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
