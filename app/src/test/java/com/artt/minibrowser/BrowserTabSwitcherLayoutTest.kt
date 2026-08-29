package com.artt.minibrowser

import com.artt.minibrowser.ui.tabGridColumnCount
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserTabSwitcherLayoutTest {
    @Test
    fun compactWidthUsesTwoColumns() {
        assertEquals(2, tabGridColumnCount(599f))
    }

    @Test
    fun mediumWidthStartsAt600Dp() {
        assertEquals(3, tabGridColumnCount(600f))
        assertEquals(3, tabGridColumnCount(839f))
    }

    @Test
    fun expandedWidthStartsAt840Dp() {
        assertEquals(4, tabGridColumnCount(840f))
        assertEquals(4, tabGridColumnCount(1200f))
    }
}
