package com.pcosina.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.ProgressViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressLoggingPolicyInstrumentedTest {

    @Test
    fun loggingPolicy_onlyTodayIsLoggable() {
        val vm = createViewModel()
        val today = LocalDate.now()
        assertTrue(vm.isDateLoggable(today))
        assertFalse(vm.isDateLoggable(today.minusDays(1)))
        assertFalse(vm.isDateLoggable(today.plusDays(1)))
    }

    @Test
    fun toggleMeal_blocksPastAndFuture_allowsToday() {
        val vm = createViewModel()
        val today = LocalDate.now()
        val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        assertFalse(vm.toggleMeal(today.minusDays(1), "recipe_past", "Lunch"))
        assertFalse(vm.toggleMeal(today.plusDays(1), "recipe_future", "Dinner"))
        assertTrue(vm.dailyLogs.value.isEmpty())

        assertTrue(vm.toggleMeal(today, "recipe_today", "Lunch"))
        val completed = vm.dailyLogs.value[todayKey]?.completedMealIds.orEmpty()
        assertTrue(completed.any { ProgressViewModel.extractRecipeId(it) == "recipe_today" })
    }

    @Test
    fun markMealAsEaten_isTodayOnly() {
        val vm = createViewModel()
        val today = LocalDate.now()
        val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        assertFalse(vm.markMealAsEaten(today.plusDays(1), "recipe_future"))
        assertTrue(vm.dailyLogs.value.isEmpty())

        assertTrue(vm.markMealAsEaten(today, "recipe_today"))
        val completed = vm.dailyLogs.value[todayKey]?.completedMealIds.orEmpty()
        assertTrue(completed.any { ProgressViewModel.extractRecipeId(it) == "recipe_today" })
    }

    @Test
    fun markMealAsEaten_supportsRepeatedRecipeAcrossMealLabels() {
        val vm = createViewModel()
        val today = LocalDate.now()
        val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        assertTrue(vm.markMealAsEaten(today, "recipe_repeat", "Breakfast"))
        assertTrue(vm.markMealAsEaten(today, "recipe_repeat", "Dinner"))
        val completed = vm.dailyLogs.value[todayKey]?.completedMealIds.orEmpty()
        assertTrue(completed.contains(ProgressViewModel.buildMealKey("Breakfast", "recipe_repeat")))
        assertTrue(completed.contains(ProgressViewModel.buildMealKey("Dinner", "recipe_repeat")))
    }

    @Test
    fun setWeight_blocksPastAndFuture() {
        val vm = createViewModel()
        val today = LocalDate.now()
        val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        assertFalse(vm.setWeight(today.minusDays(1), 60f, "past"))
        assertFalse(vm.setWeight(today.plusDays(1), 61f, "future"))
        assertTrue(vm.dailyLogs.value.isEmpty())

        assertTrue(vm.setWeight(today, 62f, "today"))
        assertTrue(vm.dailyLogs.value[todayKey]?.weightKg == 62f)
    }

    @Test
    fun saveReflection_blocksPastAndFuture() {
        val vm = createViewModel()
        val today = LocalDate.now()
        val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        assertFalse(vm.saveReflection(today.minusDays(1), 3, 2, 4, listOf("Fatigue"), "past"))
        assertFalse(vm.saveReflection(today.plusDays(1), 3, 2, 4, listOf("Fatigue"), "future"))
        assertTrue(vm.dailyLogs.value.isEmpty())

        assertTrue(vm.saveReflection(today, 4, 2, 5, listOf("Fatigue"), "today"))
        assertTrue(vm.dailyLogs.value[todayKey]?.energyLevel == 4)
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
