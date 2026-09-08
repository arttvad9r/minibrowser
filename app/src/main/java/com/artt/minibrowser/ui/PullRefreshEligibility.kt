package com.artt.minibrowser.ui

import org.mozilla.geckoview.PanZoomController

/**
 * Browser-level pull-to-refresh is safe only when the touched scroll container is already at its
 * top edge, the website itself did not consume the touch, and Gecko reports vertical overscroll.
 *
 * Firefox intentionally permits both browser-handled and otherwise-unhandled touches here. A
 * simple/non-scrollable page commonly reports UNHANDLED, and rejecting it makes pull-to-refresh
 * feel intermittent. CONTENT and IGNORED remain browser-chrome ineligible.
 */
internal fun isBrowserPullRefreshEligible(
    pageEnabled: Boolean,
    rootScrollY: Int,
    handledResult: Int,
    scrollableDirections: Int,
    overscrollDirections: Int,
): Boolean {
    if (!pageEnabled || rootScrollY > 0) return false
    if (
        handledResult == PanZoomController.INPUT_RESULT_HANDLED_CONTENT ||
        handledResult == PanZoomController.INPUT_RESULT_IGNORED
    ) {
        return false
    }
    if (scrollableDirections and PanZoomController.SCROLLABLE_FLAG_TOP != 0) return false
    return overscrollDirections and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0
}
