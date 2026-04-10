package com.pcosina.app

import android.os.SystemClock
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.model.DummyData
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
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.screens.GoalSelectionScreen
import com.pcosina.app.ui.screens.GroceryListScreen
import com.pcosina.app.ui.screens.LoginScreen
import com.pcosina.app.ui.screens.MealPlanNextActionAnalytics
import com.pcosina.app.ui.screens.MealPlanScreen
import com.pcosina.app.ui.screens.ProgressScreen
import com.pcosina.app.ui.screens.UserProfileScreen
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstWinFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstWinFlow_loginToProgress_completesWithinNinetySeconds() {
        val fixture = createFixture("first_win_e2e_${System.currentTimeMillis()}")
        val startMs = SystemClock.elapsedRealtime()
        val timeoutMs = firstWinTimeoutMs()

        composeRule.setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(FirstWinScreen.Login) }
                var goalCompletionPending by remember { mutableStateOf(false) }
                var nextActionType by remember { mutableStateOf<String?>(null) }
                val groceryItems by fixture.groceryViewModel.groceryItems.collectAsState()

                LaunchedEffect(screen, groceryItems.size, nextActionType) {
                    if (screen == FirstWinScreen.MealPlan && groceryItems.isNotEmpty()) {
                        screen = FirstWinScreen.Grocery
                    } else if (screen == FirstWinScreen.MealPlan && nextActionType == "sync_grocery_list") {
                        if (groceryItems.isEmpty()) {
                            fixture.groceryViewModel.addItems(
                                listOf(
                                    DummyData.GroceryItem(
                                        name = "Spinach",
                                        quantity = "1 bunch",
                                        price = 60,
                                        category = "Produce"
                                    )
                                )
                            )
                        }
                        screen = FirstWinScreen.Grocery
                    }
                }
                LaunchedEffect(goalCompletionPending) {
                    if (goalCompletionPending) {
                        val seeds = fixture.mealPlanViewModel.seedDemoWeeks(fixture.userViewModel.userProfile.value)
                        fixture.progressViewModel.seedDemoWeeks(seeds)
                        screen = FirstWinScreen.MealPlan
                        goalCompletionPending = false
                    }
                }

                when (screen) {
                    FirstWinScreen.Login -> LoginScreen(
                        authViewModel = fixture.authViewModel,
                        onLoginSuccess = { screen = FirstWinScreen.Profile },
                        onNavigateToSignUp = {},
                        onDebugFirstWinContinue = { screen = FirstWinScreen.Profile }
                    )
                    FirstWinScreen.Profile -> UserProfileScreen(
                        userViewModel = fixture.userViewModel,
                        onNext = { screen = FirstWinScreen.Goal },
                        isEditMode = false
                    )
                    FirstWinScreen.Goal -> GoalSelectionScreen(
                        userViewModel = fixture.userViewModel,
                        onFinish = {
                            goalCompletionPending = true
                        }
                    )
                    FirstWinScreen.MealPlan -> MealPlanScreen(
                        userViewModel = fixture.userViewModel,
                        mealPlanViewModel = fixture.mealPlanViewModel,
                        groceryViewModel = fixture.groceryViewModel,
                        progressViewModel = fixture.progressViewModel,
                        onRecipeClick = { _, _ -> },
                        onViewProgress = { screen = FirstWinScreen.Progress },
                        onNavigateToRoute = { route ->
                            when (route) {
                                Routes.GroceryList -> screen = FirstWinScreen.Grocery
                                Routes.Progress -> screen = FirstWinScreen.Progress
                            }
                        },
                        nextActionAnalytics = object : MealPlanNextActionAnalytics {
                            override fun trackTap(actionType: String, networkState: String, userId: String) {
                                nextActionType = actionType
                            }
                        },
                        onlineStateOverride = true
                    )
                    FirstWinScreen.Grocery -> GroceryListScreen(
                        groceryViewModel = fixture.groceryViewModel,
                        userViewModel = fixture.userViewModel,
                        mealPlanViewModel = fixture.mealPlanViewModel,
                        progressViewModel = fixture.progressViewModel,
                        onNavigateToRoute = { route ->
                            when (route) {
                                Routes.Progress -> screen = FirstWinScreen.Progress
                                Routes.MealPlan -> screen = FirstWinScreen.MealPlan
                            }
                        }
                    )
                    FirstWinScreen.Progress -> ProgressScreen(
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

        composeRule.onNodeWithTag("login_first_win_card").assertIsDisplayed()
        composeRule.onNodeWithTag("login_step1_label").assertIsDisplayed()
        composeRule.onNodeWithTag("login_debug_continue_first_win").performClick()

        composeRule.onNodeWithTag("profile_first_win_card").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_step1_name_input").performTextInput("First Win User")
        composeRule.onNodeWithTag("profile_step1_age_input").performTextInput("27")
        composeRule.onNodeWithTag("profile_step1_weight_input").performTextInput("64")
        composeRule.onNodeWithTag("profile_step1_height_cm_input").performTextInput("165")
        composeRule.onNodeWithTag("profile_next_step_cta").performClick()
        composeRule.onNodeWithTag("profile_next_step_cta").performClick()
        composeRule.onNodeWithTag("profile_complete_cta").performClick()

        composeRule.onNodeWithTag("goal_step2_label").assertIsDisplayed()
        composeRule.onNodeWithTag("goal_why_card").assertIsDisplayed()
        composeRule.onNodeWithTag("goal_option_weight_loss").performClick()
        composeRule.onNodeWithTag("goal_save_continue_cta").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag("mealplan_step3_label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mealplan_content_list").assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_step3_label").assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_top_section_capture").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasTestTag("mealplan_day_position_label"))
        composeRule.onNodeWithTag("mealplan_day_position_label").assertIsDisplayed()
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
            assertTrue(
                "Expected day label $label in meal plan navigation.",
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            )
        }
        assertTrue(
            "Day position label should expose 7-day context.",
            currentDayPositionText().contains("of 7")
        )
        val hasWeekendJump = composeRule.onAllNodesWithText("Jump to Weekend (Sat)")
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (hasWeekendJump) {
            composeRule.onNodeWithText("Jump to Weekend (Sat)").performScrollTo().performClick()
            val weekendPosition = currentDayPositionText()
            assertTrue(
                "Day position should remain in 7-day range after weekend jump. label=$weekendPosition",
                weekendPosition.contains("of 7")
            )
            composeRule.onNodeWithContentDescription("Next day").performClick()
            val nextPosition = currentDayPositionText()
            assertTrue(
                "Next-day navigation should remain in 7-day range. label=$nextPosition",
                nextPosition.contains("of 7")
            )
            assertTrue(
                "Day navigation should move or already be at week end. before=$weekendPosition after=$nextPosition",
                nextPosition != weekendPosition || weekendPosition.contains("Day 7 of 7")
            )
            composeRule.onNodeWithContentDescription("Previous day").performClick()
            val previousPosition = currentDayPositionText()
            assertTrue(
                "Previous-day navigation should remain in 7-day range. label=$previousPosition",
                previousPosition.contains("of 7")
            )
        } else {
            val current = currentDayPositionText()
            assertTrue(
                "If weekend jump is hidden, selected day should already be Sat/Sun. label=$current",
                current.contains("Day 6 of 7") || current.contains("Day 7 of 7")
            )
        }
        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasTestTag("mealplan_next_best_action_card"))
        composeRule.onNodeWithTag("mealplan_next_best_action_card").assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_next_best_action_reason")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_next_best_action_cta").performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("grocery_next_steps_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("grocery_next_steps_card").assertIsDisplayed()
        composeRule.onNodeWithText("Categories").assertIsDisplayed()
        composeRule.onNodeWithTag("grocery_expand_toggle_all").assertIsDisplayed()
        composeRule.onNodeWithTag("grocery_open_mealplan_cta").assertIsDisplayed()
        composeRule.onNodeWithTag("grocery_open_progress_cta").assertIsDisplayed()
        composeRule.onNodeWithTag("grocery_open_progress_cta").performClick()

        composeRule.onNodeWithTag("progress_step4_card").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_focus_mode_card").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_today_hub_card").assertIsDisplayed()
        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        Log.i("FirstWinFlowUiTest", "first_win_elapsed_ms=$elapsedMs timeout_ms=$timeoutMs")
        assertTrue(
            "First-win flow should complete within ${timeoutMs}ms (elapsed=${elapsedMs}ms).",
            elapsedMs <= timeoutMs
        )
    }

    private fun createFixture(userId: String): FirstWinFixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userPrefs = UserPreferencesRepository(context)
        val userViewModel = UserViewModel(userPrefs)
        val mealPlanViewModel = MealPlanViewModel(MealPlanRepository(), userPrefs)
        val groceryViewModel = GroceryViewModel(userPrefs)
        val fastFailClient = OkHttpClient.Builder()
            .callTimeout(300, TimeUnit.MILLISECONDS)
            .connectTimeout(300, TimeUnit.MILLISECONDS)
            .readTimeout(300, TimeUnit.MILLISECONDS)
            .build()
        val progressViewModel = ProgressViewModel(
            userPrefsRepository = userPrefs,
            reflectionStore = ReflectionStore(context),
            feedbackRepository = FeedbackRepository(
                baseUrl = "http://127.0.0.1:9/",
                client = fastFailClient
            )
        )
        val authViewModel = AuthViewModel(AuthRepository(context))
        userViewModel.loadProfileForUser(userId)
        setMealPlanCurrentUserIdForTest(mealPlanViewModel, userId)
        setGroceryCurrentUserIdForTest(groceryViewModel, userId)
        bindProgressCurrentUserIdForTest(progressViewModel, userId)

        return FirstWinFixture(
            userId = userId,
            authViewModel = authViewModel,
            userViewModel = userViewModel,
            mealPlanViewModel = mealPlanViewModel,
            groceryViewModel = groceryViewModel,
            progressViewModel = progressViewModel
        )
    }

    private fun setMealPlanCurrentUserIdForTest(
        mealPlanViewModel: MealPlanViewModel,
        userId: String
    ) {
        val field = MealPlanViewModel::class.java.getDeclaredField("currentUserId")
        field.isAccessible = true
        field.set(mealPlanViewModel, userId)
    }

    private fun setGroceryCurrentUserIdForTest(
        groceryViewModel: GroceryViewModel,
        userId: String
    ) {
        val field = GroceryViewModel::class.java.getDeclaredField("currentUserId")
        field.isAccessible = true
        field.set(groceryViewModel, userId)
    }

    private fun currentDayPositionText(): String {
        return runCatching {
            composeRule.onAllNodesWithTag("mealplan_day_position_label")
                .fetchSemanticsNodes()
                .first()
                .config[SemanticsProperties.Text]
                .joinToString(separator = " ") { it.text }
        }.getOrElse { "" }
    }

    private fun firstWinTimeoutMs(): Long {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("first_win_timeout_ms")
            ?.toLongOrNull()
            ?.coerceAtLeast(30_000L)
            ?: 90_000L
    }
}

private enum class FirstWinScreen {
    Login,
    Profile,
    Goal,
    MealPlan,
    Grocery,
    Progress
}

private data class FirstWinFixture(
    val userId: String,
    val authViewModel: AuthViewModel,
    val userViewModel: UserViewModel,
    val mealPlanViewModel: MealPlanViewModel,
    val groceryViewModel: GroceryViewModel,
    val progressViewModel: ProgressViewModel
)
