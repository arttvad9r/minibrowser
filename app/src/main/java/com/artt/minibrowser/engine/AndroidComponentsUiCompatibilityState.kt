package com.artt.minibrowser.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        update(sessionId) { current ->
            current.copy(
                securityState = securityStateForGeckoSecurityInfo(
                    isException = isException,
                    isSecure = isSecure,
                ),
            )
        }
    }

    fun onLoadError(sessionId: String, category: Int) {
        update(sessionId) { current ->
            current.copy(pageLoadError = pageLoadErrorForCategory(category))
        }
    }

    fun remove(sessionId: String) {
        if (sessionId !in mutableSnapshots.value) return
        mutableSnapshots.value = mutableSnapshots.value - sessionId
    }

    private fun update(
        sessionId: String,
        transform: (AndroidComponentsUiCompatibilitySnapshot) -> AndroidComponentsUiCompatibilitySnapshot,
    ) {
        val current = mutableSnapshots.value[sessionId] ?: AndroidComponentsUiCompatibilitySnapshot()
        set(sessionId, transform(current))
    }

    private fun set(
        sessionId: String,
        snapshot: AndroidComponentsUiCompatibilitySnapshot,
    ) {
        mutableSnapshots.value = mutableSnapshots.value + (sessionId to snapshot)
    }
}
