package com.artt.minibrowser.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.artt.minibrowser.engine.BrowserCommandTarget
import com.artt.minibrowser.engine.createGeckoEngineSessionSidecar
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.browser.engine.gecko.GeckoEngineView
import mozilla.components.concept.engine.EngineSession
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Pull-to-refresh shell around Android Components' GeckoEngineView.
 *
 * Raw-owned tabs render through a temporary GeckoEngineSession facade that borrows the exact raw
 * GeckoSession. Relinquished tabs render their BrowserStore-linked EngineSession directly; this
 * view never creates a second facade around a session whose lifetime has moved to EngineMiddleware.
 */
internal class BrowserSwipeRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    val engineView = GeckoEngineView(context)

    private var renderedRawSession: GeckoSession? = null
    private var renderedLinkedSession: EngineSession? = null
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
        target: BrowserCommandTarget<GeckoSession, EngineSession>?,
        privateMode: Boolean,
    ) {
        when (target) {
            is BrowserCommandTarget.Raw -> bindRawSession(
                runtime = runtime,
                session = target.session,
                privateMode = privateMode,
            )

            is BrowserCommandTarget.Linked -> bindLinkedSession(target.session)
            null -> releaseRenderedSession()
        }
    }

    private fun bindRawSession(
        runtime: GeckoRuntime,
        session: GeckoSession,
        privateMode: Boolean,
    ) {
        if (renderedRawSession === session && renderedLinkedSession == null) return

        releaseRenderedSession()
        renderedRawSession = session
        engineSessionSidecar = createGeckoEngineSessionSidecar(
            runtime = runtime,
            session = session,
            privateMode = privateMode,
        ).also(engineView::render)
    }

    private fun bindLinkedSession(session: EngineSession) {
        if (renderedLinkedSession === session && renderedRawSession == null) return

        releaseRenderedSession()
        renderedLinkedSession = session
        engineView.render(session)
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

    private fun releaseRenderedSession() {
        if (renderedRawSession == null && renderedLinkedSession == null && engineSessionSidecar == null) return

        resetForSessionChange()
        engineView.release()
        renderedRawSession = null
        renderedLinkedSession = null
        // Do not close either session here. Raw sidecars borrow TabManager-owned GeckoSessions;
        // linked EngineSessions are owned and closed by BrowserStore/EngineMiddleware.
        engineSessionSidecar = null
    }

    fun clearPullToRefresh() {
        resetForSessionChange()
        pageSupportsRefresh = false
        refreshAction = {}
        isEnabled = false
        releaseRenderedSession()
    }
}
