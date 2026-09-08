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
 * Produces the smallest BrowserStore action set needed to mirror the current MiniBrowser tab state.
 * Structural changes rebuild the tab list to preserve its exact order. Content changes are diffed
 * so Gecko progress callbacks do not cause unrelated BrowserStore updates.
 */
internal fun browserStoreSyncActions(
    state: BrowserState,
    tabs: List<BrowserStoreTabSnapshot>,
    selectedTabId: String?,
): List<BrowserAction> {
    val actions = mutableListOf<BrowserAction>()
    val currentStructure = state.tabs.map { it.id to it.content.private }
    val nextStructure = tabs.map { it.id to it.isPrivate }
    val structureChanged = currentStructure != nextStructure

    if (structureChanged) {
        if (state.tabs.isNotEmpty()) {
            actions += TabListAction.RemoveAllTabsAction(recoverable = false)
        }
        if (tabs.isNotEmpty()) {
            actions += TabListAction.AddMultipleTabsAction(
                tabs.map { tab ->
                    createTab(
                        url = tab.url,
                        private = tab.isPrivate,
                        id = tab.id,
                        title = tab.title,
                    )
                },
            )
            tabs.forEach { tab -> actions += fullContentActions(tab) }
        }
    } else {
        tabs.forEachIndexed { index, tab ->
            actions += changedContentActions(state.tabs[index].content, tab)
        }
    }

    val validSelectedId = selectedTabId?.takeIf { selected -> tabs.any { it.id == selected } }
    if (validSelectedId != null && (structureChanged || state.selectedTabId != validSelectedId)) {
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
