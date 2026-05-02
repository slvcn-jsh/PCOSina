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

        val typeNames = Regex("""const val (\w+) = "[^"]+"""")
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
    fun mealPlanRefinedScreen_usesInlineFeedbackAndViewModelEntryPoints() {
        val mealPlanPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "MealPlanRefinedScreen.kt"
        )
        val source = read(mealPlanPath)

        assertTrue(
            "MealPlan should keep inline feedback message state in the refined shell.",
            source.contains("var feedbackMessage by remember { mutableStateOf<String?>(null) }")
        )
        assertTrue(
            "Primary generation should route through MealPlanViewModel.generateMealPlan(profile).",
            source.contains("mealPlanViewModel.generateMealPlan(profile)")
        )
        assertTrue(
            "Replace-week flow should route through MealPlanViewModel.generateMealPlanFresh(profile).",
            source.contains("mealPlanViewModel.generateMealPlanFresh(profile)")
        )
        assertTrue(
            "Grocery sync path should route through extractGrocerySourcesForPlan in one place.",
            source.contains("mealPlanViewModel.extractGrocerySourcesForPlan { sources ->")
        )
        assertTrue(
            "Refined MealPlan screen should avoid direct notification dispatch calls.",
            !source.contains("NotificationScheduler.notifyPlanReady") &&
                !source.contains("NotificationScheduler.notifyGrocerySyncResult")
        )
        assertFalse(
            "Refined MealPlan screen should not keep a local pending plan-ready notification flag.",
            source.contains("pendingPlanReadyNotification")
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
