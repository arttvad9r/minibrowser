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
 * The view mirrors Firefox's Gecko pull-to-refresh hand-off: ACTION_DOWN is first offered to Gecko
 * for detailed input classification while the parent is prevented from intercepting. If Gecko says
 * the touched content can overscroll from its top edge and the website did not consume the touch,
 * a downward pan may be yielded to [BrowserSwipeRefreshLayout]. Pull physics and animation stay in
 * AndroidX instead of being reimplemented here.
 */
internal class PullToRefreshGeckoView(context: Context) : GeckoView(context) {
    private enum class GateState { BLOCKED, PENDING, ALLOWED }
    private enum class InitialScrollDirection { NOT_YET, PULL_DOWN, PUSH_UP }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var trackedSession: GeckoSession? = null
    private var rootScrollY = 0
    private var refreshGateEnabled = false
    private var gateState = GateState.BLOCKED
    private var initialScrollDirection = InitialScrollDirection.NOT_YET
    private var gestureGeneration = 0L
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
        finishCurrentGesture()
    }

    fun configurePullRefreshGate(enabled: Boolean) {
        if (refreshGateEnabled == enabled) return
        refreshGateEnabled = enabled
        if (!enabled) finishCurrentGesture()
    }

    fun canStartBrowserPullRefresh(): Boolean =
        refreshGateEnabled &&
            rootScrollY <= 0 &&
            gateState == GateState.ALLOWED &&
            initialScrollDirection != InitialScrollDirection.PUSH_UP

    fun clearPullRefreshGate() {
        trackedSession?.let { previous ->
            if (previous.scrollDelegate === scrollDelegate) previous.scrollDelegate = null
        }
        trackedSession = null
        rootScrollY = 0
        refreshGateEnabled = false
        finishCurrentGesture()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val generation = ++gestureGeneration
                downY = event.y
                initialScrollDirection = InitialScrollDirection.NOT_YET
                gateState = GateState.BLOCKED

                if (!refreshGateEnabled || rootScrollY > 0) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return super.onTouchEvent(event)
                }

                // Firefox keeps the parent out until Gecko answers. This avoids the refresh
                // container stealing the gesture before APZ/site touch handlers classify it.
                gateState = GateState.PENDING
                parent?.requestDisallowInterceptTouchEvent(true)

                // ACTION_DOWN must go through this result-producing API exactly once. Subsequent
                // MOVE/UP events continue through normal GeckoView dispatch below.
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

                        if (allowed && initialScrollDirection != InitialScrollDirection.PUSH_UP) {
                            parent?.requestDisallowInterceptTouchEvent(false)
                        } else {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    },
                    {
                        if (generation == gestureGeneration && gateState == GateState.PENDING) {
                            gateState = GateState.BLOCKED
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    },
                )
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                ++gestureGeneration
                gateState = GateState.BLOCKED
                initialScrollDirection = InitialScrollDirection.PUSH_UP
                parent?.requestDisallowInterceptTouchEvent(true)
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                if (initialScrollDirection == InitialScrollDirection.NOT_YET) {
                    val deltaY = event.y - downY
                    if (abs(deltaY) > touchSlop) {
                        initialScrollDirection =
                            if (deltaY > 0f) {
                                InitialScrollDirection.PULL_DOWN
                            } else {
                                InitialScrollDirection.PUSH_UP
                            }

                        when {
                            initialScrollDirection == InitialScrollDirection.PULL_DOWN &&
                                gateState == GateState.ALLOWED -> {
                                parent?.requestDisallowInterceptTouchEvent(false)
                            }

                            else -> parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(event)
                finishCurrentGesture()
                return handled
            }

            else -> return super.onTouchEvent(event)
        }
    }

    private fun finishCurrentGesture() {
        ++gestureGeneration
        gateState = GateState.BLOCKED
        initialScrollDirection = InitialScrollDirection.NOT_YET
        parent?.requestDisallowInterceptTouchEvent(false)
    }
}
