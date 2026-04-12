package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGuardrailPolicyTest {

    @Test
    fun notificationBuilder_callsAreCentralizedInHelper() {
        val sourceRoot = resolveMainSourceRoot()
        val violations = mutableListOf<String>()
        val builderRegex = Regex("""NotificationCompat\s*\.\s*Builder\s*\(""")

        Files.walk(sourceRoot).use { paths ->
            paths.asSequence()
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .forEach { file ->
                    if (file.fileName.toString() == "NotificationHelper.kt") return@forEach
                    Files.readAllLines(file).forEachIndexed { index, line ->
                        if (builderRegex.containsMatchIn(line)) {
                            violations.add("${sourceRoot.relativize(file)}:${index + 1} -> ${line.trim()}")
                        }
                    }
                }
        }

        assertTrue(
            "Use NotificationHelper.showNotification() instead of direct NotificationCompat.Builder:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun schedulerTagDiscovery_usesSharedTagRegistry() {
        val schedulerPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationScheduler.kt"
        )
        val eventsPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationEvents.kt"
        )
        val scheduler = read(schedulerPath)
        val events = read(eventsPath)

        assertTrue("NotificationEvents should expose SchedulerTags registry.", events.contains("val SchedulerTags ="))
        assertTrue(
            "NotificationScheduler should use NotificationEvents.SchedulerTags to avoid tag drift.",
            scheduler.contains("NotificationEvents.SchedulerTags")
        )
    }

    @Test
    fun schedulerNotificationDispatch_isCentralized() {
        val schedulerPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationScheduler.kt"
        )
        val source = read(schedulerPath)
        assertTrue(
            "NotificationScheduler should centralize posting in dispatchAndTrackNotification().",
            source.contains("private suspend fun dispatchAndTrackNotification(")
        )
        val directShowCalls = Regex("""NotificationHelper\.showNotification\s*\(""")
            .findAll(source)
            .count()
        assertEquals(
            "NotificationScheduler should call NotificationHelper.showNotification only inside dispatch helper.",
            1,
            directShowCalls
        )
    }

    @Test
    fun configurableNotificationTypes_requireExplicitTypeMapping() {
        val eventsPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationEvents.kt"
        )
        val schedulerPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "notifications", "NotificationScheduler.kt"
        )
        val events = read(eventsPath)
        val scheduler = read(schedulerPath)

        val typeNames = Regex("""const val (\w+) = \"[^\"]+\"""")
            .findAll(events)
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("Tag") || it.startsWith("Work") }
            .toList()

        assertTrue(
            "NotificationScheduler must define explicit type gating via isTypeEnabled().",
            scheduler.contains("internal fun isTypeEnabled(")
        )
        typeNames.forEach { name ->
            assertTrue(
                "Notification type '$name' must be explicitly handled in isTypeEnabled().",
                scheduler.contains("NotificationEvents.$name")
            )
        }
    }

    @Test
    fun mealPlanEntryPoints_useUnifiedFeedbackTriggerFunctions() {
        val mealPlanPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "MealPlanScreen.kt"
        )
        val source = read(mealPlanPath)

        assertTrue(
            "MealPlan should define triggerPlanGeneration().",
            Regex("""fun\s+triggerPlanGeneration\s*\(\s*\)""").containsMatchIn(source)
        )
        assertTrue(
            "MealPlan should define triggerGrocerySync().",
            Regex("""fun\s+triggerGrocerySync\s*\([^)]*\)""").containsMatchIn(source)
        )
        assertTrue("MealPlan should render AppFeedbackBanner for action feedback.", source.contains("AppFeedbackBanner("))

        val generateCalls = Regex("""generateMealPlan\s*\(userProfile\)""")
            .findAll(source)
            .count()
        assertEquals(
            "Generate entry points should route through triggerPlanGeneration() only.",
            1,
            generateCalls
        )

        val syncCalls = Regex("""extractGrocerySourcesForPlan\s*\{""")
            .findAll(source)
            .count()
        assertEquals(
            "Sync entry points should route through triggerGrocerySync() only.",
            1,
            syncCalls
        )
        val loadingButtons = Regex("""LoadingActionButton\s*\(""")
            .findAll(source)
            .count()
        assertTrue(
            "Core plan/sync actions should use LoadingActionButton for consistent feedback state machine.",
            loadingButtons >= 2
        )
        val rawGenerateButtons = Regex("""(?<![A-Za-z])Button\s*\(\s*onClick\s*=\s*\{\s*triggerPlanGeneration\(\)\s*\}""")
            .findAll(source)
            .count()
        assertFalse(
            "Use LoadingActionButton for triggerPlanGeneration actions instead of plain Button.",
            rawGenerateButtons > 0
        )
        val rawSyncButtons = Regex("""(?<![A-Za-z])Button\s*\(\s*onClick\s*=\s*\{\s*triggerGrocerySync\(\)\s*\}""")
            .findAll(source)
            .count()
        assertFalse(
            "Use LoadingActionButton for triggerGrocerySync actions instead of plain Button.",
            rawSyncButtons > 0
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for notification policy test.")
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
