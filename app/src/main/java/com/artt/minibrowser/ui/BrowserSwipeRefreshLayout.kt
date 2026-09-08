package com.artt.minibrowser.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.artt.minibrowser.engine.createGeckoEngineSessionSidecar
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.browser.engine.gecko.GeckoEngineView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Pull-to-refresh shell around Android Components' GeckoEngineView.
 *
 * GeckoEngineView contains Mozilla's NestedGeckoView, so APZ input classification and nested
 * scrolling come from the same implementation used by Firefox Android instead of a MiniBrowser
 * copy. Refresh triggering remains local for this migration step; SwipeRefreshFeature takes over
 * once BrowserStore owns the linked EngineSession.
 */
internal class BrowserSwipeRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    val engineView = GeckoEngineView(context)

    private var renderedSession: GeckoSession? = null
    private var engineSessionSidecar: GeckoEngineSession? = null
    private var pageSupportsRefresh = false
    private var pageLoading = false
    private var refreshAction: () -> Unit = {}

    init {
        addView(
            engineView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        setSize(DEFAULT)
        setOnChildScrollUpCallback { _, _ ->
            !pageSupportsRefresh || !engineView.getInputResultDetail().canOverscrollTop()
        }
        setOnRefreshListener {
            if (!pageSupportsRefresh) {
                isRefreshing = false
                return@setOnRefreshListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            refreshAction()
        }
    }

    fun bindSession(
        runtime: GeckoRuntime,
        session: GeckoSession?,
        privateMode: Boolean,
    ) {
        if (renderedSession === session) return

        resetForSessionChange()
        engineView.release()
        renderedSession = session
        engineSessionSidecar = session?.let {
            createGeckoEngineSessionSidecar(
                runtime = runtime,
                session = it,
                privateMode = privateMode,
            ).also(engineView::render)
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

        isEnabled = pageSupportsRefresh
        setColorSchemeColors(indicatorColor)
        setProgressBackgroundColorSchemeColor(indicatorBackgroundColor)

        // Firefox ends the refresh animation as soon as the observed tab stops loading.
        if (!pageLoading || !pageSupportsRefresh) {
            isRefreshing = false
        }
    }

    fun resetForSessionChange() {
        isRefreshing = false
        pageLoading = false
    }

    fun clearPullToRefresh() {
        resetForSessionChange()
        pageSupportsRefresh = false
        refreshAction = {}
        isEnabled = false
        engineView.release()
        renderedSession = null
        // Do not call GeckoEngineSession.close(): the sidecar borrows a GeckoSession whose lifetime
        // is still owned by TabManager during this migration stage.
        engineSessionSidecar = null
    }
}
