package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.screens.NotificationScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationReminderUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notificationScreen_emptyHistoryExplainsDeliveryAndSchedulingGates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userViewModel = UserViewModel(UserPreferencesRepository(context))
        val userId = "notification_ui_${System.currentTimeMillis()}"

        userViewModel.loadProfileForUser(userId)
        userViewModel.updateNotificationPreferences { prefs ->
            prefs.copy(masterEnabled = true, mealRemindersEnabled = true)
        }

        composeRule.setContent {
            MaterialTheme {
                NotificationScreen(
                    userViewModel = userViewModel,
                    userId = userId,
                    onBack = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithTag("notification_content_list")
            .performScrollToNode(hasText("Phone notifications"))
        composeRule.onNodeWithText("Phone notifications").assertIsDisplayed()

        composeRule.onNodeWithTag("notification_content_list")
            .performScrollToNode(hasText("Next scheduled"))
        composeRule.onNodeWithText("Next scheduled").assertIsDisplayed()

        composeRule.onNodeWithTag("notification_content_list")
            .performScrollToNode(hasText("No notifications delivered yet", substring = true))
        composeRule.onNodeWithText("No notifications delivered yet", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("log starts only after Android posts", substring = true)
            .assertIsDisplayed()
    }
}
