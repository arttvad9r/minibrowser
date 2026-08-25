package com.artt.minibrowser

import com.artt.minibrowser.engine.formatDateTimeLocal
import com.artt.minibrowser.engine.formatDateValue
import com.artt.minibrowser.engine.formatIsoWeekValue
import com.artt.minibrowser.engine.formatMonthValue
import com.artt.minibrowser.engine.formatTimeValue
import java.time.LocalDate
import java.time.LocalTime
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
}
