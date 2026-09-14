package com.artt.minibrowser.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PromptFormattersTest {
    @Test
    fun formatsHtmlDateTimeValues() {
        val date = LocalDate.of(2026, 9, 9)
        val time = LocalTime.of(8, 7, 45)

        assertEquals("2026-09-09", formatDateValue(date))
        assertEquals("08:07", formatTimeValue(time))
        assertEquals("2026-09-09T08:07", formatDateTimeLocal(date, time))
        assertEquals("2026-09", formatMonthValue(date))
    }

    @Test
    fun parsesTimeWithOptionalSecondsAndFraction() {
        assertEquals(LocalTime.of(8, 7), parseTimeValue("08:07"))
        assertEquals(LocalTime.of(8, 7, 45), parseTimeValue("08:07:45"))
        assertEquals(LocalTime.of(8, 7, 45, 125_000_000), parseTimeValue("08:07:45.125"))
        assertNull(parseTimeValue("25:00"))
    }

    @Test
    fun parsesDateTimeLocalAndMonthValues() {
        assertEquals(
            LocalDateTime.of(2026, 9, 9, 8, 7),
            parseDateTimeLocalValue("2026-09-09T08:07"),
        )
        assertEquals(LocalDate.of(2026, 9, 1), parseMonthValue("2026-09"))
        assertNull(parseMonthValue("2026-13"))
    }

    @Test
    fun preservesIsoWeekYearAcrossCalendarYearBoundary() {
        assertEquals("2020-W53", formatIsoWeekValue(LocalDate.of(2021, 1, 1)))
        assertEquals("2021-W01", formatIsoWeekValue(LocalDate.of(2021, 1, 4)))
        assertEquals(LocalDate.of(2020, 12, 28), parseIsoWeekValue("2020-W53"))
    }

    @Test
    fun rejectsInvalidIsoWeekNumbers() {
        assertNull(parseIsoWeekValue("2021-W53"))
        assertNull(parseIsoWeekValue("2026-W00"))
        assertNull(parseIsoWeekValue("2026-W54"))
        assertNull(parseIsoWeekValue("2026-36"))
    }
}
