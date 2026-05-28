package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.screens.GroceryListScreen
import com.pcosina.app.ui.screens.MealPlanScreen
import com.pcosina.app.ui.screens.ProgressScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrentCoreFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mealPlanGroceryProgress_currentScreensRenderAndNavigate() {
        val fixture = createFixture("current_core_flow_${System.currentTimeMillis()}")
        val screenState = mutableStateOf(CurrentScreen.MealPlan)

        composeRule.setContent {
            MaterialTheme {
                val screen = remember { screenState }
                LaunchedEffect(Unit) {
                    val seeds = fixture.mealPlanViewModel.seedDemoWeeks(fixture.userViewModel.userProfile.value)
                    fixture.progressViewModel.seedDemoWeeks(seeds)
                    fixture.groceryViewModel.addItems(
                        listOf(
                            DummyData.GroceryItem("Spinach", "2 bundles", 80, "Produce"),
                            DummyData.GroceryItem("Eggs", "1 dozen", 110, "Eggs & Dairy"),
                        )
                    )
                }

                when (screen.value) {
                    CurrentScreen.MealPlan -> MealPlanScreen(
                        userViewModel = fixture.userViewModel,
                        mealPlanViewModel = fixture.mealPlanViewModel,
                        groceryViewModel = fixture.groceryViewModel,
                        progressViewModel = fixture.progressViewModel,
                        onRecipeClick = { _, _ -> },
                        onViewProgress = { screen.value = CurrentScreen.Progress },
                        onNavigateToRoute = { route ->
                            when (route) {
                                Routes.GroceryList -> screen.value = CurrentScreen.Grocery
                                Routes.Progress -> screen.value = CurrentScreen.Progress
                            }
                        },
                        onlineStateOverride = true
                    )

                    CurrentScreen.Grocery -> GroceryListScreen(
                        groceryViewModel = fixture.groceryViewModel,
                        userViewModel = fixture.userViewModel,
                        mealPlanViewModel = fixture.mealPlanViewModel,
                        progressViewModel = fixture.progressViewModel,
                        onNavigateToRoute = { route ->
                            when (route) {
                                Routes.MealPlan -> screen.value = CurrentScreen.MealPlan
                                Routes.Progress -> screen.value = CurrentScreen.Progress
                            }
                        },
                        onlineStateOverride = true
                    )

                    CurrentScreen.Progress -> ProgressScreen(
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

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("mealplan_content_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mealplan_content_list").assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasTestTag("mealplan_next_best_action_card"))
        composeRule.onNodeWithTag("mealplan_next_best_action_cta").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("grocery_content_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("grocery_content_list").assertIsDisplayed()
        composeRule.runOnUiThread {
            screenState.value = CurrentScreen.Progress
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("progress_content_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("progress_header").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_today_hub_card").assertIsDisplayed()
        composeRule.runOnUiThread {
            fixture.progressViewModel.setAdvancedWeekAnalyticsExpandedPreference(true)
        }
        composeRule.onNodeWithTag("progress_content_list")
            .performScrollToNode(hasTestTag("progress_week_macro_card"))
        composeRule.onNodeWithTag("progress_week_macro_card").assertIsDisplayed()
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
        userViewModel.loadProfileForUser(userId)
        bindMealPlanCurrentUserIdForTest(mealPlanViewModel, userId)
        bindGroceryCurrentUserIdForTest(groceryViewModel, userId)
        bindProgressCurrentUserIdForTest(progressViewModel, userId)

        return Fixture(
            userId = userId,
            userViewModel = userViewModel,
            mealPlanViewModel = mealPlanViewModel,
            groceryViewModel = groceryViewModel,
            progressViewModel = progressViewModel
        )
    }

    private enum class CurrentScreen {
        MealPlan,
        Grocery,
        Progress,
    }

    private data class Fixture(
        val userId: String,
        val userViewModel: UserViewModel,
        val mealPlanViewModel: MealPlanViewModel,
        val groceryViewModel: GroceryViewModel,
        val progressViewModel: ProgressViewModel,
    )
}
