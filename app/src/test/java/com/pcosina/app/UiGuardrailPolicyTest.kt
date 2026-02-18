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

        Files.list(screenDir).use { paths ->
            paths.asSequence()
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith("Screen.kt") }
                .forEach { file ->
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
    fun lockedCards_includeLearnMoreAffordance() {
        val progress = read(resolveScreen("ProgressScreen.kt"))
        val grocery = read(resolveScreen("GroceryListScreen.kt"))
        val dashboard = read(resolveScreen("DashboardScreen.kt"))

        assertTrue("Progress locked states should expose Learn more.", progress.contains("LockedFlowCopy.LearnMoreLabel"))
        assertTrue("Grocery locked states should expose Learn more.", grocery.contains("LockedFlowCopy.LearnMoreLabel"))
        assertTrue("Dashboard locked states should expose Learn more.", dashboard.contains("LockedFlowCopy.LearnMoreLabel"))
    }

    @Test
    fun lockedFlowCopy_isCentralizedForCoreLockedStates() {
        val progress = read(resolveScreen("ProgressScreen.kt"))
        val grocery = read(resolveScreen("GroceryListScreen.kt"))
        val dashboard = read(resolveScreen("DashboardScreen.kt"))

        assertTrue("Progress should use shared locked-flow copy.", progress.contains("LockedFlowCopy"))
        assertTrue("Grocery should use shared locked-flow copy.", grocery.contains("LockedFlowCopy"))
        assertTrue("Dashboard should use shared locked-flow copy.", dashboard.contains("LockedFlowCopy"))
    }

    @Test
    fun learnMoreOwnership_avoidsLiteralDriftInCoreLockedScreens() {
        val progress = read(resolveScreen("ProgressScreen.kt"))
        val grocery = read(resolveScreen("GroceryListScreen.kt"))
        val dashboard = read(resolveScreen("DashboardScreen.kt"))

        assertFalse("Use LockedFlowCopy label, not a literal Learn more.", progress.contains("\"Learn more\""))
        assertFalse("Use LockedFlowCopy label, not a literal Learn more.", grocery.contains("\"Learn more\""))
        assertFalse("Use LockedFlowCopy label, not a literal Learn more.", dashboard.contains("\"Learn more\""))
        assertFalse("Use LockedFlowCopy title, not a literal tracking-lock title.", progress.contains("\"Why tracking is locked\""))
        assertFalse("Use LockedFlowCopy title, not a literal grocery-lock title.", grocery.contains("\"Why grocery is locked\""))
    }

    @Test
    fun progressWeekHistory_compactFlowDefaultsCollapsedAndAutoCollapses() {
        val progress = read(resolveScreen("ProgressScreen.kt"))

        assertTrue(
            "Progress should define compact-width week history behavior.",
            progress.contains("val collapseWeekHistoryOnCompact = screenWidthDp <= 360")
        )
        assertTrue(
            "Week history expansion state should be keyed to compact-width behavior.",
            progress.contains("rememberSaveable(collapseWeekHistoryOnCompact)")
        )
        assertTrue(
            "Week history should default collapsed on compact widths.",
            progress.contains("defaultExpanded = !collapseWeekHistoryOnCompact")
        )
        assertTrue(
            "Week history should use controlled expansion state for policy consistency.",
            progress.contains("expanded = weekHistoryExpanded") &&
                progress.contains("onExpandedChange = { weekHistoryExpanded = it }")
        )

        val autoCollapseMatches = Regex("""weekHistoryExpanded\s*=\s*false""")
            .findAll(progress)
            .count()
        assertTrue(
            "Compact week history should auto-collapse after navigation/selection actions.",
            autoCollapseMatches >= 3
        )
    }

    @Test
    fun progressReadOnlyState_offersJumpToTodayAction() {
        val progress = read(resolveScreen("ProgressScreen.kt"))
        assertTrue(
            "Read-only daily check-off state should compute today's index in the selected week.",
            progress.contains("todayIndexInWeek")
        )
        assertTrue(
            "Read-only daily check-off state should offer a Jump to Today action.",
            progress.contains("Jump to Today")
        )
        assertTrue(
            "Reflection section should also expose Jump to Today for symmetry.",
            progress.contains("ProgressJumpToTodayAction(")
        )
    }

    @Test
    fun progressSelectedDayCompletion_usesSlotSnapshotNotRecipeOnlyMatching() {
        val progress = read(resolveScreen("ProgressScreen.kt"))
        assertTrue(
            "Selected-day completion should use TodayMealDescriptor slot snapshots.",
            progress.contains("val selectedDaySnapshot = remember(selectedDayDescriptors, selectedCompletedIds)")
        )
        assertTrue(
            "Selected-day completion count should come from selectedDaySnapshot.completedCount.",
            progress.contains("val selectedCompletedCount = selectedDaySnapshot.completedCount")
        )
        assertFalse(
            "Selected-day completion must not use recipe-only counting by extractRecipeId.",
            Regex("""plannedMealsForDay\.count\s*\{\s*meal\s*->[\s\S]*extractRecipeId""")
                .containsMatchIn(progress)
        )
    }

    @Test
    fun mealImpactSuggestions_areSharedAcrossProgressAndRecipeDetails() {
        val progress = read(resolveScreen("ProgressScreen.kt"))
        val recipe = read(resolveScreen("RecipeDetailsScreen.kt"))
        val sharedCopy = read(
            resolve("app", "src", "main", "java", "com", "pcosina", "app", "ui", "util", "MealImpactCopy.kt")
        )

        assertTrue("Shared meal-impact suggestion utility should exist.", sharedCopy.contains("fun mealImpactNextSuggestion("))
        assertTrue("Progress should use shared meal-impact suggestion utility.", progress.contains("mealImpactNextSuggestion("))
        assertTrue("Recipe details should use shared meal-impact suggestion utility.", recipe.contains("mealImpactNextSuggestion("))
        assertFalse("Progress should not keep local duplicated suggestion template.", progress.contains("private fun mealTypeAwareSuggestion("))
    }

    @Test
    fun impactMetrics_useSharedShortFormattersAcrossProgressAndRecipeDetails() {
        val progress = read(resolveScreen("ProgressScreen.kt"))
        val recipe = read(resolveScreen("RecipeDetailsScreen.kt"))
        val formatter = read(
            resolve("app", "src", "main", "java", "com", "pcosina", "app", "ui", "util", "ImpactMetricFormatter.kt")
        )

        assertTrue("Shared impact metric formatter utility should exist.", formatter.contains("formatKcalProgressShort"))
        assertTrue("Progress should use shared kcal formatter.", progress.contains("formatKcalProgressShort("))
        assertTrue("Progress should use shared protein formatter.", progress.contains("formatProteinProgressShort("))
        assertTrue("Progress should use shared fiber formatter.", progress.contains("formatFiberProgressShort("))
        assertTrue("Recipe details should use shared kcal formatter.", recipe.contains("formatKcalProgressShort("))
        assertTrue("Recipe details should use shared protein formatter.", recipe.contains("formatProteinProgressShort("))
        assertTrue("Recipe details should use shared fiber formatter.", recipe.contains("formatFiberProgressShort("))
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
            "DashboardScreen.kt",
            "GroceryListScreen.kt",
            "IpoVisualizationScreen.kt",
            "MealPlanScreen.kt",
            "MoreToolsScreen.kt",
            "ProgressScreen.kt",
            "RecipeDetailsScreen.kt",
            "SettingsScreen.kt",
            "UserProfileScreen.kt"
        )

        private val chipTokenScreens = setOf(
            "DashboardScreen.kt",
            "GroceryListScreen.kt",
            "MealPlanScreen.kt",
            "ProgressScreen.kt",
            "UserProfileScreen.kt"
        )

        private val policyExemptScreens = setOf(
            "GoalSelectionScreen.kt",
            "LoginScreen.kt",
            "SignUpScreen.kt",
            "SplashScreen.kt"
        )
    }
}
