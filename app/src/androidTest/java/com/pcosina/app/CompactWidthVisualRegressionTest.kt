package com.pcosina.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.data.repository.AuthRepository
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.screens.DashboardTodayOutcomeCard
import com.pcosina.app.ui.screens.GoalSelectionScreen
import com.pcosina.app.ui.screens.LoginScreen
import com.pcosina.app.ui.screens.MealPlanScreen
import com.pcosina.app.ui.screens.ProgressScreen
import com.pcosina.app.ui.screens.TodayTimelineState
import com.pcosina.app.ui.screens.TodayTimelineStep
import com.pcosina.app.ui.screens.StepThreeDiet
import com.pcosina.app.ui.screens.UserProfileScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CompactWidthVisualRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactWidth_profileStep3_screenshotRegression() {
        val captureTag = "compact_profile_step3_capture"
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag(captureTag)
                    ) {
                        StepThreeDiet(
                            r1 = true,
                            onR1 = {},
                            r2 = true,
                            onR2 = {},
                            r3 = false,
                            onR3 = {},
                            r4 = false,
                            onR4 = {},
                            r5 = false,
                            onR5 = {},
                            budget = "1800",
                            onBudget = {},
                            maxCookingTime = "45",
                            onMaxCookingTime = {},
                            varietyPreference = "Balanced",
                            onVarietyPreference = {},
                            planningPriority = "Nutrition Tight",
                            onPlanningPriority = {},
                            pantryText = "eggs, oats, tuna, spinach",
                            onPantryText = {},
                            allergiesText = "dairy, shellfish, gluten",
                            onAllergiesText = {},
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        assertCompactScreenshot(captureTag, "compact_profile_step3")
    }

    @Test
    fun compactWidth_chipRow_screenshotRegression() {
        val captureTag = "compact_chip_row_capture"
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag(captureTag)
                    ) {
                        CompactChipRowFixture()
                    }
                }
            }
        }

        assertCompactScreenshot(captureTag, "compact_chip_row")
    }

    @Test
    fun compactWidth_dashboardTodayOutcome_screenshotRegression() {
        val captureTag = "compact_dashboard_outcome_capture"
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag(captureTag)
                    ) {
                        DashboardTodayOutcomeCard(
                            progress = 0.66f,
                            completedMealsLabel = "2/3 meals logged today",
                            nextLabel = "Next: Dinner • Ginger Garlic Fish Bowl",
                            deltaLabel = "Δ 160 kcal left",
                            timeline = listOf(
                                TodayTimelineStep("Breakfast", TodayTimelineState.Done),
                                TodayTimelineStep("Lunch", TodayTimelineState.Done),
                                TodayTimelineStep("Dinner", TodayTimelineState.Now)
                            ),
                            ctaLabel = "Open Next Unlogged Meal",
                            onPrimaryAction = {}
                        )
                    }
                }
            }
        }

        assertCompactScreenshot(captureTag, "compact_dashboard_today_outcome")
    }

    @Test
    fun compactWidth_progressTopSection_screenshotRegression() {
        val fixture = createProgressFixture("compact_progress_top_${System.currentTimeMillis()}")
        val rootTag = "compact_progress_top_root_capture"

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag(rootTag)
                    ) {
                        ProgressScreen(
                            userViewModel = fixture.userViewModel,
                            mealPlanViewModel = fixture.mealPlanViewModel,
                            progressViewModel = fixture.progressViewModel,
                            groceryViewModel = fixture.groceryViewModel,
                            userId = fixture.userId,
                            onBackToDashboard = {},
                            onNavigateToRoute = {},
                            onlineStateOverride = true
                        )
                    }
                }
            }
        }

        assertCompactScreenshot("progress_top_section_capture", "compact_progress_top_section")
    }

    @Test
    fun compactWidth_mealPlanTopSection_screenshotRegression() {
        val fixture = createMealPlanFixture("compact_mealplan_top_${System.currentTimeMillis()}")
        val rootTag = "compact_mealplan_top_root_capture"

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag(rootTag)
                    ) {
                        MealPlanScreen(
                            userViewModel = fixture.userViewModel,
                            mealPlanViewModel = fixture.mealPlanViewModel,
                            groceryViewModel = fixture.groceryViewModel,
                            progressViewModel = fixture.progressViewModel,
                            onRecipeClick = { _, _ -> },
                            onViewProgress = {},
                            onNavigateToRoute = {},
                            onlineStateOverride = true
                        )
                    }
                }
            }
        }

        assertCompactScreenshot(rootTag, "compact_mealplan_top_section")
    }

    @Test
    fun compactWidth_loginFirstWinCard_screenshotRegression() {
        val fixture = createFirstWinFixture("compact_login_first_win_${System.currentTimeMillis()}")
        val rootTag = "compact_login_first_win_root_capture"

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag(rootTag)
                    ) {
                        LoginScreen(
                            authViewModel = fixture.authViewModel,
                            onLoginSuccess = {},
                            onNavigateToSignUp = {},
                            onDebugFirstWinContinue = {}
                        )
                    }
                }
            }
        }

        assertCompactScreenshot("login_first_win_card", "compact_login_first_win_card")
    }

    @Test
    fun compactWidth_profileFirstWinCard_screenshotRegression() {
        val fixture = createFirstWinFixture("compact_profile_first_win_${System.currentTimeMillis()}")
        val rootTag = "compact_profile_first_win_root_capture"

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag(rootTag)
                    ) {
                        UserProfileScreen(
                            userViewModel = fixture.userViewModel,
                            onNext = {},
                            isEditMode = false
                        )
                    }
                }
            }
        }

        assertCompactScreenshot("profile_first_win_card", "compact_profile_first_win_card")
    }

    @Test
    fun compactWidth_goalHandoffCard_screenshotRegression() {
        val fixture = createFirstWinFixture("compact_goal_handoff_${System.currentTimeMillis()}")
        val rootTag = "compact_goal_handoff_root_capture"

        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag(rootTag)
                    ) {
                        GoalSelectionScreen(
                            userViewModel = fixture.userViewModel,
                            onFinish = {}
                        )
                    }
                }
            }
        }

        assertCompactScreenshot("goal_why_card", "compact_goal_handoff_card")
    }

    private fun assertCompactScreenshot(captureTag: String, baselineName: String) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(captureTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag(captureTag).assertCountEquals(1)
        val bitmap = composeRule.onNodeWithTag(captureTag).captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        assertTrue(hasVisualVariance(bitmap))

        val snapshotFile = saveCurrentSnapshot(bitmap, baselineName)
        val baseline = loadBaselineSnapshot(baselineName)
        if (baseline == null && requireBaselineAssets()) {
            fail(
                "Missing baseline asset for $baselineName. " +
                    "Current capture saved at ${snapshotFile.absolutePath}. " +
                    "Set require_visual_baseline=false for smoke mode or add a baseline PNG."
            )
        }
        if (baseline != null && !refreshBaselineMode()) {
            assertEquals("Baseline size mismatch for $baselineName", baseline.width, bitmap.width)
            assertEquals("Baseline size mismatch for $baselineName", baseline.height, bitmap.height)
            val delta = averagePixelDelta(baseline, bitmap)
            val threshold = visualDeltaThreshold()
            assertTrue(
                "Visual drift too high for $baselineName (delta=$delta, threshold=$threshold)",
                delta <= threshold
            )
        }
    }

    private fun saveCurrentSnapshot(bitmap: Bitmap, baselineName: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "visual_regression/current")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, "$baselineName.png")
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return outFile
    }

    private fun loadBaselineSnapshot(baselineName: String): Bitmap? {
        val context = InstrumentationRegistry.getInstrumentation().context
        return try {
            context.assets.open("visual_baselines/$baselineName.png").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun averagePixelDelta(reference: Bitmap, candidate: Bitmap): Double {
        val sampleDivisorX = visualSampleDivisorX()
        val sampleDivisorY = visualSampleDivisorY()
        val stepX = (reference.width / sampleDivisorX).coerceAtLeast(1)
        val stepY = (reference.height / sampleDivisorY).coerceAtLeast(1)
        var total = 0L
        var count = 0
        var y = 0
        while (y < reference.height) {
            var x = 0
            while (x < reference.width) {
                val a = reference.getPixel(x, y)
                val b = candidate.getPixel(x, y)
                val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
                val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
                val db = abs((a and 0xFF) - (b and 0xFF))
                total += (dr + dg + db).toLong()
                count += 3
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0.0 else total.toDouble() / count.toDouble()
    }

    private fun hasVisualVariance(bitmap: Bitmap): Boolean {
        val first = bitmap.getPixel(0, 0)
        val stepX = (bitmap.width / 40).coerceAtLeast(1)
        val stepY = (bitmap.height / 40).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) != first) return true
                x += stepX
            }
            y += stepY
        }
        return false
    }

    private fun visualDeltaThreshold(): Double {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("visual_delta_threshold")?.toDoubleOrNull() ?: 10.0
    }

    private fun visualSampleDivisorX(): Int {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("visual_sample_divisor_x")?.toIntOrNull()?.coerceAtLeast(8) ?: 72
    }

    private fun visualSampleDivisorY(): Int {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("visual_sample_divisor_y")?.toIntOrNull()?.coerceAtLeast(8) ?: 96
    }

    private fun refreshBaselineMode(): Boolean {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("refresh_visual_baseline")?.toBooleanStrictOrNull() ?: false
    }

    private fun requireBaselineAssets(): Boolean {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("require_visual_baseline")?.toBooleanStrictOrNull() ?: false
    }

    private fun createProgressFixture(userId: String): ProgressFixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userPrefs = UserPreferencesRepository(context)
        val userViewModel = UserViewModel(userPrefs)
        val mealPlanViewModel = MealPlanViewModel(MealPlanRepository(), userPrefs)
        val groceryViewModel = GroceryViewModel(userPrefs)
        val progressViewModel = ProgressViewModel(
            userPrefsRepository = userPrefs,
            reflectionStore = ReflectionStore(context),
            feedbackRepository = FeedbackRepository(BuildConfig.BASE_URL)
        )
        val weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        userViewModel.loadProfileForUser(userId)
        mealPlanViewModel.loadSavedPlan(userId)
        groceryViewModel.loadGroceryForUser(userId)
        progressViewModel.loadForUser(userId, weekStart)
        val seeds = mealPlanViewModel.seedDemoWeeks(userViewModel.userProfile.value)
        progressViewModel.seedDemoWeeks(seeds)

        return ProgressFixture(
            userId = userId,
            userViewModel = userViewModel,
            mealPlanViewModel = mealPlanViewModel,
            groceryViewModel = groceryViewModel,
            progressViewModel = progressViewModel
        )
    }

    private fun createMealPlanFixture(userId: String): MealPlanFixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userPrefs = UserPreferencesRepository(context)
        val userViewModel = UserViewModel(userPrefs)
        val mealPlanViewModel = MealPlanViewModel(MealPlanRepository(), userPrefs)
        val groceryViewModel = GroceryViewModel(userPrefs)
        val progressViewModel = ProgressViewModel(
            userPrefsRepository = userPrefs,
            reflectionStore = ReflectionStore(context),
            feedbackRepository = FeedbackRepository(BuildConfig.BASE_URL)
        )
        val weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        userViewModel.loadProfileForUser(userId)
        mealPlanViewModel.loadSavedPlan(userId)
        groceryViewModel.loadGroceryForUser(userId)
        progressViewModel.loadForUser(userId, weekStart)
        val seeds = mealPlanViewModel.seedDemoWeeks(userViewModel.userProfile.value)
        progressViewModel.seedDemoWeeks(seeds)

        return MealPlanFixture(
            userViewModel = userViewModel,
            mealPlanViewModel = mealPlanViewModel,
            groceryViewModel = groceryViewModel,
            progressViewModel = progressViewModel
        )
    }

    private fun createFirstWinFixture(userId: String): FirstWinCompactFixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userPrefs = UserPreferencesRepository(context)
        val userViewModel = UserViewModel(userPrefs)
        val authViewModel = AuthViewModel(AuthRepository(context))
        userViewModel.loadProfileForUser(userId)
        return FirstWinCompactFixture(
            authViewModel = authViewModel,
            userViewModel = userViewModel
        )
    }
}

private data class ProgressFixture(
    val userId: String,
    val userViewModel: UserViewModel,
    val mealPlanViewModel: MealPlanViewModel,
    val groceryViewModel: GroceryViewModel,
    val progressViewModel: ProgressViewModel
)

private data class MealPlanFixture(
    val userViewModel: UserViewModel,
    val mealPlanViewModel: MealPlanViewModel,
    val groceryViewModel: GroceryViewModel,
    val progressViewModel: ProgressViewModel
)

private data class FirstWinCompactFixture(
    val authViewModel: AuthViewModel,
    val userViewModel: UserViewModel
)

@androidx.compose.runtime.Composable
private fun CompactChipRowFixture() {
    val options = listOf(
        "Monthly (≈ weekly × 4.33)",
        "Target kcal formula",
        "Nutrition Tight",
        "Gluten/Wheat"
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEachIndexed { colIndex, label ->
                    val absoluteIndex = rowIndex * 2 + colIndex
                    FilterChip(
                        selected = absoluteIndex % 2 == 0,
                        onClick = {},
                        label = { Text(label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}
