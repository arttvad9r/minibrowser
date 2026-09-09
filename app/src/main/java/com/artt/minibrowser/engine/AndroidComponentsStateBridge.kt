package com.artt.minibrowser.engine

import androidx.compose.runtime.snapshotFlow
import com.artt.minibrowser.data.EngineSessionStateEnvelope
import com.artt.minibrowser.data.decodeBoundEngineSessionStateEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSessionState
import kotlin.math.roundToInt

/**
 * Temporary migration snapshot used while raw GeckoSession remains the execution source of truth.
 * BrowserStore is intentionally shadow state at this stage; later Android Components features can
 * move one responsibility at a time without forcing a simultaneous TabManager rewrite.
 *
 * [mirrorsRawContent] is the one-way ownership seam: once raw session authority is relinquished,
 * structural tab/order information may still flow through this bridge, but stale raw content and
 * restore state must never overwrite or recreate Android Components-owned BrowserStore state.
 */
internal data class BrowserStoreTabSnapshot(
    val id: String,
    val url: String,
    val title: String,
    val isPrivate: Boolean,
    val desktop: Boolean,
    val progress: Int,
    val loading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val fullscreen: Boolean,
    val securityState: SecurityState,
    val mirrorsRawContent: Boolean = true,
    val persistedEngineSessionState: EngineSessionStateEnvelope? = null,
    val persistedEngineSessionStateUrl: String? = null,
)

private fun Tab.toBrowserStoreTabSnapshot(): BrowserStoreTabSnapshot {
    val loading = progress >= 0f
    val progressPercent = when {
        loading -> (progress * 100f).roundToInt().coerceIn(0, 100)
        url.isBlank() || url.equals("about:blank", ignoreCase = true) -> 0
        else -> 100
    }
    return BrowserStoreTabSnapshot(
        id = id.toString(),
        url = url,
        title = title,
        isPrivate = isPrivate,
        desktop = desktop,
        progress = progressPercent,
        loading = loading,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        fullscreen = fullscreen,
        securityState = securityState,
        persistedEngineSessionState = persistedEngineSessionState,
        persistedEngineSessionStateUrl = persistedEngineSessionStateUrl,
    )
}

/**
 * A-C 154 exposes secure/insecure/unknown but has no state for GeckoView's certificate-exception bit.
 * Preserve the coarse connection classification in BrowserStore; the Exception distinction remains
 * a MiniBrowser compatibility sidecar until the UI read-side ownership cutover is complete.
 */
internal fun SecurityState.toBrowserStoreSecurityInfo(): SecurityInfo = when (this) {
    SecurityState.Unknown -> SecurityInfo.Unknown
    SecurityState.Secure -> SecurityInfo.Secure()
    SecurityState.Insecure,
    SecurityState.Exception,
    -> SecurityInfo.Insecure()
}

private fun shouldRecreateShadowTab(
    current: TabSessionState,
    next: BrowserStoreTabSnapshot,
    desiredEngineSessionState: EngineSessionState?,
): Boolean {
    // After raw authority is relinquished this bridge must not destroy a linked/live A-C tab just
    // because its stale raw sidecar disagrees with BrowserStore metadata or restore state.
    if (!next.mirrorsRawContent) return false
    if (current.content.private != next.isPrivate) return true

    // A-C 154 has no nullable UpdateEngineSessionStateAction. While this bridge is shadow-only,
    // recreate just the affected BrowserStore tab to atomically replace or clear its persisted
    // restore state. Never use this path once a live EngineSession has been linked: TabsRemovedMiddleware
    // would then own closing it, which belongs to the later explicit ownership cutover.
    return current.engineState.engineSession == null &&
        current.engineState.engineSessionState !== desiredEngineSessionState
}

/**
 * Produces granular BrowserStore actions that mirror MiniBrowser without rebuilding unaffected tabs.
 * Structural changes remove, add and move only the tabs that actually changed, preserving existing
 * BrowserStore sessions for ordinary reorders. Content changes are diffed by tab ID so structural
 * updates do not cause unrelated content actions.
 *
 * [engineSessionStates] contains already-decoded, URL-bound A-C restore state. The shadow store may
 * hold this state before live ownership moves, but this function never dispatches CreateEngineSessionAction.
 */
internal fun browserStoreSyncActions(
    state: BrowserState,
    tabs: List<BrowserStoreTabSnapshot>,
    selectedTabId: String?,
    engineSessionStates: Map<String, EngineSessionState?> = emptyMap(),
): List<BrowserAction> {
    val actions = mutableListOf<BrowserAction>()
    val currentById = state.tabs.associateBy { it.id }
    val nextById = tabs.associateBy { it.id }

    val removedIds = state.tabs.mapNotNull { current ->
        val next = nextById[current.id]
        current.id.takeIf {
            next == null || shouldRecreateShadowTab(
                current = current,
                next = next,
                desiredEngineSessionState = engineSessionStates[next.id],
            )
        }
    }
    val removedIdSet = removedIds.toSet()
    if (removedIds.isNotEmpty()) {
        actions += TabListAction.RemoveTabsAction(removedIds)
    }

    val addedTabs = tabs.filter { next ->
        if (!next.mirrorsRawContent) return@filter false
        val current = currentById[next.id]
        current == null || shouldRecreateShadowTab(
            current = current,
            next = next,
            desiredEngineSessionState = engineSessionStates[next.id],
        )
    }
    val addedIdSet = addedTabs.mapTo(mutableSetOf()) { it.id }
    if (addedTabs.isNotEmpty()) {
        actions += TabListAction.AddMultipleTabsAction(
            addedTabs.map { tab ->
                createTab(
                    url = tab.url,
                    private = tab.isPrivate,
                    id = tab.id,
                    title = tab.title,
                    desktopMode = tab.desktop,
                    engineSessionState = engineSessionStates[tab.id],
                )
            },
        )
    }

    tabs.forEach { tab ->
        if (!tab.mirrorsRawContent) return@forEach
        if (tab.id in addedIdSet) {
            actions += fullContentActions(tab)
        } else {
            currentById[tab.id]?.let { current ->
                actions += changedContentActions(current.content, tab)
            }
        }
    }

    // RemoveTabs preserves relative order and AddMultipleTabs appends new tabs. Simulate that order,
    // then use AC's MoveTabsAction to reach MiniBrowser's exact order without destroying sessions.
    val simulatedOrder = state.tabs
        .map { it.id }
        .filterNot { it in removedIdSet }
        .toMutableList()
        .apply { addAll(addedTabs.map { it.id }) }
    val desiredStoreOrder = tabs.map { it.id }.filter(simulatedOrder::contains)

    desiredStoreOrder.forEachIndexed { targetIndex, tabId ->
        if (simulatedOrder.getOrNull(targetIndex) != tabId) {
            val currentIndex = simulatedOrder.indexOf(tabId)
            if (currentIndex >= 0) {
                val targetTabId = simulatedOrder[targetIndex]
                actions += TabListAction.MoveTabsAction(
                    tabIds = listOf(tabId),
                    targetTabId = targetTabId,
                    placeAfter = false,
                )
                simulatedOrder.removeAt(currentIndex)
                simulatedOrder.add(targetIndex, tabId)
            }
        }
    }

    val validSelectedId = selectedTabId?.takeIf(simulatedOrder::contains)
    if (
        validSelectedId != null &&
        (state.selectedTabId != validSelectedId || validSelectedId in removedIdSet)
    ) {
        actions += TabListAction.SelectTabAction(validSelectedId)
    }

    return actions
}

private fun fullContentActions(tab: BrowserStoreTabSnapshot): List<BrowserAction> = buildList {
    add(ContentAction.UpdateProgressAction(tab.id, tab.progress))
    add(ContentAction.UpdateLoadingStateAction(tab.id, tab.loading))
    add(ContentAction.UpdateBackNavigationStateAction(tab.id, tab.canGoBack))
    add(ContentAction.UpdateForwardNavigationStateAction(tab.id, tab.canGoForward))
    add(ContentAction.FullScreenChangedAction(tab.id, tab.fullscreen))
    if (tab.securityState != SecurityState.Unknown) {
        add(ContentAction.UpdateSecurityInfoAction(tab.id, tab.securityState.toBrowserStoreSecurityInfo()))
    }
}

private fun changedContentActions(
    current: ContentState,
    next: BrowserStoreTabSnapshot,
): List<BrowserAction> = buildList {
    if (current.url != next.url) add(ContentAction.UpdateUrlAction(next.id, next.url))
    if (current.title != next.title) add(ContentAction.UpdateTitleAction(next.id, next.title))
    if (current.desktopMode != next.desktop) add(ContentAction.UpdateTabDesktopMode(next.id, next.desktop))
    if (current.progress != next.progress) add(ContentAction.UpdateProgressAction(next.id, next.progress))
    if (current.loading != next.loading) add(ContentAction.UpdateLoadingStateAction(next.id, next.loading))
    if (current.canGoBack != next.canGoBack) {
        add(ContentAction.UpdateBackNavigationStateAction(next.id, next.canGoBack))
    }
    if (current.canGoForward != next.canGoForward) {
        add(ContentAction.UpdateForwardNavigationStateAction(next.id, next.canGoForward))
    }
    if (current.fullScreen != next.fullscreen) {
        add(ContentAction.FullScreenChangedAction(next.id, next.fullscreen))
    }
    val securityInfo = next.securityState.toBrowserStoreSecurityInfo()
    if (current.securityInfo != securityInfo) {
        add(ContentAction.UpdateSecurityInfoAction(next.id, securityInfo))
    }
}

private data class PersistedEngineStateKey(
    val envelope: EngineSessionStateEnvelope?,
    val stateUrl: String?,
    val tabUrl: String,
)

private data class CachedEngineSessionState(
    val key: PersistedEngineStateKey,
    val decoded: EngineSessionState?,
)

internal class AndroidComponentsStateBridge(
    private val store: BrowserStore,
    private val engine: Engine,
) {
    private val decodedEngineStates = mutableMapOf<String, CachedEngineSessionState>()

    fun sync(tabs: List<BrowserStoreTabSnapshot>, selectedTabId: String?) {
        val liveIds = tabs.mapTo(mutableSetOf()) { it.id }
        decodedEngineStates.keys.retainAll(liveIds)
        val engineSessionStates = tabs.associate { tab ->
            if (tab.mirrorsRawContent) {
                tab.id to resolveEngineSessionState(tab)
            } else {
                decodedEngineStates.remove(tab.id)
                tab.id to null
            }
        }
        browserStoreSyncActions(store.state, tabs, selectedTabId, engineSessionStates).forEach(store::dispatch)
    }

    private fun resolveEngineSessionState(tab: BrowserStoreTabSnapshot): EngineSessionState? {
        val key = PersistedEngineStateKey(
            envelope = tab.persistedEngineSessionState,
            stateUrl = tab.persistedEngineSessionStateUrl,
            tabUrl = tab.url,
        )
        decodedEngineStates[tab.id]?.takeIf { it.key == key }?.let { return it.decoded }

        val decoded = decodeBoundEngineSessionStateEnvelope(
            envelope = tab.persistedEngineSessionState,
            stateUrl = tab.persistedEngineSessionStateUrl,
            tabUrl = tab.url,
            engine = engine,
        )
        decodedEngineStates[tab.id] = CachedEngineSessionState(key, decoded)
        return decoded
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.bindTabManagerToBrowserStore(
    tabManager: TabManager,
    store: BrowserStore,
    engine: Engine,
): Job = launch {
    val bridge = AndroidComponentsStateBridge(store, engine)
    tabManager.tabs
        .flatMapLatest { tabs ->
            // _tabs only emits on structural changes. Individual Tab properties are Compose state,
            // so snapshotFlow is required to observe URL/title/progress/navigation changes too.
            snapshotFlow { tabs.map { it.toBrowserStoreTabSnapshot() } }
        }
        .combine(tabManager.currentId) { tabs, selectedId -> tabs to selectedId?.toString() }
        .distinctUntilChanged()
        .collect { (tabs, selectedId) -> bridge.sync(tabs, selectedId) }
}
