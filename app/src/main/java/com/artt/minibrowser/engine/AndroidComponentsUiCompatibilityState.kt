package com.artt.minibrowser.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware

/**
 * MiniBrowser UI state that A-C 154 BrowserStore cannot represent losslessly.
 *
 * The store is keyed by BrowserStore session id so it remains valid after Gecko delegate authority
 * moves away from TabManager. Raw ownership seeds a handoff immediately before cutover; selective
 * Gecko delegate wrappers then keep the same state current without retaining an Activity or Tab.
 */
internal data class AndroidComponentsUiCompatibilitySnapshot(
    val securityState: SecurityState = SecurityState.Unknown,
    val pageLoadError: PageLoadError? = null,
)

/** Snapshot captured while the raw owner still has authoritative Gecko callback state. */
internal data class AndroidComponentsUiCompatibilityHandoff(
    val securityState: SecurityState,
    val pageLoadError: PageLoadError?,
)

internal fun Tab.androidComponentsUiCompatibilityHandoff(): AndroidComponentsUiCompatibilityHandoff =
    AndroidComponentsUiCompatibilityHandoff(
        securityState = securityState,
        pageLoadError = loadError,
    )

/** Matches the precedence used by TabManager's current Gecko ProgressDelegate. */
internal fun securityStateForGeckoSecurityInfo(
    isException: Boolean,
    isSecure: Boolean,
): SecurityState = when {
    isException -> SecurityState.Exception
    isSecure -> SecurityState.Secure
    else -> SecurityState.Insecure
}

/** Uses takeover-only compatibility state when present, otherwise preserves the raw-owner UI path. */
internal fun effectiveUiSecurityState(
    compatibility: AndroidComponentsUiCompatibilitySnapshot?,
    raw: SecurityState?,
): SecurityState = compatibility?.securityState ?: raw ?: SecurityState.Unknown

/**
 * A present compatibility snapshot is authoritative even when its error is null: page start uses
 * null to clear a previous raw error, so Elvis fallback would incorrectly resurrect stale UI state.
 */
internal fun effectiveUiPageLoadError(
    compatibility: AndroidComponentsUiCompatibilitySnapshot?,
    raw: PageLoadError?,
): PageLoadError? = if (compatibility != null) compatibility.pageLoadError else raw

internal class AndroidComponentsUiCompatibilityState {
    private val mutableSnapshots =
        MutableStateFlow<Map<String, AndroidComponentsUiCompatibilitySnapshot>>(emptyMap())

    val snapshots: StateFlow<Map<String, AndroidComponentsUiCompatibilitySnapshot>> =
        mutableSnapshots.asStateFlow()

    fun snapshot(sessionId: String): AndroidComponentsUiCompatibilitySnapshot? =
        mutableSnapshots.value[sessionId]

    fun seed(
        sessionId: String,
        handoff: AndroidComponentsUiCompatibilityHandoff,
    ) {
        set(
            sessionId = sessionId,
            snapshot = AndroidComponentsUiCompatibilitySnapshot(
                securityState = handoff.securityState,
                pageLoadError = handoff.pageLoadError,
            ),
        )
    }

    /** Mirrors raw onPageStart: a new document clears both the old error and old security result. */
    fun onPageStart(sessionId: String) {
        set(sessionId, AndroidComponentsUiCompatibilitySnapshot())
    }

    fun onSecurityChange(
        sessionId: String,
        isException: Boolean,
        isSecure: Boolean,
    ) {
        updateSnapshot(sessionId) { current ->
            current.copy(
                securityState = securityStateForGeckoSecurityInfo(
                    isException = isException,
                    isSecure = isSecure,
                ),
            )
        }
    }

    fun onLoadError(sessionId: String, category: Int) {
        updateSnapshot(sessionId) { current ->
            current.copy(pageLoadError = pageLoadErrorForCategory(category))
        }
    }

    fun remove(sessionId: String) {
        mutableSnapshots.update { current -> current - sessionId }
    }

    /** Keeps sidecar lifetime aligned with BrowserStore tab lifetime without enumerating remove actions. */
    fun retain(sessionIds: Set<String>) {
        mutableSnapshots.update { current ->
            if (current.keys.all(sessionIds::contains)) {
                current
            } else {
                current.filterKeys(sessionIds::contains)
            }
        }
    }

    private fun updateSnapshot(
        sessionId: String,
        transform: (AndroidComponentsUiCompatibilitySnapshot) -> AndroidComponentsUiCompatibilitySnapshot,
    ) {
        mutableSnapshots.update { current ->
            val snapshot = current[sessionId] ?: AndroidComponentsUiCompatibilitySnapshot()
            current + (sessionId to transform(snapshot))
        }
    }

    private fun set(
        sessionId: String,
        snapshot: AndroidComponentsUiCompatibilitySnapshot,
    ) {
        mutableSnapshots.update { current -> current + (sessionId to snapshot) }
    }
}

/**
 * Prunes compatibility entries only after the BrowserStore reducer/middleware chain has processed
 * an action. Store.dispatch is synchronous in A-C 154, so the post-[next] state is the authoritative
 * live-tab set and covers single, bulk and future removal action variants uniformly.
 */
internal fun androidComponentsUiCompatibilityCleanupMiddleware(
    compatibilityState: AndroidComponentsUiCompatibilityState,
): Middleware<BrowserState, BrowserAction> = { store, next, action ->
    next(action)
    compatibilityState.retain(store.state.tabs.mapTo(mutableSetOf()) { it.id })
}
