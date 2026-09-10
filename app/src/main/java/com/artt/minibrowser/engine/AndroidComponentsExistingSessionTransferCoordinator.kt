package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.store.BrowserStore
import org.mozilla.geckoview.GeckoRuntime

/**
 * Runs an irreversible existing-session transfer without allowing ownership rollback.
 *
 * Failures before [captureAndRelinquish] returns leave cleanup to the still-raw owner. Once that
 * operation returns, ownership has crossed the one-way boundary and every later failure is terminal.
 */
internal fun <Preflight, Handoff, Prepared> runIrreversibleExistingSessionTransfer(
    preflight: () -> Preflight,
    captureAndRelinquish: (Preflight) -> Handoff,
    prepare: (Preflight, Handoff) -> Prepared,
    linkAndReplay: (Prepared) -> Unit,
    terminalCleanup: (Handoff, Prepared?) -> Unit,
): Prepared {
    val checked = preflight()
    val handoff = captureAndRelinquish(checked)
    var prepared: Prepared? = null
    try {
        val preparedTransfer = prepare(checked, handoff)
        prepared = preparedTransfer
        linkAndReplay(preparedTransfer)
        return preparedTransfer
    } catch (throwable: Throwable) {
        val cleanupFailure = runCatching { terminalCleanup(handoff, prepared) }.exceptionOrNull()
        if (cleanupFailure != null && cleanupFailure !== throwable) {
            throwable.addSuppressed(cleanupFailure)
        }
        throw throwable
    }
}

/** Runs every terminal cleanup step and reports cleanup failures without skipping later steps. */
internal fun runAllTerminalCleanupSteps(vararg steps: () -> Unit) {
    var firstFailure: Throwable? = null
    steps.forEach { step ->
        try {
            step()
        } catch (throwable: Throwable) {
            val existing = firstFailure
            if (existing == null) {
                firstFailure = throwable
            } else if (existing !== throwable) {
                existing.addSuppressed(throwable)
            }
        }
    }
    firstFailure?.let { throw it }
}

/**
 * Production coordinator for moving one already-open MiniBrowser GeckoSession to A-C ownership.
 *
 * This function is intentionally not called by the current raw-owned runtime yet. It centralizes the
 * exact main-thread ordering needed by the eventual live cutover and, critically, owns terminal
 * cleanup for the interval after [Tab.captureAndroidComponentsHandoffAndRelinquish] has made raw
 * ownership irreversible.
 */
@MainThread
internal fun transferTabToAndroidComponents(
    tabManager: TabManager,
    tab: Tab,
    runtime: GeckoRuntime,
    store: BrowserStore,
    configurator: AndroidComponentsOwnedSessionConfigurator,
    compatibilityRegistry: AndroidComponentsGeckoCompatibilityRegistry,
    uiCompatibilityState: AndroidComponentsUiCompatibilityState,
    sessionStatePersistence: AndroidComponentsSessionStatePersistenceState,
): GeckoEngineSession {
    val prepared = runIrreversibleExistingSessionTransfer(
        preflight = {
            val rawSession = tab.session
            check(tab.ownsRawSession(rawSession)) {
                "Tab must still own its raw GeckoSession before Android Components transfer"
            }
            preflightAndroidComponentsExistingSessionTransfer(
                rawSession = rawSession,
                store = store,
                tabId = tab.id.toString(),
            )
        },
        captureAndRelinquish = { preflight ->
            tab.captureAndroidComponentsHandoffAndRelinquish(preflight.rawSession)
        },
        prepare = { preflight, handoff ->
            prepareAndroidComponentsExistingSessionTransferAfterRawRelinquish(
                runtime = runtime,
                preflight = preflight,
                configurator = configurator,
                compatibilityRegistry = compatibilityRegistry,
                uiCompatibilityState = uiCompatibilityState,
                uiCompatibilityHandoff = handoff.uiCompatibilityHandoff,
                mediaSessionHandoff = handoff.mediaSessionHandoff,
                sessionStatePersistence = sessionStatePersistence,
            )
        },
        linkAndReplay = { transfer ->
            transfer.transferPlan.linkAndReplayTo(store, transfer.engineSession)
        },
        terminalCleanup = { handoff, transfer ->
            terminallyCleanupFailedAndroidComponentsTransfer(
                tabManager = tabManager,
                tab = tab,
                store = store,
                uiCompatibilityState = uiCompatibilityState,
                sessionStatePersistence = sessionStatePersistence,
                rawHandoff = handoff,
                prepared = transfer,
            )
        },
    )
    return prepared.engineSession
}

/**
 * Removes a tab that crossed the relinquish boundary but failed to become a usable A-C-owned tab.
 *
 * BrowserStore removal is attempted first so EngineMiddleware gets the first opportunity to close an
 * already-linked EngineSession. If no EngineSession was linked, the prepared wrapper (if any) or the
 * exact relinquished raw GeckoSession is closed directly. TabManager then drops only its structural
 * record; its ownership guard prevents a second raw close.
 */
private fun terminallyCleanupFailedAndroidComponentsTransfer(
    tabManager: TabManager,
    tab: Tab,
    store: BrowserStore,
    uiCompatibilityState: AndroidComponentsUiCompatibilityState,
    sessionStatePersistence: AndroidComponentsSessionStatePersistenceState,
    rawHandoff: AndroidComponentsRawSessionRelinquishHandoff,
    prepared: AndroidComponentsPreparedExistingSessionTransfer?,
) {
    val sessionId = tab.id.toString()
    val linkedBeforeRemoval = store.state.tabs
        .firstOrNull { it.id == sessionId }
        ?.engineState
        ?.engineSession
    var storeRemovalFailed = false

    runAllTerminalCleanupSteps(
        { sessionStatePersistence.remove(sessionId) },
        { uiCompatibilityState.remove(sessionId) },
        {
            try {
                store.dispatch(TabListAction.RemoveTabsAction(listOf(sessionId)))
            } catch (throwable: Throwable) {
                storeRemovalFailed = true
                throw throwable
            }
        },
        {
            when {
                linkedBeforeRemoval != null -> {
                    if (storeRemovalFailed) {
                        linkedBeforeRemoval.close()
                    }
                    if (prepared != null && linkedBeforeRemoval !== prepared.engineSession) {
                        prepared.engineSession.close()
                    }
                }

                prepared != null -> prepared.engineSession.close()
                rawHandoff.rawSession.isOpen -> runAllTerminalCleanupSteps(
                    { rawHandoff.rawSession.stop() },
                    { rawHandoff.rawSession.close() },
                )
            }
        },
        { tabManager.closeTab(tab.id) },
    )
}
