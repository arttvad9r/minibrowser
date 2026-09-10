package com.artt.minibrowser.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineSessionState
import mozilla.components.lib.state.Middleware
import org.mozilla.geckoview.GeckoSession

/**
 * A-C-owned session state paired with the exact Gecko history URL from the same callback.
 *
 * EngineSessionState is intentionally kept opaque here. Persistence encoding happens later through
 * the public EngineSessionState.writeTo contract; this bridge only solves the otherwise-racy URL
 * binding needed before raw Tab session state can stop being authoritative.
 */
internal data class AndroidComponentsBoundEngineSessionState(
    val state: EngineSessionState,
    val stateUrl: String,
)

internal fun shouldRequestAndroidComponentsSessionStatePersist(
    isPrivate: Boolean,
    ownership: RawSessionOwnership,
): Boolean = !isPrivate && ownership == RawSessionOwnership.Relinquished

/**
 * Routes an app-scoped A-C persistence-state change into TabManager's existing debounced writer.
 * The concrete changed session id is intentionally unnecessary here: one dirty signal serializes the
 * complete current tab snapshot, and the queue is conflated. The per-session entry point remains for
 * the irreversible existing-session transfer, where the exact id is already available synchronously.
 */
internal fun TabManager.requestPersistForAndroidComponentsSessionStateChange() {
    val persistedTab = tabs.value.firstOrNull { tab ->
        shouldRequestAndroidComponentsSessionStatePersist(
            isPrivate = tab.isPrivate,
            ownership = tab.rawSessionOwnership,
        )
    } ?: return
    requestPersistFromAndroidComponentsSessionState(persistedTab.id.toString())
}

/** App-scoped latest bound EngineSessionState for sessions that have crossed to A-C ownership. */
internal class AndroidComponentsSessionStatePersistenceState {
    private val mutableSnapshots =
        MutableStateFlow<Map<String, AndroidComponentsBoundEngineSessionState>>(emptyMap())

    val snapshots: StateFlow<Map<String, AndroidComponentsBoundEngineSessionState>> =
        mutableSnapshots.asStateFlow()

    fun snapshot(sessionId: String): AndroidComponentsBoundEngineSessionState? =
        mutableSnapshots.value[sessionId]

    fun bind(
        sessionId: String,
        stateUrl: String?,
        state: EngineSessionState,
    ) {
        val bound = stateUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { url -> AndroidComponentsBoundEngineSessionState(state = state, stateUrl = url) }
        mutableSnapshots.update { current ->
            if (bound == null) current - sessionId else current + (sessionId to bound)
        }
    }

    fun remove(sessionId: String) {
        mutableSnapshots.update { current -> current - sessionId }
    }

    fun retain(sessionIds: Set<String>) {
        mutableSnapshots.update { current ->
            if (current.keys.all(sessionIds::contains)) {
                current
            } else {
                current.filterKeys(sessionIds::contains)
            }
        }
    }
}

/**
 * Correlates A-C's EngineSessionState with GeckoView's exact SessionState URL without reading
 * BrowserStore content state.
 *
 * GeckoEngineSession creates the opaque EngineSessionState inside its ProgressDelegate and invokes
 * EngineSession observers synchronously. BrowserStore's EngineObserver then dispatches its action
 * asynchronously, so reading BrowserStore after forwarding the raw callback would race. This
 * observer instead runs inside the same stock notifyObservers call while [withRawSessionStateUrl]
 * keeps the source Gecko history URL on the stack.
 *
 * [onPersistenceStateChanged] reports the exact session id plus the currently safe bound URL after
 * every opaque-state update or fail-closed invalidation. It intentionally does not persist itself:
 * the ownership coordinator must route this signal into the app's single persistence writer.
 */
internal class AndroidComponentsSessionStatePersistenceObserver(
    private val sessionId: String,
    private val persistenceState: AndroidComponentsSessionStatePersistenceState,
    private val onPersistenceStateChanged: (sessionId: String, stateUrl: String?) -> Unit = { _, _ -> },
) : EngineSession.Observer {
    private data class Capture(
        val stateUrl: String?,
        var observed: Boolean = false,
    )

    private var currentCapture: Capture? = null

    fun withRawSessionStateUrl(
        stateUrl: String?,
        forward: () -> Unit,
    ) {
        val previous = currentCapture
        val capture = Capture(stateUrl)
        currentCapture = capture
        try {
            forward()
        } finally {
            // Restore correlation context before invoking external persistence signaling. A throwing
            // signal callback must never leave a stale capture installed for a later engine update.
            currentCapture = previous
            // If the stock A-C delegate did not emit a matching opaque state, retaining an older
            // snapshot would incorrectly bind it to a later document. Fail closed instead.
            if (!capture.observed) {
                persistenceState.remove(sessionId)
                notifyPersistenceStateChanged()
            }
        }
    }

    override fun onStateUpdated(state: EngineSessionState) {
        val capture = currentCapture
        if (capture == null) {
            // An uncorrelated state cannot be safely associated with a URL.
            persistenceState.remove(sessionId)
            notifyPersistenceStateChanged()
            return
        }

        capture.observed = true
        persistenceState.bind(
            sessionId = sessionId,
            stateUrl = capture.stateUrl,
            state = state,
        )
        notifyPersistenceStateChanged()
    }

    private fun notifyPersistenceStateChanged() {
        onPersistenceStateChanged(
            sessionId,
            persistenceState.snapshot(sessionId)?.stateUrl,
        )
    }
}

/** Intercepts only SessionState callbacks while preserving the stock A-C ProgressDelegate. */
internal class AndroidComponentsSessionStatePersistenceDelegate(
    private val delegate: GeckoSession.ProgressDelegate,
    private val observer: AndroidComponentsSessionStatePersistenceObserver,
) : GeckoSession.ProgressDelegate by delegate {
    override fun onSessionStateChange(
        session: GeckoSession,
        sessionState: GeckoSession.SessionState,
    ) {
        observer.withRawSessionStateUrl(currentSessionStateUrl(sessionState)) {
            delegate.onSessionStateChange(session, sessionState)
        }
    }
}

/**
 * Installs callback-level URL binding around the stock GeckoEngineSession delegate.
 *
 * Call this before the MiniBrowser UI progress wrapper so the existing UI delegate remains the
 * outermost raw Gecko delegate while SessionState still reaches this wrapper and then stock A-C.
 */
internal fun installAndroidComponentsSessionStatePersistence(
    session: GeckoSession,
    engineSession: EngineSession,
    sessionId: String,
    persistenceState: AndroidComponentsSessionStatePersistenceState,
    onPersistenceStateChanged: (sessionId: String, stateUrl: String?) -> Unit = { _, _ -> },
) {
    val delegate = checkNotNull(session.progressDelegate) {
        "GeckoEngineSession must install its ProgressDelegate before persistence binding"
    }
    if (delegate is AndroidComponentsSessionStatePersistenceDelegate) return

    val observer = AndroidComponentsSessionStatePersistenceObserver(
        sessionId = sessionId,
        persistenceState = persistenceState,
        onPersistenceStateChanged = onPersistenceStateChanged,
    )
    engineSession.register(observer)
    try {
        session.progressDelegate = AndroidComponentsSessionStatePersistenceDelegate(
            delegate = delegate,
            observer = observer,
        )
    } catch (throwable: Throwable) {
        engineSession.unregister(observer)
        throw throwable
    }
}

/** Keeps opaque persistence snapshots bounded to BrowserStore tab lifetime. */
internal fun androidComponentsSessionStatePersistenceCleanupMiddleware(
    persistenceState: AndroidComponentsSessionStatePersistenceState,
): Middleware<BrowserState, BrowserAction> = { store, next, action ->
    next(action)
    persistenceState.retain(store.state.tabs.mapTo(mutableSetOf()) { it.id })
}
