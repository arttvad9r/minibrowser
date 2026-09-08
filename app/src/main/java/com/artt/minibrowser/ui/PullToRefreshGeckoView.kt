package com.artt.minibrowser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * GeckoView child used by [BrowserSwipeRefreshLayout].
 *
 * It does not implement pull physics. It only asks Gecko whether the ACTION_DOWN belongs to a safe
 * browser overscroll gesture. While that asynchronous answer is pending, the parent is prevented
 * from intercepting. If meaningful movement begins before Gecko answers, this gesture remains page
 * owned; pull-to-refresh can only start at the beginning of a pan.
 */
internal class PullToRefreshGeckoView(context: Context) : GeckoView(context) {
    private enum class GateState { BLOCKED, PENDING, ALLOWED }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var trackedSession: GeckoSession? = null
    private var rootScrollY = 0
    private var refreshGateEnabled = false
    private var gateState = GateState.BLOCKED
    private var gestureGeneration = 0L
    private var downX = 0f
    private var downY = 0f

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
        blockCurrentGesture()
    }

    fun configurePullRefreshGate(enabled: Boolean) {
        if (refreshGateEnabled == enabled) return
        refreshGateEnabled = enabled
        if (!enabled) blockCurrentGesture()
    }

    fun canStartBrowserPullRefresh(): Boolean =
        refreshGateEnabled && rootScrollY <= 0 && gateState == GateState.ALLOWED

    fun clearPullRefreshGate() {
        trackedSession?.let { previous ->
            if (previous.scrollDelegate === scrollDelegate) previous.scrollDelegate = null
        }
        trackedSession = null
        rootScrollY = 0
        refreshGateEnabled = false
        blockCurrentGesture()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val generation = ++gestureGeneration
                downX = event.x
                downY = event.y
                gateState = GateState.BLOCKED
                parent?.requestDisallowInterceptTouchEvent(false)

                if (!refreshGateEnabled || rootScrollY > 0) {
                    return super.onTouchEvent(event)
                }

                gateState = GateState.PENDING
                parent?.requestDisallowInterceptTouchEvent(true)

                // Dispatch ACTION_DOWN to Gecko exactly once and additionally request the input
                // details needed by the browser-level gesture gate.
                onTouchEventForDetailResult(event).accept(
                    { detail ->
                        if (generation != gestureGeneration || gateState != GateState.PENDING) {
                            return@accept
                        }
                        val allowed = detail != null && isBrowserPullRefreshEligible(
                            pageEnabled = refreshGateEnabled,
                            rootScrollY = rootScrollY,
                            handledResult = detail.handledResult(),
                            scrollableDirections = detail.scrollableDirections(),
                            overscrollDirections = detail.overscrollDirections(),
                        )
                        gateState = if (allowed) GateState.ALLOWED else GateState.BLOCKED
                        parent?.requestDisallowInterceptTouchEvent(false)
                    },
                    {
                        if (generation == gestureGeneration && gateState == GateState.PENDING) {
                            gateState = GateState.BLOCKED
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    },
                )
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                blockCurrentGesture()
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                if (gateState == GateState.PENDING) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (maxOf(dx, dy) > touchSlop) {
                        // Gecko answered too late for this pan. Do not let refresh join mid-gesture.
                        blockCurrentGesture()
                    }
                }
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_UP -> {
                val handled = super.onTouchEvent(event)
                blockCurrentGesture()
                return handled
            }

            MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(event)
                if (gateState == GateState.PENDING) blockCurrentGesture()
                // If ALLOWED, this CANCEL is normally generated because SwipeRefreshLayout has
                // started intercepting. Keep the gate alive for the parent until that gesture ends.
                return handled
            }

            else -> return super.onTouchEvent(event)
        }
    }

    private fun blockCurrentGesture() {
        ++gestureGeneration
        gateState = GateState.BLOCKED
        parent?.requestDisallowInterceptTouchEvent(false)
    }
}
