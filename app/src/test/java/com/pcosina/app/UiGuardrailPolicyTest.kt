package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiGuardrailPolicyTest {

    @Test
    fun allScreens_areClassifiedForUiPolicyScope() {
        val discovered = discoverScreenFileNames()
        val classified = spacingTokenScreens + chipTokenScreens + policyExemptScreens
        assertEquals(
            "Every *Screen.kt must be explicitly classified for UI policy scope.",
            discovered,
            classified
        )
    }

    @Test
    fun spacingTokens_areUsedInClassifiedScreens() {
        val files = spacingTokenScreens.map { resolveScreen(it) }
        files.forEach { file ->
            val text = read(file)
            assertTrue("Expected UiSpacingTokens import in ${file.fileName}", text.contains("UiSpacingTokens"))
            assertFalse(
                "Avoid local spacing constants in ${file.fileName}; prefer UiSpacingTokens.",
                Regex("""private\s+val\s+\w*Spacing\w*\s*=\s*\d+\.dp""").containsMatchIn(text)
            )
        }
    }

    @Test
    fun chipWidthClassRules_areTokenizedInChipHeavyScreens() {
        val files = chipTokenScreens.map { resolveScreen(it) }

        files.forEach { file ->
            val text = read(file)
            assertTrue("Expected UiChipTokens usage in ${file.fileName}", text.contains("UiChipTokens"))
            assertTrue("Expected width-class utility usage in ${file.fileName}", text.contains("widthByClass("))
            assertTrue(
                "Expected tokenized min touch height in ${file.fileName}",
                text.contains("UiChipTokens.MinTouchHeight") ||
                    text.contains("TokenizedFilterChip(") ||
                    text.contains("heightIn(min = 48.dp)")
            )
        }
    }

    @Test
    fun chipBearingScreens_detectedByRegexUseTokenGuardrails() {
        val screenDir = resolveScreenDir()
        val chipCallRegex = Regex("""\b(?:AssistChip|FilterChip|InputChip|SuggestionChip)\s*\(""")
        val violations = mutableListOf<String>()
        val exempt = policyExemptScreens

        Files.list(screenDir).use { paths ->
            paths.asSequence()
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith("Screen.kt") }
                .forEach { file ->
                    if (file.fileName.toString() in exempt) return@forEach
                    val text = read(file)
                    if (chipCallRegex.containsMatchIn(text)) {
                        val hasGuardrails = text.contains("UiChipTokens") || text.contains("TokenizedFilterChip(")
                        if (!hasGuardrails) violations.add(file.fileName.toString())
                    }
                }
        }

        assertTrue(
            "Chip-bearing screens must use UiChipTokens or TokenizedFilterChip: ${violations.joinToString()}",
            violations.isEmpty()
        )
    }

    @Test
    fun refinedCoreScreens_doNotDependOnLegacyLockedFlowCopy() {
        val refinedScreens = listOf(
            "DashboardRefinedScreen.kt",
            "GroceryRefinedScreen.kt",
            "MealPlanRefinedScreen.kt",
            "ProgressRefinedScreen.kt"
        )

        refinedScreens.forEach { fileName ->
            val text = read(resolveScreen(fileName))
            assertFalse("$fileName should not depend on legacy LockedFlowCopy.", text.contains("LockedFlowCopy"))
        }
    }

    @Test
    fun progressRefinedScreen_usesWeeklyDashboardSections() {
        val progress = read(resolveScreen("ProgressRefinedScreen.kt"))

        assertTrue(
            "Refined progress should surface weekly savings.",
            progress.contains("Weekly savings")
        )
        assertTrue(
            "Refined progress should surface average daily macros.",
            progress.contains("Average daily macros")
        )
        assertTrue(
            "Refined progress should keep the weekly review CTA visible.",
            progress.contains("text = \"Review week\"")
        )
    }

    @Test
    fun progressRefinedScreen_avoidsLegacyFocusModeArtifacts() {
        val progress = read(resolveScreen("ProgressRefinedScreen.kt"))

        assertFalse("Refined progress should not expose the old Jump to Today CTA.", progress.contains("Jump to Today"))
        assertFalse("Refined progress should not keep old traversal card ids.", progress.contains("progress_week_insights_card"))
        assertFalse("Refined progress should not keep the old Track focus label.", progress.contains("ProgressScreenFocus.Track"))
        assertTrue("Refined progress should keep the Check in CTA.", progress.contains("Check in"))
        assertTrue("Refined progress should keep the Review week CTA.", progress.contains("Review week"))
    }

    @Test
    fun recipeDetails_usesSharedMealImpactSuggestionUtility() {
        val recipe = read(resolveScreen("RecipeDetailsScreen.kt"))
        val sharedCopy = read(
            resolve("app", "src", "main", "java", "com", "pcosina", "app", "ui", "util", "MealImpactCopy.kt")
        )

        assertTrue("Shared meal-impact suggestion utility should exist.", sharedCopy.contains("fun mealImpactNextSuggestion("))
        assertTrue("Recipe details should use shared meal-impact suggestion utility.", recipe.contains("mealImpactNextSuggestion("))
    }

    @Test
    fun recipeDetails_usesSharedImpactMetricFormatters() {
        val recipe = read(resolveScreen("RecipeDetailsScreen.kt"))
        val formatter = read(
            resolve("app", "src", "main", "java", "com", "pcosina", "app", "ui", "util", "ImpactMetricFormatter.kt")
        )

        assertTrue("Shared impact metric formatter utility should exist.", formatter.contains("formatKcalProgressShort"))
        assertTrue("Recipe details should use shared kcal formatter.", recipe.contains("formatKcalProgressShort("))
        assertTrue("Recipe details should use shared protein formatter.", recipe.contains("formatProteinProgressShort("))
        assertTrue("Recipe details should use shared fiber formatter.", recipe.contains("formatFiberProgressShort("))
    }

    @Test
    fun adminOperatorRouting_doesNotWhitelistClientMoreToolsSurface() {
        val navHost = read(
            resolve("app", "src", "main", "java", "com", "pcosina", "app", "ui", "navigation", "AppNavHost.kt")
        )

        assertFalse("Operator accounts should not get a MoreTools client-side bypass.", navHost.contains("isOperatorReviewRoute"))
        assertTrue(
            "Operator dashboard More Tools entry should stay in admin methodology.",
            navHost.contains("onOpenMoreTools = { navigateInternal(Routes.AdminMethodology) }")
        )
    }

    private fun discoverScreenFileNames(): Set<String> {
        val screenDir = resolveScreenDir()
        Files.list(screenDir).use { paths ->
            return paths.asSequence()
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith("Screen.kt") }
                .map { it.fileName.toString() }
                .toSet()
        }
    }

    private fun resolveScreenDir(): Path {
        val first = Paths.get("app", "src", "main", "java", "com", "pcosina", "app", "ui", "screens")
        if (Files.exists(first)) return first
        val second = Paths.get("src", "main", "java", "com", "pcosina", "app", "ui", "screens")
        if (Files.exists(second)) return second
        error("Could not locate ui/screens directory.")
    }

    private fun resolveScreen(fileName: String): Path {
        val first = resolveScreenDir().resolve(fileName)
        if (Files.exists(first)) return first
        error("Could not locate screen file: $fileName")
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

    companion object {
        private val spacingTokenScreens = setOf(
            "IpoVisualizationScreen.kt",
            "RecipeDetailsScreen.kt",
            "SettingsScreen.kt",
            "UserProfileScreen.kt"
        )

        private val chipTokenScreens = setOf(
            "UserProfileScreen.kt"
        )

        private val policyExemptScreens = setOf(
            "CommunityScreen.kt",
            "DashboardRefinedScreen.kt",
            "GoalSelectionScreen.kt",
            "GroceryRefinedScreen.kt",
            "LoginScreen.kt",
            "MealPlanRefinedScreen.kt",
            "MoreToolsScreen.kt",
            "NotificationScreen.kt",
            "OperatorDashboardScreen.kt",
            "ProgressRefinedScreen.kt",
            "SignUpScreen.kt",
            "SplashScreen.kt"
        )
    }
}
