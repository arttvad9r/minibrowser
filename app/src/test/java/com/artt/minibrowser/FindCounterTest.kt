package com.artt.minibrowser

import com.artt.minibrowser.engine.formatFindCounter
import kotlin.test.Test
import kotlin.test.assertEquals

class FindCounterTest {
    @Test fun usesGeckosOneBasedCurrentOrdinal() {
        assertEquals("1/1", formatFindCounter(1, 1))
        assertEquals("1/2", formatFindCounter(1, 2))
        assertEquals("2/2", formatFindCounter(2, 2))
    }

    @Test fun hidesUnknownOrEmptyResults() {
        assertEquals("", formatFindCounter(0, 0))
        assertEquals("", formatFindCounter(0, -1))
    }
}
