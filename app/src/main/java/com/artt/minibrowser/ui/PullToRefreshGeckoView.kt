package com.artt.minibrowser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController

/**
 * GeckoView bridge for pull-to-refresh.
 *
 * The gesture never steals MotionEvents from Gecko. ACTION_DOWN is dispatched through
 * onTouchEventForDetailResult so Gecko can tell us whether the touched scroll container permits
 * vertical browser overscroll; subsequent events continue through GeckoView's normal touch path.
 */
internal class PullToRefreshGeckoView(context: Context) : GeckoView(context) {
    private val density = resources.displayMetrics.density
    private val gestureTracker = PullRefreshGestureTracker(
        touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        triggerDistancePx = 72f * density,
    )

    private var trackedSession: GeckoSession? = null
    private var rootScrollY = 0
    private var refreshEnabled = false
    private var onPullProgress: (Float) -> Unit = {}
    private var onRefresh: () -> Unit = {}
    private var gestureGeneration = 0L

    private val scrollDelegate = object : GeckoSession.ScrollDelegate {
        override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
            if (session === trackedSession) rootScrollY = scrollY
        }
    }

    fun trackScrollFor(session: GeckoSession?) {
        if (trackedSession === session) return
        trackedSession?.let { previous ->
            if (previous.scrollDelegate === scrollDelegate) previous.scrollDelegate = null
        }
        trackedSession = session
        rootScrollY = 0
        session?.scrollDelegate = scrollDelegate
        cancelPullGesture()
    }

    fun configurePullToRefresh(
        enabled: Boolean,
        onProgress: (Float) -> Unit,
        onRefresh: () -> Unit,
    ) {
        refreshEnabled = enabled
        onPullProgress = onProgress
        this.onRefresh = onRefresh
        if (!enabled) cancelPullGesture()
    }

    fun clearPullToRefresh() {
        trackedSession?.let { previous ->
            if (previous.scrollDelegate === scrollDelegate) previous.scrollDelegate = null
        }
        trackedSession = null
        refreshEnabled = false
        onPullProgress = {}
        onRefresh = {}
        rootScrollY = 0
        cancelPullGesture()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val generation = ++gestureGeneration
                gestureTracker.onDown(
                    x = event.x,
                    y = event.y,
                    pageEligible = refreshEnabled && rootScrollY <= 0,
                )
                onPullProgress(0f)

                // This dispatches ACTION_DOWN to Gecko exactly once and additionally returns the
                // browser-overscroll policy for the touched scroll container.
                onTouchEventForDetailResult(event).accept(
                    { detail ->
                        if (generation != gestureGeneration) return@accept
                        val verticalOverscrollAllowed = detail != null &&
                            detail.handledResult() != PanZoomController.INPUT_RESULT_IGNORED &&
                            detail.overscrollDirections() and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0
                        gestureTracker.setGeckoEligible(verticalOverscrollAllowed)
                    },
                    {
                        if (generation == gestureGeneration) gestureTracker.setGeckoEligible(false)
                    },
                )
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelPullGesture()
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val handled = super.onTouchEvent(event)
                if (!refreshEnabled || event.pointerCount != 1) {
                    cancelPullGesture()
                    return handled
                }
                onPullProgress(gestureTracker.onMove(event.x, event.y))
                return handled
            }

            MotionEvent.ACTION_UP -> {
                val handled = super.onTouchEvent(event)
                val refresh = gestureTracker.finish(commit = refreshEnabled)
                ++gestureGeneration
                onPullProgress(0f)
                if (refresh) onRefresh()
                return handled
            }

            MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(event)
                cancelPullGesture()
                return handled
            }

            else -> return super.onTouchEvent(event)
        }
    }

    private fun cancelPullGesture() {
        ++gestureGeneration
        gestureTracker.cancel()
        onPullProgress(0f)
    }
}
