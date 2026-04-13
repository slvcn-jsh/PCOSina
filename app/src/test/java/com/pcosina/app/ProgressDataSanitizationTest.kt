package com.pcosina.app

import com.pcosina.app.ui.parseDailyLogsSafely
import com.pcosina.app.ui.parseFeedbackEntriesSafely
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressDataSanitizationTest {

    @Test
    fun parseDailyLogsSafely_dropsMalformedDates_andBadNestedEntries() {
        val raw = """
            [
              null,
              {
                "date": "bad-date",
                "completedMealIds": ["Breakfast::a1"]
              },
              {
                "date": "2026-04-13",
                "completedMealIds": ["Breakfast::a1", "", null],
                "mealCheckIns": [
                  null,
                  {
                    "mealKey": "Breakfast::a1",
                    "recipeId": "a1",
                    "mealLabel": "Breakfast",
                    "energyLevel": 6,
                    "timestamp": 123
                  },
                  {
                    "mealKey": "",
                    "recipeId": "",
                    "mealLabel": "",
                    "timestamp": 456
                  }
                ],
                "symptomTags": ["Bloating", "", null]
              }
            ]
        """.trimIndent()

        val logs = parseDailyLogsSafely(raw)

        assertEquals(1, logs.size)
        assertEquals("2026-04-13", logs.first().date)
        assertEquals(listOf("Breakfast::a1"), logs.first().completedMealIds)
        assertEquals(1, logs.first().mealCheckIns.size)
        assertEquals(5, logs.first().mealCheckIns.first().energyLevel)
        assertEquals(listOf("Bloating"), logs.first().symptomTags)
    }

    @Test
    fun parseFeedbackEntriesSafely_dropsBlankOrInvalidEntries() {
        val raw = """
            [
              null,
              {
                "id": "",
                "message": "Missing id"
              },
              {
                "id": "ok-1",
                "message": "  Helpful feedback  ",
                "status": "sending",
                "attempts": -2,
                "lastError": "  timeout  "
              }
            ]
        """.trimIndent()

        val entries = parseFeedbackEntriesSafely(raw)

        assertEquals(1, entries.size)
        assertEquals("ok-1", entries.first().id)
        assertEquals("Helpful feedback", entries.first().message)
        assertEquals("Sending", entries.first().status)
        assertEquals(0, entries.first().attempts)
        assertEquals("timeout", entries.first().lastError)
        assertNull(entries.first().lastTriedAt)
        assertTrue(entries.first().createdAt > 0)
    }
}
