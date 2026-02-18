package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardPrimaryCard_hasTraversalOrder_andPrimaryCtaShowsBanner() {
        val fixture = createFixture("dashboard_traversal_banner_${System.currentTimeMillis()}")

        composeRule.setContent {
            MaterialTheme {
                DashboardScreen(
                    userViewModel = fixture.userViewModel,
                    authViewModel = fixture.authViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onViewPlan = {},
                    onNavigateToRoute = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_primary_next_card")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
        composeRule.onNodeWithText("Generate My Plan").performClick()
        composeRule.onNodeWithText("Opening plan generator…").assertIsDisplayed()
    }

    @Test
    fun progressFocusCard_hasTraversalOrder_andPrimaryCtaShowsBanner() {
        val fixture = createFixture("progress_traversal_banner_${System.currentTimeMillis()}")

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

        composeRule.onNodeWithTag("progress_focus_mode_card")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
        composeRule.onNodeWithText("Generate My First Plan").performScrollTo().performClick()
        composeRule.onNodeWithText("Opening plan generator…").assertIsDisplayed()
    }

    @Test
    fun progressStepFourCard_visibleWithTraversalTag() {
        val fixture = createFixture("progress_step4_banner_${System.currentTimeMillis()}")

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

        composeRule.onNodeWithTag("progress_step4_card")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0.8f))
        composeRule.onNodeWithTag("progress_focus_mode_card")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
        val stepTraversal = composeRule.onNodeWithTag("progress_step4_card")
            .fetchSemanticsNode()
            .config[SemanticsProperties.TraversalIndex]
        val focusTraversal = composeRule.onNodeWithTag("progress_focus_mode_card")
            .fetchSemanticsNode()
            .config[SemanticsProperties.TraversalIndex]
        assertTrue(
            "TalkBack should read Step 4 card before Focus Mode card.",
            stepTraversal < focusTraversal
        )
        composeRule.onNodeWithText("Step 4 of 4: Track Progress")
            .assertIsDisplayed()
    }

    @Test
    fun progressWeekMode_seededCards_haveTraversalOrder() {
        val fixture = createFixture("progress_week_seed_${System.currentTimeMillis()}")
        val seeds = fixture.mealPlanViewModel.seedDemoWeeks(fixture.userViewModel.userProfile.value)
        fixture.progressViewModel.seedDemoWeeks(seeds)

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

        composeRule.onNodeWithTag("progress_mode_week").performClick()
        composeRule.onNodeWithTag("progress_week_insights_card")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 5f))
        composeRule.onNodeWithTag("progress_week_spending_card")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 6f))
        composeRule.onNodeWithText("Advanced Week Analytics")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("progress_week_macro_card")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 7f))
        composeRule.onNodeWithTag("progress_plan_feedback_card")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 8f))
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
        val weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        userViewModel.loadProfileForUser(userId)
        mealPlanViewModel.loadSavedPlan(userId)
        groceryViewModel.loadGroceryForUser(userId)
        progressViewModel.loadForUser(userId, weekStart)

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
