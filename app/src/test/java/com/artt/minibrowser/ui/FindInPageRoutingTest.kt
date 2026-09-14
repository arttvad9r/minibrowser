package com.artt.minibrowser.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import mozilla.components.browser.state.state.content.FindResultState

class FindInPageRoutingTest {
    @Test
    fun androidComponentsOrdinalIsConvertedToHumanReadablePosition() {
        val position = androidComponentsFindPosition(
            FindResultState(
                activeMatchOrdinal = 0,
                numberOfMatches = 3,
                isDoneCounting = true,
            ),
        )

        assertEquals(1 to 3, position)
    }

    @Test
    fun noMatchPositionMatchesAndroidComponentsFindBar() {
        val position = androidComponentsFindPosition(
            FindResultState(
                activeMatchOrdinal = 0,
                numberOfMatches = 0,
                isDoneCounting = true,
            ),
        )

        assertEquals(0 to 0, position)
    }
}
