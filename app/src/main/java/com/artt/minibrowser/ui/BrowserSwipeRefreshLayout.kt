package com.artt.minibrowser.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.lifecycle.LifecycleOwner
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
 * GeckoSession. Relinquished tabs are rendered by A-C's lifecycle-aware SessionFeature from their
 * BrowserStore-linked EngineSession; this view never creates a second facade around an A-C-owned
 * session and SessionFeature is never started for a raw-owned tab.
 */
internal class BrowserSwipeRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    val engineView = GeckoEngineView(context)

    private var renderedRawSession: GeckoSession? = null
    private var renderedLinkedSession: EngineSession? = null
    private var renderedLinkedTabId: String? = null
    private var engineSessionSidecar: GeckoEngineSession? = null
    private var linkedSessionFeature: SessionFeature? = null
    private var linkedSessionFeatureOwner: LifecycleOwner? = null
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
    ) {
        when (target) {
            is BrowserCommandTarget.Raw -> bindRawSession(
                runtime = runtime,
                session = target.session,
                privateMode = privateMode,
            )

            is BrowserCommandTarget.Linked -> bindLinkedSession(
                store = store,
                tabId = checkNotNull(tabId) { "Linked render target requires a BrowserStore tab id" },
                session = target.session,
            )
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

    private fun bindLinkedSession(
        store: BrowserStore,
        tabId: String,
        session: EngineSession,
    ) {
        val storeSession = store.state.tabs
            .firstOrNull { it.id == tabId }
            ?.engineState
            ?.engineSession
        check(storeSession === session) {
            "Linked render target must be the exact BrowserStore EngineSession"
        }

        if (
            renderedLinkedSession === session &&
            renderedLinkedTabId == tabId &&
            renderedRawSession == null
        ) {
            attachLinkedSessionFeatureToLifecycle()
            return
        }

        releaseRenderedSession()
        renderedLinkedSession = session
        renderedLinkedTabId = tabId
        linkedSessionFeature = SessionFeature(
            store = store,
            goBackUseCase = SessionUseCases(store).goBack,
            engineView = engineView,
            tabId = tabId,
        )
        attachLinkedSessionFeatureToLifecycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachLinkedSessionFeatureToLifecycle()
    }

    override fun onDetachedFromWindow() {
        detachLinkedSessionFeatureFromLifecycle()
        super.onDetachedFromWindow()
    }

    private fun attachLinkedSessionFeatureToLifecycle() {
        val feature = linkedSessionFeature ?: return
        val owner = findViewTreeLifecycleOwner() ?: return
        if (linkedSessionFeatureOwner === owner) return

        detachLinkedSessionFeatureFromLifecycle()
        linkedSessionFeatureOwner = owner
        owner.lifecycle.addObserver(feature)
    }

    private fun detachLinkedSessionFeatureFromLifecycle() {
        val feature = linkedSessionFeature ?: return
        val owner = linkedSessionFeatureOwner ?: return
        val wasStarted = owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        owner.lifecycle.removeObserver(feature)
        linkedSessionFeatureOwner = null
        if (wasStarted) {
            // Removing an observer while STARTED does not synthesize onStop. SessionFeature.stop()
            // releases the EngineView without closing its BrowserStore-owned EngineSession.
            feature.stop()
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
        detachLinkedSessionFeatureFromLifecycle()
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