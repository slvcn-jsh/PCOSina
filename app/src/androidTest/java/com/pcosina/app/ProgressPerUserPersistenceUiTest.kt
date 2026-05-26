package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.screens.ProgressScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressPerUserPersistenceUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressModeAndAdvancedAnalytics_persistPerUserAcrossSwitch() {
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
        val userA = "progress_persist_user_a_${System.currentTimeMillis()}"
        val userB = "progress_persist_user_b_${System.currentTimeMillis()}"

        fun loadUser(userId: String) {
            progressViewModel.reset()
            userViewModel.loadProfileForUser(userId)
            bindMealPlanCurrentUserIdForTest(mealPlanViewModel, userId)
            bindGroceryCurrentUserIdForTest(groceryViewModel, userId)
            bindProgressCurrentUserIdForTest(progressViewModel, userId)
            val seeds = mealPlanViewModel.seedDemoWeeks(userViewModel.userProfile.value)
            progressViewModel.seedDemoWeeks(seeds)
            progressViewModel.loadProgressUiPreferences()
        }

        val renderedUserId = mutableStateOf(userA)

        composeRule.setContent {
            MaterialTheme {
                ProgressScreen(
                    userViewModel = userViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    progressViewModel = progressViewModel,
                    groceryViewModel = groceryViewModel,
                    userId = renderedUserId.value,
                    onBackToDashboard = {},
                    onNavigateToRoute = {},
                    onlineStateOverride = true
                )
            }
        }

        fun render(userId: String) {
            composeRule.runOnUiThread {
                renderedUserId.value = userId
            }
        }

        loadUser(userA)
        progressViewModel.setProgressModePreference("Week")
        progressViewModel.setAdvancedWeekAnalyticsExpandedPreference(true)
        render(userA)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("progress_content_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("progress_mode_week").performClick()
        composeRule.onNodeWithTag("progress_content_list")
            .performScrollToNode(hasTestTag("progress_week_macro_card"))
        composeRule.onNodeWithTag("progress_week_macro_card").assertIsDisplayed()

        loadUser(userB)
        render(userB)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("progress_mode_today").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("progress_mode_today").assertIsSelected()
        composeRule.onAllNodesWithTag("progress_week_macro_card").assertCountEquals(0)

        loadUser(userA)
        render(userA)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("progress_content_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("progress_mode_week").assertIsSelected()
        composeRule.onNodeWithTag("progress_content_list")
            .performScrollToNode(hasTestTag("progress_week_macro_card"))
        composeRule.onNodeWithTag("progress_week_macro_card").assertIsDisplayed()
    }
}
