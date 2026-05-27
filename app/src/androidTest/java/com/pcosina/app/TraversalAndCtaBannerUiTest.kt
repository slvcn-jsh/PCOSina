package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.repository.AuthRepository
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.screens.DashboardScreen
import com.pcosina.app.ui.screens.ProgressScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraversalAndCtaBannerUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboardPrimaryCard_hasTraversalOrder_andPrimaryCtaShowsBanner() {
        val fixture = createFixture("dashboard_traversal_banner_${System.currentTimeMillis()}")
        val openedPlan = mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                DashboardScreen(
                    userViewModel = fixture.userViewModel,
                    authViewModel = fixture.authViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onViewPlan = { openedPlan.value = true },
                    onNavigateToRoute = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_content_list")
            .performScrollToNode(hasTestTag("dashboard_primary_next_card"))
        composeRule.onNodeWithTag("dashboard_primary_next_card")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
        composeRule.onNodeWithText("Go to Plan").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            openedPlan.value
        }
        assertTrue(openedPlan.value)
    }

    @Test
    fun progressWeekMode_seededCards_haveTraversalOrder() {
        val fixture = createFixture("progress_week_seed_${System.currentTimeMillis()}")
        val seeds = fixture.mealPlanViewModel.seedDemoWeeks(fixture.userViewModel.userProfile.value)
        fixture.progressViewModel.seedDemoWeeks(seeds)
        fixture.progressViewModel.setProgressModePreference("Week")
        fixture.progressViewModel.setAdvancedWeekAnalyticsExpandedPreference(true)

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
                    onlineStateOverride = true
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("progress_content_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("progress_mode_week").performClick()
        composeRule.onNodeWithTag("progress_content_list")
            .performScrollToNode(hasTestTag("progress_week_spending_card"))
        composeRule.onNodeWithTag("progress_week_spending_card")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 6f))
        composeRule.onNodeWithTag("progress_content_list")
            .performScrollToNode(hasTestTag("progress_week_macro_card"))
        composeRule.onNodeWithTag("progress_week_macro_card")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 7f))
        composeRule.onNodeWithTag("progress_content_list")
            .performScrollToNode(hasTestTag("progress_next_plan_adjustment_card"))
        composeRule.onNodeWithTag("progress_next_plan_adjustment_card")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 8.5f))
    }

    private fun createFixture(userId: String): Fixture {
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
        val authViewModel = AuthViewModel(AuthRepository(context))
        userViewModel.loadProfileForUser(userId)
        userViewModel.updateProfileName("Traversal Tester")
        userViewModel.updatePersonalDetails(age = 29, weight = 64, height = 162, activity = "Lightly Active")
        userViewModel.updateGoal("Weight Loss")
        userViewModel.setProfileCompleted(true)
        bindMealPlanCurrentUserIdForTest(mealPlanViewModel, userId)
        bindGroceryCurrentUserIdForTest(groceryViewModel, userId)
        bindProgressCurrentUserIdForTest(progressViewModel, userId)

        return Fixture(
            userId = userId,
            authViewModel = authViewModel,
            userViewModel = userViewModel,
            mealPlanViewModel = mealPlanViewModel,
            groceryViewModel = groceryViewModel,
            progressViewModel = progressViewModel
        )
    }

    private data class Fixture(
        val userId: String,
        val authViewModel: AuthViewModel,
        val userViewModel: UserViewModel,
        val mealPlanViewModel: MealPlanViewModel,
        val groceryViewModel: GroceryViewModel,
        val progressViewModel: ProgressViewModel
    )
}
