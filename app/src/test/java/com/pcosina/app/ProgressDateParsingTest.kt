package com.pcosina.app

import com.pcosina.app.ui.screens.isInWeek
import com.pcosina.app.ui.screens.parseProgressDateOrNull
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressDateParsingTest {

    @Test
    fun parseProgressDateOrNull_returnsNullForMalformedKeys() {
        assertNull(parseProgressDateOrNull("not-a-date"))
        assertNull(parseProgressDateOrNull("2026/04/12"))
    }

    @Test
    fun isInWeek_returnsFalseForMalformedKeys_insteadOfThrowing() {
        val weekStart = LocalDate.of(2026, 4, 6)

        assertFalse(isInWeek("bad-key", weekStart))
        assertTrue(isInWeek("2026-04-08", weekStart))
    }
}
