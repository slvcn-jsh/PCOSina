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
import com.google.gson.Gson
import com.pcosina.app.data.model.FeedbackEntry
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.screens.ProgressScreen
import com.pcosina.app.ui.util.ActionFeedbackCopy
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressRetryBannerUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun retryAll_offline_showsQueueSavedBanner() {
        val queue = listOf(
            FeedbackEntry(id = "failed_1", message = "first", status = "Failed", attempts = 1, lastError = "network"),
            FeedbackEntry(id = "failed_2", message = "second", status = "Failed", attempts = 1, lastError = "timeout")
        )
        val fixture = createFixture(
            userId = "progress_retry_offline_${System.currentTimeMillis()}",
            initialQueue = queue
        )

        composeRule.setContent {
            MaterialTheme {
                ProgressScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    progressViewModel = fixture.progressViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    userId = fixture.userId,
                    onBackToDashboard = {},
                    onNavigateToRoute = {},
                    onlineStateOverride = false,
                    feedbackSectionExpandedByDefault = true
                )
            }
        }

        composeRule.onNodeWithText("Retry all").performScrollTo().performClick()
        composeRule.onNodeWithText(ActionFeedbackCopy.QueueSaved).assertIsDisplayed()
    }

    @Test
    fun retrySingle_online_showsQueueRetryingBanner() {
        val queue = listOf(
            FeedbackEntry(id = "failed_single", message = "single", status = "Failed", attempts = 1, lastError = "network")
        )
        val fixture = createFixture(
            userId = "progress_retry_online_${System.currentTimeMillis()}",
            initialQueue = queue
        )

        composeRule.setContent {
            MaterialTheme {
                ProgressScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    progressViewModel = fixture.progressViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    userId = fixture.userId,
                    onBackToDashboard = {},
                    onNavigateToRoute = {},
                    onlineStateOverride = true,
                    feedbackSectionExpandedByDefault = true
                )
            }
        }

        composeRule.onAllNodesWithText("Retry")[0].performScrollTo().performClick()
        composeRule.onNodeWithText(ActionFeedbackCopy.QueueRetrying).assertIsDisplayed()
    }

    private fun createFixture(userId: String, initialQueue: List<FeedbackEntry>): Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userPrefs = UserPreferencesRepository(context)
        runBlocking {
            userPrefs.saveFeedbackQueueJson(userId, Gson().toJson(initialQueue))
        }
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

        return Fixture(
            userId = userId,
            userViewModel = userViewModel,
            mealPlanViewModel = mealPlanViewModel,
            groceryViewModel = groceryViewModel,
            progressViewModel = progressViewModel
        )
    }

    private data class Fixture(
        val userId: String,
        val userViewModel: UserViewModel,
        val mealPlanViewModel: MealPlanViewModel,
        val groceryViewModel: GroceryViewModel,
        val progressViewModel: ProgressViewModel
    )
}
