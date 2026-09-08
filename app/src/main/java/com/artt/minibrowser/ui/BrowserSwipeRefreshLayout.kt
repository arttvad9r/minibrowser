package com.artt.minibrowser.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Chrome-like pull-to-refresh shell around GeckoView.
 *
 * AndroidX SwipeRefreshLayout provides the same core interaction model used by Chromium's modified
 * implementation: a 40 dp indicator, 64 dp trigger/rest target, 0.5 drag rate, nonlinear tension,
 * and native settle/retract animations. GeckoView remains the authority on whether touched web
 * content may yield the gesture to browser chrome.
 */
internal class BrowserSwipeRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    val geckoView = PullToRefreshGeckoView(context)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopAfterLoad = Runnable { finishRefreshAnimation() }
    private val hardStop = Runnable { finishRefreshAnimation() }

    private var pageSupportsRefresh = false
    private var pageLoading = false
    private var refreshInFlight = false
    private var sawLoadingAfterRefresh = false
    private var refreshAction: () -> Unit = {}

    init {
        addView(
            geckoView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // Keep AndroidX's native spinner start/end geometry. The previous custom start offset
        // changed SwipeRefreshLayout into custom-start mode and produced a visibly different
        // slingshot path from Chrome/Firefox.
        setSize(DEFAULT)

        setOnChildScrollUpCallback { _, _ ->
            !gestureEnabled() || !geckoView.canStartBrowserPullRefresh()
        }
        setOnRefreshListener {
            if (!gestureEnabled() || !geckoView.canStartBrowserPullRefresh()) {
                isRefreshing = false
                return@setOnRefreshListener
            }
            beginRefresh()
        }
    }

    fun configurePullToRefresh(
        pageSupportsRefresh: Boolean,
        pageLoading: Boolean,
        @ColorInt indicatorColor: Int,
        @ColorInt indicatorBackgroundColor: Int,
        onRefresh: () -> Unit,
    ) {
        this.pageSupportsRefresh = pageSupportsRefresh
        this.pageLoading = pageLoading
        refreshAction = onRefresh

        setColorSchemeColors(indicatorColor)
        setProgressBackgroundColorSchemeColor(indicatorBackgroundColor)

        if (refreshInFlight) {
            if (pageLoading) {
                sawLoadingAfterRefresh = true
                mainHandler.removeCallbacks(stopAfterLoad)
            } else if (sawLoadingAfterRefresh) {
                // Chromium keeps the completed refresh animation visible briefly instead of
                // snapping it away exactly when the network load reports completion.
                mainHandler.removeCallbacks(stopAfterLoad)
                mainHandler.postDelayed(stopAfterLoad, STOP_REFRESH_ANIMATION_DELAY_MS)
            }
        }

        geckoView.configurePullRefreshGate(gestureEnabled())
    }

    fun resetForSessionChange() {
        mainHandler.removeCallbacks(stopAfterLoad)
        mainHandler.removeCallbacks(hardStop)
        refreshInFlight = false
        sawLoadingAfterRefresh = false
        isRefreshing = false
        geckoView.configurePullRefreshGate(false)
    }

    fun clearPullToRefresh() {
        resetForSessionChange()
        pageSupportsRefresh = false
        pageLoading = false
        refreshAction = {}
        geckoView.clearPullRefreshGate()
    }

    private fun beginRefresh() {
        refreshInFlight = true
        sawLoadingAfterRefresh = false
        geckoView.configurePullRefreshGate(false)
        mainHandler.removeCallbacks(stopAfterLoad)
        mainHandler.removeCallbacks(hardStop)
        mainHandler.postDelayed(hardStop, MAX_REFRESH_ANIMATION_DURATION_MS)
        refreshAction()
    }

    private fun finishRefreshAnimation() {
        mainHandler.removeCallbacks(stopAfterLoad)
        mainHandler.removeCallbacks(hardStop)
        refreshInFlight = false
        sawLoadingAfterRefresh = false
        isRefreshing = false
        geckoView.configurePullRefreshGate(gestureEnabled())
    }

    private fun gestureEnabled(): Boolean =
        pageSupportsRefresh && !pageLoading && !refreshInFlight

    private companion object {
        const val STOP_REFRESH_ANIMATION_DELAY_MS = 500L
        const val MAX_REFRESH_ANIMATION_DURATION_MS = 7_500L
    }
}
