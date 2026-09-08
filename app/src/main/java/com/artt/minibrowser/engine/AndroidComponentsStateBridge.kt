package com.artt.minibrowser.engine

import androidx.compose.runtime.snapshotFlow
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
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import kotlin.math.roundToInt

/**
 * Temporary migration snapshot used while raw GeckoSession remains the execution source of truth.
 * BrowserStore is intentionally shadow state at this stage; later Android Components features can
 * move one responsibility at a time without forcing a simultaneous TabManager rewrite.
 *
 * Desktop mode deliberately remains owned by TabManager for now. Android Components 154 exposes
 * ContentState.desktopMode but no public BrowserAction that can update it. Rebuilding a tab only to
 * mirror that bit would discard granular state and undermine the purpose of this bridge.
 */
internal data class BrowserStoreTabSnapshot(
    val id: String,
    val url: String,
    val title: String,
    val isPrivate: Boolean,
    val progress: Int,
    val loading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val fullscreen: Boolean,
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
        progress = progressPercent,
        loading = loading,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        fullscreen = fullscreen,
    )
}

/**
 * Produces granular BrowserStore actions that mirror MiniBrowser without rebuilding unaffected tabs.
 * Structural changes remove, add and move only the tabs that actually changed, preserving existing
 * BrowserStore sessions for ordinary reorders. Content changes are diffed by tab ID so structural
 * updates do not cause unrelated content actions.
 */
internal fun browserStoreSyncActions(
    state: BrowserState,
    tabs: List<BrowserStoreTabSnapshot>,
    selectedTabId: String?,
): List<BrowserAction> {
    val actions = mutableListOf<BrowserAction>()
    val currentById = state.tabs.associateBy { it.id }
    val nextById = tabs.associateBy { it.id }

    // Privacy is part of immutable tab creation state in this migration bridge. If it ever changes
    // for an existing ID, recreate only that tab rather than rebuilding the complete BrowserStore.
    val removedIds = state.tabs.mapNotNull { current ->
        val next = nextById[current.id]
        current.id.takeIf { next == null || next.isPrivate != current.content.private }
    }
    val removedIdSet = removedIds.toSet()
    if (removedIds.isNotEmpty()) {
        actions += TabListAction.RemoveTabsAction(removedIds)
    }

    val addedTabs = tabs.filter { next ->
        val current = currentById[next.id]
        current == null || current.content.private != next.isPrivate
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
                )
            },
        )
    }

    tabs.forEach { tab ->
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

    tabs.forEachIndexed { targetIndex, tab ->
        if (simulatedOrder.getOrNull(targetIndex) != tab.id) {
            val currentIndex = simulatedOrder.indexOf(tab.id)
            if (currentIndex >= 0) {
                val targetTabId = simulatedOrder[targetIndex]
                actions += TabListAction.MoveTabsAction(
                    tabIds = listOf(tab.id),
                    targetTabId = targetTabId,
                    placeAfter = false,
                )
                simulatedOrder.removeAt(currentIndex)
                simulatedOrder.add(targetIndex, tab.id)
            }
        }
    }

    val validSelectedId = selectedTabId?.takeIf { selected -> tabs.any { it.id == selected } }
    if (
        validSelectedId != null &&
        (state.selectedTabId != validSelectedId || validSelectedId in removedIdSet)
    ) {
        actions += TabListAction.SelectTabAction(validSelectedId)
    }

    return actions
}

private fun fullContentActions(tab: BrowserStoreTabSnapshot): List<BrowserAction> = listOf(
    ContentAction.UpdateProgressAction(tab.id, tab.progress),
    ContentAction.UpdateLoadingStateAction(tab.id, tab.loading),
    ContentAction.UpdateBackNavigationStateAction(tab.id, tab.canGoBack),
    ContentAction.UpdateForwardNavigationStateAction(tab.id, tab.canGoForward),
    ContentAction.FullScreenChangedAction(tab.id, tab.fullscreen),
)

private fun changedContentActions(
    current: ContentState,
    next: BrowserStoreTabSnapshot,
): List<BrowserAction> = buildList {
    if (current.url != next.url) add(ContentAction.UpdateUrlAction(next.id, next.url))
    if (current.title != next.title) add(ContentAction.UpdateTitleAction(next.id, next.title))
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
}

internal class AndroidComponentsStateBridge(
    private val store: BrowserStore,
) {
    fun sync(tabs: List<BrowserStoreTabSnapshot>, selectedTabId: String?) {
        browserStoreSyncActions(store.state, tabs, selectedTabId).forEach(store::dispatch)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun CoroutineScope.bindTabManagerToBrowserStore(
    tabManager: TabManager,
    store: BrowserStore,
): Job = launch {
    val bridge = AndroidComponentsStateBridge(store)
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
