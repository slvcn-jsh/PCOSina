package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.pcosina.app.ui.screens.GroceryListScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroceryFeedbackSemanticsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun goToPlan_offline_showsInternetRequiredBanner() {
        val fixture = createFixture("grocery_offline_blocked", seedPlan = false)

        composeRule.setContent {
            MaterialTheme {
                GroceryListScreen(
                    groceryViewModel = fixture.groceryViewModel,
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onlineStateOverride = false,
                    onNavigateToRoute = {}
                )
            }
        }

        composeRule.onNodeWithText("Go to Plan").performClick()
        composeRule.onNodeWithText("Internet required for this action. Connect to open plan generation.")
            .assertIsDisplayed()
    }

    @Test
    fun expandCollapseAll_showsConfirmationBanner() {
        val fixture = createFixture("grocery_expand_feedback")
        fixture.groceryViewModel.addItems(
            listOf(
                DummyData.GroceryItem("Spinach", "2 bundles", 80, "Produce"),
                DummyData.GroceryItem("Eggs", "1 dozen", 110, "Eggs & Dairy"),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                GroceryListScreen(
                    groceryViewModel = fixture.groceryViewModel,
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onlineStateOverride = true,
                    onNavigateToRoute = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("grocery_content_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasTestTag("grocery_expand_toggle_all"))
        composeRule.onNodeWithTag("grocery_expand_toggle_all").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Collapsed all categories.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("Collapsed all categories."))
        composeRule.onNodeWithText("Collapsed all categories.").assertIsDisplayed()
    }

    @Test
    fun categoryToggle_exposesTalkBackLabel() {
        val fixture = createFixture("grocery_category_semantics")
        fixture.groceryViewModel.addItems(
            listOf(
                DummyData.GroceryItem("Spinach", "2 bundles", 80, "Produce"),
                DummyData.GroceryItem("Eggs", "1 dozen", 110, "Eggs & Dairy"),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                GroceryListScreen(
                    groceryViewModel = fixture.groceryViewModel,
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onlineStateOverride = true,
                    onNavigateToRoute = {}
                )
            }
        }

        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasContentDescription("Collapse Produce category"))
        composeRule.onNodeWithContentDescription("Collapse Produce category")
            .assertIsDisplayed()
    }

    @Test
    fun statusAndCategoryFilters_showResetPathWhenNoResults() {
        val fixture = createFixture("grocery_filter_flow")
        fixture.groceryViewModel.addItems(
            listOf(
                DummyData.GroceryItem("Spinach", "2 bundles", 80, "Produce"),
                DummyData.GroceryItem("Eggs", "1 dozen", 110, "Eggs & Dairy"),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                GroceryListScreen(
                    groceryViewModel = fixture.groceryViewModel,
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onlineStateOverride = true,
                    onNavigateToRoute = {}
                )
            }
        }

        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("Need to buy"))
        composeRule.onNodeWithText("Need to buy").performScrollTo().performClick()
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("Showing items you still need to buy."))
        composeRule.onNodeWithText("Showing items you still need to buy.").assertIsDisplayed()

        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("Produce (1)"))
        composeRule.onNodeWithText("Produce (1)").performScrollTo().performClick()
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("Filtered to Produce (1 items)."))
        composeRule.onNodeWithText("Filtered to Produce (1 items).").assertIsDisplayed()

        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("Bought / pantry"))
        composeRule.onNodeWithText("Bought / pantry").performScrollTo().performClick()
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("No items match this filter yet"))
        composeRule.onNodeWithText("No items match this filter yet").assertIsDisplayed()

        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("Reset Filters"))
        composeRule.onNodeWithText("Reset Filters").performScrollTo().performClick()
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("Filters reset. Showing all items."))
        composeRule.onNodeWithText("Filters reset. Showing all items.").assertIsDisplayed()
    }

    private fun createFixture(userId: String, seedPlan: Boolean = true): Fixture {
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
        if (seedPlan) {
            val seeds = mealPlanViewModel.seedDemoWeeks(userViewModel.userProfile.value)
            progressViewModel.seedDemoWeeks(seeds)
        }

        return Fixture(
            userViewModel = userViewModel,
            mealPlanViewModel = mealPlanViewModel,
            groceryViewModel = groceryViewModel,
            progressViewModel = progressViewModel
        )
    }

    private data class Fixture(
        val userViewModel: UserViewModel,
        val mealPlanViewModel: MealPlanViewModel,
        val groceryViewModel: GroceryViewModel,
        val progressViewModel: ProgressViewModel
    )
}
