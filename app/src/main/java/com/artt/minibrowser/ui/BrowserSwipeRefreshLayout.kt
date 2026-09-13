package com.artt.minibrowser.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.artt.minibrowser.engine.BrowserCommandTarget
import com.artt.minibrowser.engine.createGeckoEngineSessionSidecar
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.browser.engine.gecko.GeckoEngineView
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SessionUseCases
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Pull-to-refresh shell around Android Components' GeckoEngineView.
 *
 * Raw-owned tabs render through a temporary GeckoEngineSession facade that borrows the exact raw
 * GeckoSession. Android Components-owned tabs render through A-C's lifecycle-aware SessionFeature;
 * for a state-only restored tab the feature asks EngineMiddleware to create the missing EngineSession.
 * This view never creates its own A-C EngineSession and SessionFeature is never started for a
 * raw-owned tab or during the relinquished-before-link transfer interval.
 */
internal class BrowserSwipeRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    val engineView = GeckoEngineView(context)

    private var renderedRawSession: GeckoSession? = null
    private var renderedLinkedSession: EngineSession? = null
    private var renderedLinkedTabId: String? = null
    private var engineSessionSidecar: GeckoEngineSession? = null
    private var linkedSessionFeature: SessionFeature? = null
    private var linkedSessionFeatureLifecycle: LinkedSessionFeatureLifecycleBinding? = null
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
        store: BrowserStore,
        tabId: String?,
        target: BrowserCommandTarget<GeckoSession, EngineSession>?,
        privateMode: Boolean,
        allowAndroidComponentsSessionCreation: Boolean = false,
    ) {
        when (target) {
            is BrowserCommandTarget.Raw -> bindRawSession(
                runtime = runtime,
                session = target.session,
                privateMode = privateMode,
            )

            is BrowserCommandTarget.Linked -> {
                val linkedTabId = checkNotNull(tabId) {
                    "Linked render target requires a BrowserStore tab id"
                }
                val storeSession = store.state.tabs
                    .firstOrNull { it.id == linkedTabId }
                    ?.engineState
                    ?.engineSession
                check(storeSession === target.session) {
                    "Linked render target must be the exact BrowserStore EngineSession"
                }
                bindAndroidComponentsSession(store = store, tabId = linkedTabId)
            }

            null -> {
                // BrowserStore dispatch is asynchronous. A fresh A-C structural tab can reach
                // Compose before its queued AddTabAction has reduced; stateFlow retries this bind
                // as soon as the BrowserStore row becomes visible.
                val resolvedTabId = tabId?.takeIf { id ->
                    store.state.tabs.any { it.id == id }
                }
                if (allowAndroidComponentsSessionCreation && resolvedTabId != null) {
                    bindAndroidComponentsSession(store = store, tabId = resolvedTabId)
                } else {
                    releaseRenderedSession()
                }
            }
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

    private fun bindAndroidComponentsSession(
        store: BrowserStore,
        tabId: String,
    ) {
        val storeTab = checkNotNull(store.state.tabs.firstOrNull { it.id == tabId }) {
            "Android Components render target requires a BrowserStore tab"
        }
        val storeSession = storeTab.engineState.engineSession

        if (
            renderedLinkedTabId == tabId &&
            renderedRawSession == null &&
            linkedSessionFeature != null
        ) {
            renderedLinkedSession = storeSession
            attachLinkedSessionFeatureToLifecycle()
            return
        }

        releaseRenderedSession()
        renderedLinkedSession = storeSession
        renderedLinkedTabId = tabId
        val sessionUseCases = SessionUseCases(store)
        linkedSessionFeature = SessionFeature(
            store = store,
            goBackUseCase = sessionUseCases.goBack,
            goForwardUseCase = sessionUseCases.goForward,
            engineView = engineView,
            tabId = tabId,
        ).also { feature ->
            linkedSessionFeatureLifecycle = LinkedSessionFeatureLifecycleBinding(feature)
        }
        attachLinkedSessionFeatureToLifecycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachLinkedSessionFeatureToLifecycle()
    }

    override fun onDetachedFromWindow() {
        linkedSessionFeatureLifecycle?.detach()
        super.onDetachedFromWindow()
    }

    private fun attachLinkedSessionFeatureToLifecycle() {
        linkedSessionFeatureLifecycle?.attach(findViewTreeLifecycleOwner())
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
        if (
            renderedRawSession == null &&
            renderedLinkedSession == null &&
            engineSessionSidecar == null &&
            linkedSessionFeature == null
        ) {
            return
        }

        resetForSessionChange()
        linkedSessionFeatureLifecycle?.detach()
        linkedSessionFeatureLifecycle = null
        linkedSessionFeature = null
        engineView.release()
        renderedRawSession = null
        renderedLinkedSession = null
        renderedLinkedTabId = null
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
