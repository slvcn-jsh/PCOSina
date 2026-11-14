package com.pcosina.app

import com.pcosina.app.ui.util.profileConstraintConflictMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileConstraintSemanticsTest {

    @Test
    fun profileConstraintConflictMessage_returnsExpectedConflicts() {
        assertEquals(
            "Vegetarian and Pescatarian cannot both be active.",
            profileConstraintConflictMessage(
                vegetarian = true,
                pescatarian = true,
                planningPriority = "Balanced",
                varietyPreference = "Balanced",
                allergiesText = "",
                budgetPhp = null
            )
        )
        assertEquals(
            "Budget First requires a weekly budget.",
            profileConstraintConflictMessage(
                vegetarian = false,
                pescatarian = false,
                planningPriority = "Budget First",
                varietyPreference = "Balanced",
                allergiesText = "",
                budgetPhp = null
            )
        )
        assertEquals(
            "Variety First conflicts with Low variety preference.",
            profileConstraintConflictMessage(
                vegetarian = false,
                pescatarian = false,
                planningPriority = "Variety First",
                varietyPreference = "Low",
                allergiesText = "",
                budgetPhp = 1500
            )
        )
        assertEquals(
            "Pescatarian cannot be combined with both fish and shellfish allergies.",
            profileConstraintConflictMessage(
                vegetarian = false,
                pescatarian = true,
                planningPriority = "Balanced",
                varietyPreference = "Balanced",
                allergiesText = "bangus, hipon",
                budgetPhp = 1500
            )
        )
        assertEquals(
            "Pescatarian cannot be combined with both fish and shellfish allergies.",
            profileConstraintConflictMessage(
                vegetarian = false,
                pescatarian = true,
                planningPriority = "Balanced",
                varietyPreference = "Balanced",
                allergiesText = "tilapia; pusit",
                budgetPhp = 1500
            )
        )
        assertNull(
            profileConstraintConflictMessage(
                vegetarian = false,
                pescatarian = true,
                planningPriority = "Balanced",
                varietyPreference = "High",
                allergiesText = "fish",
                budgetPhp = 1500
            )
        )
    }

}
