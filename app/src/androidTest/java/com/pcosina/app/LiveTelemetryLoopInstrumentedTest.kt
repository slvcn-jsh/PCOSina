package com.pcosina.app

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTelemetryLoopInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun generateSwapAndLogAgainstLiveBackend() {
        val args = InstrumentationRegistry.getArguments()
        val loopCount = args.getString("pcosina.loopCount")?.toIntOrNull()?.coerceIn(1, 20) ?: 1
        val pauseMs = args.getString("pcosina.pauseMs")?.toLongOrNull()?.coerceAtLeast(0L) ?: 1_500L
        val loginEmail = args.getString("pcosina.liveEmail").orEmpty().trim()
        val loginPassword = args.getString("pcosina.livePassword").orEmpty()

        Log.i(TAG, "Starting live telemetry loop count=$loopCount baseUrl=${BuildConfig.BASE_URL}")

        repeat(loopCount) { index ->
            val humanIndex = index + 1
            Log.i(TAG, "Loop $humanIndex/$loopCount: begin")

            completeOnboardingIfNeeded(loginEmail = loginEmail, loginPassword = loginPassword)
            openPlanTab()
            generatePlanAndWait(loopIndex = humanIndex)
            swapFirstMeal(loopIndex = humanIndex)
            openProgressTab()
            logFirstUncheckedMeal(loopIndex = humanIndex)

            if (pauseMs > 0) {
                SystemClock.sleep(pauseMs)
            }
        }
    }

    private fun completeOnboardingIfNeeded(loginEmail: String, loginPassword: String) {
        waitForKnownEntryPoint()

        if (isOnLoginScreen()) {
            loginIfNeeded(loginEmail = loginEmail, loginPassword = loginPassword)
            waitForKnownEntryPoint()
        }

        if (exists(hasTestTag("profile_step1_name_input"))) {
            Log.i(TAG, "Completing profile setup")
            composeRule.onNodeWithTag("profile_step1_name_input").performTextReplacement("Live Telemetry")
            composeRule.onNodeWithTag("profile_step1_age_input").performTextReplacement("27")
            composeRule.onNodeWithTag("profile_step1_weight_input").performTextReplacement("64")
            composeRule.onNodeWithTag("profile_step1_height_cm_input").performTextReplacement("165")
            composeRule.onNodeWithTag("profile_next_step_cta").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("profile_next_step_cta").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("profile_complete_cta").performClick()
            composeRule.waitForIdle()
        }

        if (exists(hasTestTag("goal_option_weight_loss"))) {
            Log.i(TAG, "Completing goal selection")
            composeRule.onNodeWithTag("goal_option_weight_loss").performClick()
            composeRule.onNodeWithTag("goal_save_continue_cta").performClick()
            composeRule.waitForIdle()
        }
    }

    private fun loginIfNeeded(loginEmail: String, loginPassword: String) {
        if (!isOnLoginScreen()) {
            return
        }
        if (loginEmail.isBlank() || loginPassword.isBlank()) {
            error(
                "Live telemetry loop reached the login screen without credentials. " +
                    "Provide pcosina.liveEmail and pcosina.livePassword instrumentation args, " +
                    "or sign in once manually before rerunning."
            )
        }

        Log.i(TAG, "Signing in with supplied live credentials")
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextReplacement("")
        composeRule.onNodeWithTag("login_email_input", useUnmergedTree = true).performTextInput(loginEmail)
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextReplacement("")
        composeRule.onNodeWithTag("login_password_input", useUnmergedTree = true).performTextInput(loginPassword)
        composeRule.onNodeWithTag("login_primary_cta", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = LOGIN_TIMEOUT_MS) {
            !isOnLoginScreen() ||
                exists(hasTestTag("profile_step1_name_input")) ||
                exists(hasTestTag("goal_option_weight_loss")) ||
                exists(hasContentDescription("Plan")) ||
                exists(hasTestTag("mealplan_generate_new_week_button"))
        }
    }

    private fun openPlanTab() {
        if (exists(hasContentDescription("Plan"))) {
            composeRule.onNodeWithContentDescription("Plan").performClick()
        }
        composeRule.waitUntil(timeoutMillis = SHORT_WAIT_MS) {
            exists(hasTestTag("mealplan_generate_new_week_button")) || exists(hasTestTag("mealplan_content_list"))
        }
    }

    private fun generatePlanAndWait(loopIndex: Int) {
        Log.i(TAG, "Loop $loopIndex: generating plan")
        composeRule.onNodeWithTag("mealplan_generate_new_week_button").performClick()

        composeRule.waitUntil(timeoutMillis = SHORT_WAIT_MS) {
            exists(hasText("Generating…")) || exists(hasText("Generating plan…"))
        }

        composeRule.waitUntil(timeoutMillis = GENERATION_TIMEOUT_MS) {
            exists(hasText("Week ready")) || exists(hasText("Plan Ready")) || exists(hasText("Try again"))
        }

        if (exists(hasText("Try again"))) {
            error("Plan generation failed in live loop $loopIndex.")
        }

        composeRule.waitUntil(timeoutMillis = SHORT_WAIT_MS) {
            exists(hasTestTag("mealplan_swap_meal_button_0"))
        }
    }

    private fun swapFirstMeal(loopIndex: Int) {
        Log.i(TAG, "Loop $loopIndex: swapping first meal")
        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasTestTag("mealplan_swap_meal_button_0"))
        composeRule.onNodeWithTag("mealplan_swap_meal_button_0").performClick()

        composeRule.waitUntil(timeoutMillis = SHORT_WAIT_MS) {
            composeRule.onAllNodesWithText("Swap").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Swap")[0].performClick()

        composeRule.waitUntil(timeoutMillis = SHORT_WAIT_MS) {
            exists(hasText("Swapped ", substring = true))
        }
    }

    private fun openProgressTab() {
        if (exists(hasContentDescription("Progress"))) {
            composeRule.onNodeWithContentDescription("Progress").performClick()
        }
        composeRule.waitUntil(timeoutMillis = SHORT_WAIT_MS) {
            exists(hasTestTag("progress_content_list"))
        }
    }

    private fun logFirstUncheckedMeal(loopIndex: Int) {
        Log.i(TAG, "Loop $loopIndex: logging first unchecked meal")
        val checkboxMatcher = hasUncheckedProgressMealCheckbox()
        composeRule.onNodeWithTag("progress_content_list")
            .performScrollToNode(checkboxMatcher)
        composeRule.onNode(checkboxMatcher, useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = SHORT_WAIT_MS) {
            exists(hasText("Meal logged. Impact updated.")) ||
                exists(hasText("Open Next Meal")) ||
                exists(hasText("Open Progress"))
        }
    }

    private fun waitForKnownEntryPoint() {
        composeRule.waitUntil(timeoutMillis = ENTRY_WAIT_MS) {
            isOnLoginScreen() ||
                exists(hasTestTag("profile_step1_name_input")) ||
                exists(hasTestTag("goal_option_weight_loss")) ||
                exists(hasContentDescription("Plan")) ||
                exists(hasTestTag("mealplan_generate_new_week_button"))
        }
    }

    private fun isOnLoginScreen(): Boolean =
        exists(hasTestTag("login_primary_cta")) ||
            exists(hasTestTag("login_email_input")) ||
            exists(hasTestTag("login_password_input")) ||
            exists(hasTestTag("login_screen_content")) ||
            exists(hasTestTag("login_wellness_label"))

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasUncheckedProgressMealCheckbox(): SemanticsMatcher =
        SemanticsMatcher("unchecked progress meal checkbox") { node ->
            val tag = if (SemanticsProperties.TestTag in node.config) {
                node.config[SemanticsProperties.TestTag]
            } else {
                null
            }
            val toggleState = if (SemanticsProperties.ToggleableState in node.config) {
                node.config[SemanticsProperties.ToggleableState]
            } else {
                null
            }
            tag?.startsWith("progress_meal_checkbox_") == true && toggleState == ToggleableState.Off
        }

    companion object {
        private const val TAG = "LiveTelemetryLoop"
        private val SHORT_WAIT_MS = TimeUnit.SECONDS.toMillis(20)
        private val ENTRY_WAIT_MS = TimeUnit.SECONDS.toMillis(45)
        private val LOGIN_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2)
        private val GENERATION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(6)
    }
}
