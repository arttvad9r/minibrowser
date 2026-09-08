package com.artt.minibrowser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import androidx.core.view.NestedScrollingChild
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController

/**
 * GeckoView touch bridge modeled after Firefox Android's NestedGeckoView contract.
 *
 * The view owns Gecko/APZ input classification and only lets SwipeRefreshLayout intercept when
 * the current gesture can overscroll from the top and the website did not consume the touch.
 * It also participates in Android nested scrolling so parent interception behaves the same way as
 * Firefox's GeckoEngineView -> NestedGeckoView hierarchy.
 */
@Suppress("ClickableViewAccessibility")
internal class PullToRefreshGeckoView(context: Context) : GeckoView(context), NestedScrollingChild {
    private val nestedChildHelper = NestedScrollingChildHelper(this)
    private val scrollConsumed = IntArray(2)
    private val scrollOffset = IntArray(2)

    private var lastY = 0
    private var nestedOffsetY = 0
    private var initialDownY = 0f
    private var gestureCanReachParent = true
    private var inputDetail = GeckoTouchDetail.initialForPullRefresh()

    init {
        isNestedScrollingEnabled = true
    }

    fun canOverscrollTop(): Boolean = inputDetail.canOverscrollTop()

    fun resetPullRefreshTouchState() {
        inputDetail = GeckoTouchDetail.initialForPullRefresh()
        gestureCanReachParent = true
        nestedOffsetY = 0
        stopNestedScroll()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    @Suppress("ComplexMethod")
    override fun onTouchEvent(sourceEvent: MotionEvent): Boolean {
        val event = MotionEvent.obtain(sourceEvent)
        val action = sourceEvent.actionMasked
        val eventY = event.y.toInt()

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateInputDetail(event)

                nestedOffsetY = 0
                lastY = eventY
                initialDownY = event.y

                event.recycle()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val browserIsPanning = !shouldPinOnScreen() && inputDetail.isHandledByBrowser()
                var deltaY = lastY - eventY

                if (browserIsPanning && dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset)) {
                    deltaY -= scrollConsumed[1]
                    event.offsetLocation(0f, -scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                }

                lastY = eventY - scrollOffset[1]

                if (browserIsPanning && dispatchNestedScroll(0, scrollOffset[1], 0, deltaY, scrollOffset)) {
                    lastY -= scrollOffset[1]
                    event.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                }

                if (gestureCanReachParent && event.y != initialDownY) {
                    updateInputDetail(event)
                    event.recycle()
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                inputDetail = GeckoTouchDetail.initialForPullRefresh()
                stopNestedScroll()
                parent?.requestDisallowInterceptTouchEvent(false)
                gestureCanReachParent = true
            }
        }

        val handled = super.onTouchEvent(event)
        event.recycle()
        return handled
    }

    @SuppressLint("WrongThread")
    private fun updateInputDetail(event: MotionEvent) {
        val action = event.actionMasked
        val eventY = event.y

        onTouchEventForDetailResult(event).accept { geckoDetail ->
            if (!gestureCanReachParent) return@accept

            inputDetail = inputDetail.updatedFrom(geckoDetail)

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    gestureCanReachParent = inputDetail.canOverscrollTop()
                    if (gestureCanReachParent && inputDetail.isUnhandled()) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    when {
                        eventY > initialDownY -> {
                            if (!inputDetail.isHandledByWebsite()) {
                                parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }

                        eventY < initialDownY -> {
                            parent?.requestDisallowInterceptTouchEvent(true)
                            gestureCanReachParent = false
                        }

                        else -> parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }

            startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
        }
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        nestedChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = nestedChildHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = nestedChildHelper.startNestedScroll(axes)

    override fun stopNestedScroll() {
        nestedChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean = nestedChildHelper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean = nestedChildHelper.dispatchNestedScroll(
        dxConsumed,
        dyConsumed,
        dxUnconsumed,
        dyUnconsumed,
        offsetInWindow,
    )

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean = nestedChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        nestedChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        nestedChildHelper.dispatchNestedPreFling(velocityX, velocityY)

    private data class GeckoTouchDetail(
        val handledResult: Int,
        val scrollableDirections: Int,
        val overscrollDirections: Int,
    ) {
        fun updatedFrom(detail: PanZoomController.InputResultDetail?): GeckoTouchDetail {
            if (detail == null) return this

            val nextHandled = when (detail.handledResult()) {
                PanZoomController.INPUT_RESULT_UNHANDLED,
                PanZoomController.INPUT_RESULT_HANDLED,
                PanZoomController.INPUT_RESULT_HANDLED_CONTENT -> detail.handledResult()
                else -> handledResult
            }

            return copy(
                handledResult = nextHandled,
                scrollableDirections = detail.scrollableDirections(),
                overscrollDirections = detail.overscrollDirections(),
            )
        }

        fun isUnhandled(): Boolean = handledResult == PanZoomController.INPUT_RESULT_UNHANDLED

        fun isHandledByBrowser(): Boolean = handledResult == PanZoomController.INPUT_RESULT_HANDLED

        fun isHandledByWebsite(): Boolean = handledResult == PanZoomController.INPUT_RESULT_HANDLED_CONTENT

        fun canOverscrollTop(): Boolean =
            handledResult != PanZoomController.INPUT_RESULT_HANDLED_CONTENT &&
                scrollableDirections and PanZoomController.SCROLLABLE_FLAG_TOP == 0 &&
                overscrollDirections and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0

        companion object {
            private const val INPUT_HANDLING_UNKNOWN = -1

            fun initialForPullRefresh() = GeckoTouchDetail(
                handledResult = INPUT_HANDLING_UNKNOWN,
                scrollableDirections = 0,
                overscrollDirections = PanZoomController.OVERSCROLL_FLAG_VERTICAL,
            )
        }
    }
}
