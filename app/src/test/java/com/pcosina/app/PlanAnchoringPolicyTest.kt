package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAnchoringPolicyTest {

    @Test
    fun repository_sendsStartDateWithGeneratePlanRequests() {
        val repo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "MealPlanRepository.kt"
            )
        )
        val api = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "api", "PcosinaApiService.kt"
            )
        )

        assertTrue("GeneratePlanRequest should expose startDate for anchored 7-day plans.", api.contains("val startDate: String? = null"))
        assertTrue(
            "MealPlanRepository should send today's ISO date as the plan start anchor.",
            repo.contains("startDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)")
        )
    }

    @Test
    fun progressScreen_keepsTransientImpactStateOutOfRememberSaveable() {
        val progress = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "ProgressScreen.kt"
            )
        )

        assertFalse(
            "ProgressScreen should not save transient MealImpactSummary in rememberSaveable.",
            progress.contains("rememberSaveable { mutableStateOf<MealImpactSummary?>(null) }")
        )
        assertTrue(
            "ProgressScreen should keep transient MealImpactSummary in regular remember state.",
            progress.contains("remember { mutableStateOf<MealImpactSummary?>(null) }")
        )
    }

    @Test
    fun mealPlanScreen_usesCompactTodayAndWeekendJumps() {
        val mealPlan = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "MealPlanScreen.kt"
            )
        )

        assertTrue("MealPlanScreen should use compact Today jump chip.", mealPlan.contains("label = { Text(\"Today\") }"))
        assertTrue("MealPlanScreen should use compact Weekend jump chip.", mealPlan.contains("label = { Text(\"Weekend\") }"))
        assertFalse("MealPlanScreen should not keep the long Jump to Today label.", mealPlan.contains("Text(\"Jump to Today ("))
        assertFalse("MealPlanScreen should not keep the long Jump to Weekend label.", mealPlan.contains("Text(\"Jump to Weekend (Sat)\")"))
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallback = Paths.get(parts.drop(1).first(), *parts.drop(2).toTypedArray())
        if (Files.exists(fallback)) return fallback
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
