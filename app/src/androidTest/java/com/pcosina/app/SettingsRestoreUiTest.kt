package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.pcosina.app.ui.screens.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRestoreUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accountTabShowsClearAccountActionsWithoutRestoreCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "settings_restore_${System.currentTimeMillis()}"
        val userPrefs = UserPreferencesRepository(context)
        val userViewModel = UserViewModel(userPrefs)
        val authViewModel = AuthViewModel(AuthRepository(context))
        val mealPlanViewModel = MealPlanViewModel(MealPlanRepository(), userPrefs)
        val groceryViewModel = GroceryViewModel(userPrefs)
        val progressViewModel = ProgressViewModel(
            userPrefsRepository = userPrefs,
            reflectionStore = ReflectionStore(context),
            feedbackRepository = FeedbackRepository(BuildConfig.BASE_URL)
        )

        userViewModel.loadProfileForUser(userId)

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    userViewModel = userViewModel,
                    authViewModel = authViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    groceryViewModel = groceryViewModel,
                    progressViewModel = progressViewModel,
                    userId = userId,
                    onBack = {},
                    onNavigateToProfileEdit = {}
                )
            }
        }

        composeRule.onNodeWithText("Account").performClick()
        composeRule.onNodeWithTag("settings_content_scroll")
            .performScrollToNode(hasText("Account actions"))
        composeRule.onNodeWithText("Account actions").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_content_scroll")
            .performScrollToNode(hasText("Clear saved week data"))
        composeRule.onNodeWithText("Clear saved week data").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_content_scroll")
            .performScrollToNode(hasText("Sign out on this phone"))
        composeRule.onNodeWithText("Sign out on this phone").assertIsDisplayed()
        composeRule.onAllNodesWithText("Storage and restore").assertCountEquals(0)
    }
}
