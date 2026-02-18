package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class ProgressFeedbackFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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
        val weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        userViewModel.loadProfileForUser(userId)
        mealPlanViewModel.loadSavedPlan(userId)
        groceryViewModel.loadGroceryForUser(userId)
        progressViewModel.loadForUser(userId, weekStart)

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

        composeRule.onNodeWithText("Open Daily Reflection").performScrollTo().performClick()
        composeRule.onNodeWithText("Save Reflection").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Reflection saved for today.").assertIsDisplayed()
    }
}
