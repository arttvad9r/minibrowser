package com.artt.minibrowser

import androidx.window.core.layout.WindowSizeClass
import com.artt.minibrowser.ui.tabGridColumnCount
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserTabSwitcherLayoutTest {
    @Test
    fun compactWidthUsesTwoColumns() {
        assertEquals(2, tabGridColumnCount(windowSizeClass(599f)))
    }

    @Test
    fun mediumWidthUsesThreeColumns() {
        assertEquals(3, tabGridColumnCount(windowSizeClass(600f)))
        assertEquals(3, tabGridColumnCount(windowSizeClass(839f)))
    }

    @Test
    fun expandedWidthUsesFourColumns() {
        assertEquals(4, tabGridColumnCount(windowSizeClass(840f)))
        assertEquals(4, tabGridColumnCount(windowSizeClass(1199f)))
    }

    @Test
    fun largeWidthUsesFiveColumns() {
        assertEquals(5, tabGridColumnCount(windowSizeClass(1200f)))
        assertEquals(5, tabGridColumnCount(windowSizeClass(1599f)))
    }

    @Test
    fun extraLargeWidthUsesSixColumns() {
        assertEquals(6, tabGridColumnCount(windowSizeClass(1600f)))
        assertEquals(6, tabGridColumnCount(windowSizeClass(2000f)))
    }

    private fun windowSizeClass(widthDp: Float): WindowSizeClass =
        WindowSizeClass(widthDp, 800f)
}
