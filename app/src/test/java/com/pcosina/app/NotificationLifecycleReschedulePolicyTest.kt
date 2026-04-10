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

    @Test
    fun broadcastReschedule_wiresManifestActions_andCallsScheduler() {
        val manifestPath = resolve(
            "app", "src", "main", "AndroidManifest.xml"
        )
        val receiverPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationRescheduleReceiver.kt"
        )
        val manifest = read(manifestPath)
        val receiver = read(receiverPath)
        assertTrue(
            "Manifest should register NotificationRescheduleReceiver for boot/time/system-change actions.",
            manifest.contains("NotificationRescheduleReceiver") &&
                manifest.contains("android.intent.action.BOOT_COMPLETED") &&
                manifest.contains("android.intent.action.MY_PACKAGE_REPLACED") &&
                manifest.contains("android.intent.action.TIME_SET") &&
                manifest.contains("android.intent.action.TIMEZONE_CHANGED")
        )
        assertTrue(
            "Receiver should route system-change broadcasts back into NotificationScheduler.rescheduleAll.",
            receiver.contains("NotificationScheduler.rescheduleAll(")
        )
        assertTrue(
            "Receiver should fall back to persisted auth session state before treating boot/update as signed-out.",
            receiver.contains("AuthRepository(appContext)") &&
                receiver.contains("getPersistedCurrentUserUid()")
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
