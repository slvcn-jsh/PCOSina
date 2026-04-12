package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.screens.MealPlanNextActionAnalytics
import com.pcosina.app.ui.screens.MealPlanScreen
import com.pcosina.app.ui.util.MealPlanNextActionDebugLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealPlanSwapBannerUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swapMeal_offline_showsInternetRequiredBanner() {
        val fixture = createFixture("meal_plan_swap_offline_${System.currentTimeMillis()}")

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
            .performScrollToNode(hasTestTag("mealplan_swap_meal_button_0"))
        composeRule.onNodeWithTag("mealplan_swap_meal_button_0").performClick()
        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasText("Internet required for swap options. Connect and try again."))
        composeRule.onNodeWithText("Internet required for swap options. Connect and try again.")
            .assertIsDisplayed()
    }

    @Test
    fun swapMeal_onlineSuccessOverride_showsWhatChangedBanner() {
        val fixture = createFixture("meal_plan_swap_online_${System.currentTimeMillis()}")

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onlineStateOverride = true,
                    swapOptionsLoader = { _, _ ->
                        Result.success(
                            listOf(
                                RecipeSummaryDto(
                                    id = "swap_recipe_1",
                                    title = "Swap Test Bowl",
                                    mealType = "Breakfast",
                                    minutes = 15
                                )
                            )
                        )
                    },
                    swapGrocerySourceLoader = { _ ->
                        Result.success(listOf(GroceryItemSource("Spinach", "1 cup")))
                    },
                    swapApplyOverride = { _, _, _, _ -> Unit }
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasTestTag("mealplan_swap_meal_button_0"))
        composeRule.onNodeWithTag("mealplan_swap_meal_button_0").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Swap").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Swap")[0].performClick()
        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasText("Your grocery list was updated too.", substring = true))
        composeRule.onNodeWithText("Your grocery list was updated too.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun dayNavigation_weekendJumpAndWeekSwitch_keepDayPositionLabelStable() {
        val fixture = createFixture("meal_plan_day_nav_${System.currentTimeMillis()}")
        val todayIndex = when (LocalDate.now().dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        val todayLabel = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[todayIndex]
        val activePlanId = fixture.mealPlanViewModel.activePlanId.value
        val targetWeekLabel = fixture.mealPlanViewModel.planHistory.value
            .sortedByDescending { it.weekStart }
            .firstOrNull { it.id != activePlanId }
            ?.response
            ?.weekLabel
            ?: return

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

        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasTestTag("mealplan_day_position_label"))
        composeRule.onNodeWithTag("mealplan_day_position_label").assertIsDisplayed()
        composeRule.onNodeWithText("Mon").performScrollTo().performClick()
        composeRule.onNodeWithText("Jump to Weekend (Sat)").performScrollTo().performClick()
        composeRule.onNodeWithText("Day 6 of 7 • Sat selected").assertIsDisplayed()

        composeRule.onNodeWithTag("mealplan_content_list")
            .performScrollToNode(hasText(targetWeekLabel))
        composeRule.onNodeWithText(targetWeekLabel).performClick()
        composeRule.waitForIdle()
        val dayPosition = currentDayPositionText()
        assertTrue("Expected a non-empty day position label after week switch.", dayPosition.isNotBlank())
        assertTrue("Expected selected day state after week switch. label=$dayPosition", dayPosition.contains("selected"))
        assertTrue(
            "Expected a day-based label after week switch. label=$dayPosition",
            dayPosition.contains("Day ") || listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").any { dayPosition.contains(it) }
        )
    }

    @Test
    fun nextBestAction_whenGroceryMissing_showsSyncCta() {
        val fixture = createFixture("meal_plan_next_action_sync_${System.currentTimeMillis()}", seedProgressLogs = true)

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sync Grocery List").assertIsDisplayed()
    }

    @Test
    fun nextBestAction_tap_reportsTrackingSideEffectInDebugBuild() {
        val fixture = createFixture("meal_plan_next_action_tracking_${System.currentTimeMillis()}", seedProgressLogs = true)
        val fakeAnalytics = FakeMealPlanNextActionAnalytics()

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    nextActionAnalytics = fakeAnalytics,
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sync Grocery List").performClick()
        composeRule.runOnIdle {
            assertEquals("sync_grocery_list", fakeAnalytics.lastActionType)
            assertEquals("online", fakeAnalytics.lastNetworkState)
            assertEquals(fixture.userViewModel.activeUserId, fakeAnalytics.lastUserId)
        }
    }

    @Test
    fun nextBestAction_whenNeedsTracking_routesToProgress() {
        val fixture = createFixture("meal_plan_next_action_log_${System.currentTimeMillis()}", seedProgressLogs = false)
        fixture.groceryViewModel.addItems(
            listOf(DummyData.GroceryItem("Spinach", "2 bundles", 80, "Produce"))
        )

        composeRule.setContent {
            MaterialTheme {
                var route by remember { mutableStateOf("idle") }
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = { route = "progress" },
                    onlineStateOverride = true
                )
                if (route == "progress") {
                    Text("route:progress")
                }
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Log Meals in Progress").assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_next_best_action_cta").performClick()
        composeRule.onNodeWithText("route:progress").assertIsDisplayed()
    }

    @Test
    fun nextBestAction_whenTracked_routesToReviewInsights() {
        val fixture = createFixture("meal_plan_next_action_review_${System.currentTimeMillis()}", seedProgressLogs = true)
        fixture.groceryViewModel.addItems(
            listOf(DummyData.GroceryItem("Eggs", "1 dozen", 110, "Eggs & Dairy"))
        )

        composeRule.setContent {
            MaterialTheme {
                var route by remember { mutableStateOf("idle") }
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = { route = "progress" },
                    onlineStateOverride = true
                )
                if (route == "progress") {
                    Text("route:progress")
                }
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Review Progress Insights").assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_next_best_action_cta").performClick()
        composeRule.onNodeWithText("route:progress").assertIsDisplayed()
    }

    @Test
    fun nextBestAction_whenPlanExpired_showsGenerateNextWeek() {
        val fixture = createFixture("meal_plan_next_action_expired_${System.currentTimeMillis()}", seedProgressLogs = true)
        val expiredId = fixture.mealPlanViewModel.planHistory.value.minByOrNull { it.weekStart }?.id ?: return
        fixture.mealPlanViewModel.selectPlan(expiredId)

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Generate Next Week").assertIsDisplayed()
    }

    @Test
    fun nextBestAction_priorityExpiredWinsWhenMultipleConditionsOverlap() {
        val fixture = createFixture("meal_plan_next_action_priority_overlap_${System.currentTimeMillis()}", seedProgressLogs = false)
        val expiredId = fixture.mealPlanViewModel.planHistory.value.minByOrNull { it.weekStart }?.id ?: return
        fixture.mealPlanViewModel.selectPlan(expiredId)

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Generate Next Week").assertIsDisplayed()
        composeRule.onAllNodesWithText("Sync Grocery List").assertCountEquals(0)
        composeRule.onAllNodesWithText("Log Meals in Progress").assertCountEquals(0)
    }

    @Test
    fun nextBestAction_offlineSyncBranch_showsInternetRequiredLabelAndDisabledCta() {
        val fixture = createFixture("meal_plan_next_action_sync_offline_${System.currentTimeMillis()}", seedProgressLogs = true)

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    onlineStateOverride = false
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sync Grocery List (Internet required)")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun nextBestAction_offlineExpiredBranch_showsInternetRequiredLabelAndDisabledCta() {
        val fixture = createFixture("meal_plan_next_action_expired_offline_${System.currentTimeMillis()}", seedProgressLogs = true)
        val expiredId = fixture.mealPlanViewModel.planHistory.value.minByOrNull { it.weekStart }?.id ?: return
        fixture.mealPlanViewModel.selectPlan(expiredId)

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    onlineStateOverride = false
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Generate Next Week (Internet required)")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun statusCenter_hidesReminderSummaryWhenNotificationsDisabled() {
        val fixture = createFixture("meal_plan_status_center_notification_off_${System.currentTimeMillis()}", seedProgressLogs = true)
        fixture.userViewModel.updateNotificationPreferences { prefs ->
            prefs.copy(masterEnabled = false, mealRemindersEnabled = false)
        }

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Next reminders:", substring = true).assertCountEquals(0)
    }

    @Test
    fun statusCenter_showsReminderSummaryWhenNotificationsEnabled() {
        val fixture = createFixture("meal_plan_status_center_notification_on_${System.currentTimeMillis()}", seedProgressLogs = true)
        fixture.userViewModel.updateNotificationPreferences { prefs ->
            prefs.copy(masterEnabled = true, mealRemindersEnabled = true)
        }

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Next reminders:", substring = true).assertIsDisplayed()
    }

    @Test
    fun nextBestAction_tracksPayload_generateBranch() {
        val fixture = createFixture("meal_plan_tracking_generate_${System.currentTimeMillis()}", seedProgressLogs = true)
        val expiredId = fixture.mealPlanViewModel.planHistory.value.minByOrNull { it.weekStart }?.id ?: return
        fixture.mealPlanViewModel.selectPlan(expiredId)

        assertTrackedPayloadForBranch(
            fixture = fixture,
            expectedButtonLabel = "Generate Next Week",
            expectedActionType = "generate_next_week"
        )
    }

    @Test
    fun nextBestAction_tracksPayload_syncBranch() {
        val fixture = createFixture("meal_plan_tracking_sync_${System.currentTimeMillis()}", seedProgressLogs = true)
        assertTrackedPayloadForBranch(
            fixture = fixture,
            expectedButtonLabel = "Sync Grocery List",
            expectedActionType = "sync_grocery_list"
        )
    }

    @Test
    fun nextBestAction_tracksPayload_logBranch() {
        val fixture = createFixture("meal_plan_tracking_log_${System.currentTimeMillis()}", seedProgressLogs = false)
        fixture.groceryViewModel.addItems(
            listOf(DummyData.GroceryItem("Cucumber", "1", 20, "Produce"))
        )
        assertTrackedPayloadForBranch(
            fixture = fixture,
            expectedButtonLabel = "Log Meals in Progress",
            expectedActionType = "log_meals_in_progress"
        )
    }

    @Test
    fun nextBestAction_tracksPayload_reviewBranch() {
        val fixture = createFixture("meal_plan_tracking_review_${System.currentTimeMillis()}", seedProgressLogs = true)
        fixture.groceryViewModel.addItems(
            listOf(DummyData.GroceryItem("Salmon", "2 fillets", 210, "Protein"))
        )
        assertTrackedPayloadForBranch(
            fixture = fixture,
            expectedButtonLabel = "Review Progress Insights",
            expectedActionType = "review_progress_insights"
        )
    }

    @Test
    fun nextBestAction_debugLogArtifact_recordsTapEvent() {
        val fixture = createFixture("meal_plan_tracking_debug_log_${System.currentTimeMillis()}", seedProgressLogs = true)
        MealPlanNextActionDebugLog.clear()

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_next_best_action_cta").performClick()
        composeRule.runOnIdle {
            val lines = MealPlanNextActionDebugLog.snapshot()
            assertTrue(
                "Expected debug tap log for sync branch.",
                lines.any { it.contains("action=sync_grocery_list") && it.contains("network=online") }
            )
        }
    }

    private fun assertTrackedPayloadForBranch(
        fixture: Fixture,
        expectedButtonLabel: String,
        expectedActionType: String
    ) {
        val fakeAnalytics = FakeMealPlanNextActionAnalytics()

        composeRule.setContent {
            MaterialTheme {
                MealPlanScreen(
                    userViewModel = fixture.userViewModel,
                    mealPlanViewModel = fixture.mealPlanViewModel,
                    groceryViewModel = fixture.groceryViewModel,
                    progressViewModel = fixture.progressViewModel,
                    onRecipeClick = { _, _ -> },
                    onNavigateToRoute = {},
                    onViewProgress = {},
                    nextActionAnalytics = fakeAnalytics,
                    onlineStateOverride = true
                )
            }
        }

        composeRule.onNodeWithTag("mealplan_next_best_action_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(expectedButtonLabel).assertIsDisplayed()
        composeRule.onNodeWithTag("mealplan_next_best_action_cta").performClick()
        composeRule.runOnIdle {
            assertEquals(expectedActionType, fakeAnalytics.lastActionType)
            assertEquals("online", fakeAnalytics.lastNetworkState)
            assertEquals(fixture.userViewModel.activeUserId, fakeAnalytics.lastUserId)
        }
    }

    private fun createFixture(userId: String, seedProgressLogs: Boolean = true): Fixture {
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
        val seeds = mealPlanViewModel.seedDemoWeeks(userViewModel.userProfile.value)
        if (seedProgressLogs) {
            progressViewModel.seedDemoWeeks(seeds)
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

    private fun currentDayPositionText(): String {
        return runCatching {
            composeRule.onAllNodesWithTag("mealplan_day_position_label")
                .fetchSemanticsNodes()
                .first()
                .config[SemanticsProperties.Text]
                .joinToString(separator = " ") { textRange -> textRange.text }
        }.getOrElse { "" }
    }

    private data class Fixture(
        val userViewModel: UserViewModel,
        val mealPlanViewModel: MealPlanViewModel,
        val groceryViewModel: GroceryViewModel,
        val progressViewModel: ProgressViewModel
    )

    private class FakeMealPlanNextActionAnalytics : MealPlanNextActionAnalytics {
        var lastActionType: String? = null
        var lastNetworkState: String? = null
        var lastUserId: String? = null

        override fun trackTap(actionType: String, networkState: String, userId: String) {
            lastActionType = actionType
            lastNetworkState = networkState
            lastUserId = userId
        }
    }
}
