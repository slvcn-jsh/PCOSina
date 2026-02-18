package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReliabilityPolicyTest {

    @Test
    fun inactivitySource_prefersReflectionStoreWithLegacyFallback() {
        val repoPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "data", "repository", "UserPreferencesRepository.kt"
        )
        val source = read(repoPath)
        assertTrue(
            "Inactivity checks should read from ReflectionStore daily logs.",
            source.contains("reflectionStore.getDailyLogsJson(userId)")
        )
        assertTrue(
            "Inactivity checks should retain legacy DataStore fallback.",
            source.contains("getDailyLogsJson(userId).first()")
        )
    }

    @Test
    fun notificationHelper_checksAppLevelNotificationEnablement() {
        val helperPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationHelper.kt"
        )
        val source = read(helperPath)
        assertTrue(
            "NotificationHelper.canPostNotifications should guard app-level notification disablement.",
            source.contains("NotificationManagerCompat.from(context).areNotificationsEnabled()")
        )
    }

    @Test
    fun mealPlanScreen_clearsPendingPlanReadyFlagOnError() {
        val mealPlanPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "MealPlanScreen.kt"
        )
        val source = read(mealPlanPath)
        assertTrue(
            "Error state should clear pendingPlanReadyNotification to prevent false plan-ready notifications.",
            Regex("""is\s+MealPlanUiState\.Error\s*->\s*\{[\s\S]*?pendingPlanReadyNotification\s*=\s*false""")
                .containsMatchIn(source)
        )
    }

    @Test
    fun immediateNotifications_respectQuietHours() {
        val schedulerPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationScheduler.kt"
        )
        val source = read(schedulerPath)
        assertTrue(
            "Notification dispatch helper should enforce quiet hours for non-debug notifications.",
            Regex(
                """if\s*\(isNowWithinQuietHours\(prefs\)\s*&&\s*eventType\s*!=\s*NotificationEvents\.DebugTest\)\s*\{[\s\S]*return\s+false"""
            ).containsMatchIn(source)
        )
        assertTrue(
            "Plan-ready notifications should use centralized dispatch helper.",
            Regex("""notifyPlanReady[\s\S]*dispatchAndTrackNotification\(""")
                .containsMatchIn(source)
        )
        assertTrue(
            "Grocery sync notifications should use centralized dispatch helper.",
            Regex("""notifyGrocerySyncResult[\s\S]*dispatchAndTrackNotification\(""")
                .containsMatchIn(source)
        )
        assertTrue(
            "Weekly reset should enforce a strict minimum 7-day cooldown (max 1/week).",
            source.contains("TimeUnit.DAYS.toMillis(7)")
        )
    }

    @Test
    fun rescheduleAll_keepsExistingWorkWhenPostingIsBlocked() {
        val schedulerPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationScheduler.kt"
        )
        val source = read(schedulerPath)
        assertTrue(
            "rescheduleAll should return early when posting is blocked.",
            source.contains("if (!NotificationHelper.canPostNotifications(context))")
        )
        assertTrue(
            "Blocked-posting branch should explicitly keep existing work instead of canceling it.",
            source.contains("reschedule skipped: posting blocked, keeping existing work")
        )
        assertTrue(
            "Work cancellation should happen after posting-availability check to avoid dropping schedules.",
            Regex(
                """if\s*\(!NotificationHelper\.canPostNotifications\(context\)\)\s*\{[\s\S]*?return[\s\S]*?cancelAll\(context,\s*userId\)"""
            ).containsMatchIn(source)
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
