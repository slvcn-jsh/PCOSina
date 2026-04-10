package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.pcosina.app.ui.screens.MealPlanScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealPlanNoSafePlanUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noSafePlan_withoutSavedWeek_showsDedicatedErrorState() {
        val fixture = createFixture("mealplan_no_safe_plan_error_${System.currentTimeMillis()}")
        fixture.mealPlanViewModel.showNoSafePlan(
            message = "Increase budget or cooking time and try again.",
            guidance = listOf(
                "Increase weekly budget slightly.",
                "Allow a longer cooking time."
            ),
            diagnosticsReference = "diag-001"
        )

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithText("No safe plan is available yet").assertIsDisplayed()
        composeRule.onNodeWithText("Increase budget or cooking time and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try adjusting:").assertIsDisplayed()
        composeRule.onNodeWithText("• Increase weekly budget slightly.").assertIsDisplayed()
        composeRule.onNodeWithText("Reference: diag-001").assertIsDisplayed()
    }

    @Test
    fun noSafePlan_withSavedWeek_keepsWarningCardAndSavedPlanVisible() {
        val fixture = createFixture(
            userId = "mealplan_no_safe_plan_continuity_${System.currentTimeMillis()}",
            seedWeeks = true
        )
        fixture.mealPlanViewModel.showNoSafePlan("Relax budget, cooking time, or variety and retry.")

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_no_safe_plan_card").assertIsDisplayed()
        composeRule.onNodeWithText("Your saved week is still available below.").assertIsDisplayed()
        composeRule.onNodeWithText("Plan range:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasTestTag("mealplan_generate_new_week_button"))
        composeRule.onNodeWithTag("mealplan_generate_new_week_button").assertIsDisplayed()
    }

    @Test
    fun offlineSavedWeek_keepsPlanVisibleAndDisablesGenerateNewWeek() {
        val fixture = createFixture(
            userId = "mealplan_offline_continuity_${System.currentTimeMillis()}",
            seedWeeks = true
        )

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onlineStateOverride = false
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasText("Offline mode: showing your last saved plan."))
        composeRule.onNodeWithText("Offline mode: showing your last saved plan.").assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasTestTag("mealplan_generate_new_week_button"))
        composeRule.onNodeWithTag("mealplan_generate_new_week_button").assertIsNotEnabled()
    }

    private fun createFixture(userId: String, seedWeeks: Boolean = false): Fixture {
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
        setMealPlanCurrentUserIdForTest(mealPlanViewModel, userId)
        setGroceryCurrentUserIdForTest(groceryViewModel, userId)
        setProgressCurrentUserIdForTest(progressViewModel, userId)
        if (seedWeeks) {
            mealPlanViewModel.seedDemoWeeks(userViewModel.userProfile.value)
        }
        return Fixture(
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

    private fun setProgressCurrentUserIdForTest(
        progressViewModel: ProgressViewModel,
        userId: String
    ) {
        val field = ProgressViewModel::class.java.getDeclaredField("currentUserId")
        field.isAccessible = true
        field.set(progressViewModel, userId)
    }

    private data class Fixture(
        val userViewModel: UserViewModel,
        val mealPlanViewModel: MealPlanViewModel,
        val groceryViewModel: GroceryViewModel,
        val progressViewModel: ProgressViewModel
    )
}
