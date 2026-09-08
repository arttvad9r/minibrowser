package com.artt.minibrowser

import com.artt.minibrowser.ui.isBrowserPullRefreshEligible
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mozilla.geckoview.PanZoomController

class PullRefreshEligibilityTest {
    private fun eligible(
        pageEnabled: Boolean = true,
        rootScrollY: Int = 0,
        handledResult: Int = PanZoomController.INPUT_RESULT_HANDLED,
        scrollableDirections: Int = PanZoomController.SCROLLABLE_FLAG_BOTTOM,
        overscrollDirections: Int = PanZoomController.OVERSCROLL_FLAG_VERTICAL,
    ) = isBrowserPullRefreshEligible(
        pageEnabled = pageEnabled,
        rootScrollY = rootScrollY,
        handledResult = handledResult,
        scrollableDirections = scrollableDirections,
        overscrollDirections = overscrollDirections,
    )

    @Test
    fun browserOwnedOverscrollAtTopIsEligible() {
        assertTrue(eligible())
    }

    @Test
    fun disabledOrScrolledPageIsNotEligible() {
        assertFalse(eligible(pageEnabled = false))
        assertFalse(eligible(rootScrollY = 1))
    }

    @Test
    fun touchedNestedScrollerThatCanMoveTowardTopIsNotEligible() {
        assertFalse(
            eligible(
                scrollableDirections = PanZoomController.SCROLLABLE_FLAG_TOP or
                    PanZoomController.SCROLLABLE_FLAG_BOTTOM,
            ),
        )
    }

    @Test
    fun pageWithoutVerticalOverscrollPermissionIsNotEligible() {
        assertFalse(eligible(overscrollDirections = PanZoomController.OVERSCROLL_FLAG_NONE))
        assertFalse(eligible(overscrollDirections = PanZoomController.OVERSCROLL_FLAG_HORIZONTAL))
    }

    @Test
    fun contentHandledIgnoredAndUnhandledTouchesNeverStartBrowserRefresh() {
        assertFalse(eligible(handledResult = PanZoomController.INPUT_RESULT_HANDLED_CONTENT))
        assertFalse(eligible(handledResult = PanZoomController.INPUT_RESULT_IGNORED))
        assertFalse(eligible(handledResult = PanZoomController.INPUT_RESULT_UNHANDLED))
    }
}
