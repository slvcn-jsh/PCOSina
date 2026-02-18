package com.pcosina.app

import com.pcosina.app.notifications.NotificationGoalPolicy
import com.pcosina.app.notifications.NotificationGoalTrack
import com.pcosina.app.ui.util.GoalOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class NotificationGoalPolicyTest {

    @Test
    fun fromGoal_mapsWeightLossGoals() {
        val track = NotificationGoalPolicy.fromGoal("Weight Loss")
        assertEquals(NotificationGoalTrack.WeightLoss, track)
    }

    @Test
    fun fromGoal_mapsSymptomGoals() {
        val track = NotificationGoalPolicy.fromGoal("PCOS Symptom Management")
        assertEquals(NotificationGoalTrack.SymptomSupport, track)
    }

    @Test
    fun fromGoal_alignsWithSupportedGoalOptions() {
        assertEquals(
            NotificationGoalTrack.WeightLoss,
            NotificationGoalPolicy.fromGoal(GoalOption.WeightLoss.apiValue)
        )
        assertEquals(
            NotificationGoalTrack.SymptomSupport,
            NotificationGoalPolicy.fromGoal(GoalOption.SymptomManagement.apiValue)
        )
        assertEquals(
            NotificationGoalTrack.GeneralHealth,
            NotificationGoalPolicy.fromGoal(GoalOption.GeneralHealth.apiValue)
        )
    }

    @Test
    fun fromGoal_requiresExplicitMappingForAllGoalOptions() {
        GoalOption.entries.forEach { option ->
            val track = NotificationGoalPolicy.fromGoal(option.apiValue)
            when (option) {
                GoalOption.WeightLoss -> assertEquals(NotificationGoalTrack.WeightLoss, track)
                GoalOption.SymptomManagement -> assertEquals(NotificationGoalTrack.SymptomSupport, track)
                GoalOption.GeneralHealth -> assertEquals(NotificationGoalTrack.GeneralHealth, track)
            }
        }
    }

    @Test
    fun streakInterval_isLighterForGeneralHealth() {
        val general = NotificationGoalPolicy.streakMinIntervalMs(NotificationGoalTrack.GeneralHealth)
        val weight = NotificationGoalPolicy.streakMinIntervalMs(NotificationGoalTrack.WeightLoss)
        assertTrue(general > weight)
        assertEquals(TimeUnit.DAYS.toMillis(3), general)
    }

    @Test
    fun messageCopy_isNonMedicalAndRoutineFocused() {
        val mealCopy = NotificationGoalPolicy.mealReminderBody(NotificationGoalTrack.SymptomSupport)
        val streakCopy = NotificationGoalPolicy.streakBody(NotificationGoalTrack.WeightLoss)
        assertTrue(mealCopy.contains("routine", ignoreCase = true))
        assertTrue(streakCopy.contains("consistency", ignoreCase = true))
    }
}
