package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressFeedbackFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveReflection_showsButtonStateTransition_andFeedbackBannerMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "progress_feedback_test_user"
        val userPrefs = UserPreferencesRepository(context)
        val userViewModel = UserViewModel(userPrefs)
        val mealPlanViewModel = MealPlanViewModel(MealPlanRepository(), userPrefs)
        val groceryViewModel = GroceryViewModel(userPrefs)
        val progressViewModel = ProgressViewModel(
            userPrefsRepository = userPrefs,
            reflectionStore = ReflectionStore(context),
            feedbackRepository = FeedbackRepository(BuildConfig.BASE_URL)
        )
        userViewModel.loadProfileForUser(userId)
        bindMealPlanCurrentUserIdForTest(mealPlanViewModel, userId)
        bindGroceryCurrentUserIdForTest(groceryViewModel, userId)
        bindProgressCurrentUserIdForTest(progressViewModel, userId)
        val seeds = mealPlanViewModel.seedDemoWeeks(userViewModel.userProfile.value)
        progressViewModel.seedDemoWeeks(seeds)
        progressViewModel.setProgressModePreference("Today")
        val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
        val todayPlan = (mealPlanViewModel.uiState.value as? com.pcosina.app.ui.MealPlanUiState.Success)
            ?.response
            ?.days
            ?.firstOrNull { it.dayLabel.equals(todayLabel, ignoreCase = true) }
        todayPlan?.meals.orEmpty().forEach { meal ->
            progressViewModel.markMealAsEaten(LocalDate.now(), meal.recipeId, meal.mealLabel)
        }

        composeRule.setContent {
            MaterialTheme {
                ProgressScreen(
                    userViewModel = userViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    progressViewModel = progressViewModel,
                    groceryViewModel = groceryViewModel,
                    userId = userId,
                    onBackToDashboard = {},
                    onNavigateToRoute = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("progress_content_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("progress_content_list")
            .performScrollToNode(hasTestTag("progress_open_daily_reflection_cta"))
        composeRule.onNodeWithTag("progress_open_daily_reflection_cta").performClick()
        composeRule.onNodeWithText("Save Reflection").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Reflection saved for today.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Reflection saved for today.").assertIsDisplayed()
    }
}
