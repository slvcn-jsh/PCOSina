package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.screens.ProgressMealCheckbox
import com.pcosina.app.ui.screens.ProgressLoggingPolicyDialog
import com.pcosina.app.ui.screens.ProgressLoggingPolicyLearnMoreChip
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressDateLockUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun futureDateCheckbox_isDisabled() {
        val vm = createViewModel()
        val today = LocalDate.now()
        val selected = today.plusDays(1)

        composeRule.setContent {
            val checked = remember { mutableStateOf(false) }
            MaterialTheme {
                ProgressMealCheckbox(
                    checked = checked.value,
                    loggable = vm.isDateLoggable(selected, today),
                    testTag = "date_lock_checkbox",
                    onCheckedChange = { checked.value = it }
                )
            }
        }

        composeRule.onNodeWithTag("date_lock_checkbox").assertIsNotEnabled()
    }

    @Test
    fun todayDateCheckbox_isEnabled() {
        val vm = createViewModel()
        val today = LocalDate.now()

        composeRule.setContent {
            val checked = remember { mutableStateOf(false) }
            MaterialTheme {
                ProgressMealCheckbox(
                    checked = checked.value,
                    loggable = vm.isDateLoggable(today, today),
                    testTag = "date_lock_checkbox",
                    onCheckedChange = { checked.value = it }
                )
            }
        }

        composeRule.onNodeWithTag("date_lock_checkbox").assertIsEnabled()
    }

    @Test
    fun readOnlyDateLearnMore_showsLoggingPolicyDialog() {
        val vm = createViewModel()
        val today = LocalDate.now()
        val selected = today.plusDays(1)

        composeRule.setContent {
            val checked = remember { mutableStateOf(false) }
            var showPolicyDialog by remember { mutableStateOf(false) }
            val loggable = vm.isDateLoggable(selected, today)
            MaterialTheme {
                Column {
                    ProgressMealCheckbox(
                        checked = checked.value,
                        loggable = loggable,
                        testTag = "date_lock_checkbox",
                        onCheckedChange = { checked.value = it }
                    )
                    if (!loggable) {
                        ProgressLoggingPolicyLearnMoreChip(
                            onClick = { showPolicyDialog = true }
                        )
                    }
                    if (showPolicyDialog) {
                        ProgressLoggingPolicyDialog(
                            lockReason = vm.loggingLockReason(selected, today),
                            onDismiss = { showPolicyDialog = false }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("date_lock_checkbox").assertIsNotEnabled()
        composeRule.onNodeWithTag("progress_logging_policy_learn_more").performClick()
        composeRule.onNodeWithText("Logging policy").assertIsDisplayed()
        composeRule.onNodeWithText(ProgressViewModel.LoggingPolicySummary).assertIsDisplayed()
        composeRule.onNodeWithText("Future-day logging is locked. You can only log meals for today.").assertIsDisplayed()
    }

    private fun createViewModel(): ProgressViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ProgressViewModel(
            userPrefsRepository = UserPreferencesRepository(context),
            reflectionStore = ReflectionStore(context),
            feedbackRepository = FeedbackRepository(BuildConfig.BASE_URL)
        )
    }
}
