package com.artt.minibrowser.ui

import org.mozilla.geckoview.PanZoomController

/**
 * Browser-level pull-to-refresh is safe only when Gecko owns the pan, the touched scroll container
 * is already at its top edge, and the page permits vertical overscroll.
 */
internal fun isBrowserPullRefreshEligible(
    pageEnabled: Boolean,
    rootScrollY: Int,
    handledResult: Int,
    scrollableDirections: Int,
    overscrollDirections: Int,
): Boolean {
    if (!pageEnabled || rootScrollY > 0) return false
    if (handledResult != PanZoomController.INPUT_RESULT_HANDLED) return false
    if (scrollableDirections and PanZoomController.SCROLLABLE_FLAG_TOP != 0) return false
    return overscrollDirections and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0
}
