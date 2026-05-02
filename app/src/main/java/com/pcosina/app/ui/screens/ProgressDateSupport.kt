package com.pcosina.app.ui.screens

import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun parseProgressDateOrNull(dateKey: String): LocalDate? {
    return runCatching { LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
}

internal fun isInWeek(dateKey: String, weekStart: LocalDate): Boolean {
    val parsedDate = parseProgressDateOrNull(dateKey) ?: return false
    return !parsedDate.isBefore(weekStart) && !parsedDate.isAfter(weekStart.plusDays(6))
}
