package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    fun topCategoriesShortcutCard_isRemovedFromGroceryScreen() {
        val fixture = createFixture("grocery_removed_top_categories", seedPlan = false)

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
        waitForGroceryContent()

        composeRule.onAllNodesWithTag("grocery_next_steps_card").assertCountEquals(0)
        composeRule.onAllNodesWithTag("grocery_expand_toggle_all").assertCountEquals(0)
        composeRule.onAllNodesWithText("Go to Plan").assertCountEquals(0)
    }

    @Test
    fun groceryBudgetAppearsBeforeKitchenHubWithoutTopCategoriesCard() {
        val fixture = createFixture("grocery_budget_first")
        fixture.groceryViewModel.addItems(
            listOf(
                DummyData.GroceryItem("Spinach", "2 bundles", 80, "Produce"),
                DummyData.GroceryItem("Tomato", "2 pcs", 30, "Produce"),
                DummyData.GroceryItem("Onion", "1 pc", 20, "Produce"),
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
            .performScrollToNode(hasTestTag("grocery_budget_card"))
        composeRule.onNodeWithTag("grocery_budget_card").assertIsDisplayed()
        composeRule.onAllNodesWithTag("grocery_next_steps_card").assertCountEquals(0)
    }

    @Test
    fun categoryToggle_exposesTalkBackLabel() {
        val fixture = createFixture("grocery_category_semantics", seedPlan = false)
        fixture.groceryViewModel.addItems(
            listOf(
                DummyData.GroceryItem("Spinach", "2 bundles", 80, "Produce"),
                DummyData.GroceryItem("Tomato", "2 pcs", 30, "Produce"),
                DummyData.GroceryItem("Onion", "1 pc", 20, "Produce"),
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

        waitForGroceryContent()
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasContentDescription("Collapse Produce category"))
        composeRule.onAllNodesWithContentDescription("Collapse Produce category")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun statusAndCategoryFilters_showResetPathWhenNoResults() {
        val fixture = createFixture("grocery_filter_flow", seedPlan = false)
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

        waitForGroceryContent()
        scrollToGroceryText("Spinach")
        openFilters()
        clickFilterDialogAction("Need to buy")
        dismissFilters()
        scrollToGroceryText("Spinach")
        composeRule.onNodeWithText("Spinach").assertIsDisplayed()

        openFilters()
        clickFilterDialogAction("Produce")
        dismissFilters()
        scrollToGroceryText("Spinach")
        composeRule.onNodeWithText("Spinach").assertIsDisplayed()

        openFilters()
        clickFilterDialogAction("Bought or in pantry")
        dismissFilters()
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasText("No ingredients match your current filters."))
        composeRule.onNodeWithText("No ingredients match your current filters.").assertIsDisplayed()

        openFilters()
        clickFilterDialogAction("Clear All")
        dismissFilters()
        val visibleResetItem = scrollToAnyGroceryText("Spinach", "Eggs")
        composeRule.onNodeWithText(visibleResetItem).assertIsDisplayed()
    }

    private fun waitForGroceryContent() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("grocery_content_list").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    private fun openFilters() {
        composeRule.onNodeWithTag("grocery_content_list")
            .performScrollToNode(hasContentDescription("Open grocery filters"))
        composeRule.onNodeWithContentDescription("Open grocery filters").performClick()
        composeRule.onNodeWithText("Select Filters").assertIsDisplayed()
    }

    private fun clickFilterDialogAction(text: String) {
        composeRule.onNode(hasText(text) and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    private fun dismissFilters() {
        composeRule.onNodeWithTag("grocery_filter_done").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithText("Select Filters").fetchSemanticsNodes().isEmpty()
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    private fun scrollToGroceryText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("grocery_content_list")
                    .performScrollToNode(hasText(text))
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    private fun scrollToAnyGroceryText(vararg texts: String): String {
        var visibleText = ""
        composeRule.waitUntil(timeoutMillis = 10_000) {
            texts.any { text ->
                runCatching {
                    composeRule.onNodeWithTag("grocery_content_list")
                        .performScrollToNode(hasText(text))
                    visibleText = text
                    true
                }.getOrDefault(false)
            }
        }
        composeRule.waitForIdle()
        return visibleText
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
