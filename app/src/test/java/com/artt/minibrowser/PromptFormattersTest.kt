package com.artt.minibrowser

import com.artt.minibrowser.engine.formatDateTimeLocal
import com.artt.minibrowser.engine.formatDateValue
import com.artt.minibrowser.engine.formatIsoWeekValue
import com.artt.minibrowser.engine.formatMonthValue
import com.artt.minibrowser.engine.formatTimeValue
import com.artt.minibrowser.engine.parseDateTimeLocalValue
import com.artt.minibrowser.engine.parseDateValue
import com.artt.minibrowser.engine.parseIsoWeekValue
import com.artt.minibrowser.engine.parseMonthValue
import com.artt.minibrowser.engine.parseTimeValue
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class PromptFormattersTest {
    private val date = LocalDate.of(2026, 8, 25)

    @Test fun formatsMachineReadableDateValues() {
        assertEquals("2026-08-25", formatDateValue(date))
        assertEquals("09:05", formatTimeValue(LocalTime.of(9, 5)))
        assertEquals("2026-08-25T09:05", formatDateTimeLocal(date, LocalTime.of(9, 5)))
        assertEquals("2026-08", formatMonthValue(date))
        assertEquals("2026-W35", formatIsoWeekValue(date))
    }

    @Test fun machineReadableValuesIgnoreDefaultLocaleDigits() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("09:05", formatTimeValue(LocalTime.of(9, 5)))
            assertEquals("2026-08", formatMonthValue(date))
            assertEquals("2026-W35", formatIsoWeekValue(date))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun parsesTypeSpecificDateTimeValues() {
        assertEquals(date, parseDateValue("2026-08-25"))
        assertEquals(LocalTime.of(9, 5), parseTimeValue("09:05"))
        assertEquals(date.atTime(9, 5), parseDateTimeLocalValue("2026-08-25T09:05"))
        assertEquals(date.withDayOfMonth(1), parseMonthValue("2026-08"))
        assertEquals(date.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1), parseIsoWeekValue("2026-W35"))
        assertEquals(null, parseMonthValue("2026-13"))
        assertEquals(null, parseIsoWeekValue("2026-W99"))
    }
}
