package com.artt.minibrowser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.BrowserCommandTarget
import com.artt.minibrowser.engine.Tab
import com.artt.minibrowser.engine.browserCommandTargetForTab
import com.artt.minibrowser.engine.reloadOrStopBrowser
import org.mozilla.geckoview.BasicSelectionActionDelegate

internal fun View.updateBrowserContentAccessibility(hidden: Boolean) {
    importantForAccessibility = if (hidden) {
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    } else {
        View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Thin Compose/EngineView bridge. Browser chrome remains independent of Gecko session objects. */
@Composable
internal fun GeckoContent(
    tab: Tab?,
    previewStore: TabPreviewStore,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as BrowserApp
    val browserStoreState by app.browserStore.stateFlow.collectAsStateWithLifecycle()
    val tabId = tab?.id
    val storeContent = tabId?.toString()?.let { id ->
        browserStoreState.tabs.firstOrNull { it.id == id }?.content
    }
    val rawTab = tab?.takeIf { it.hasRawSessionAuthority }
    val url = storeContent?.url ?: rawTab?.url.orEmpty()
    val isPrivate = storeContent?.private ?: (rawTab?.isPrivate == true)
    val pageSupportsRefresh =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
    val pageLoading = storeContent?.loading ?: ((rawTab?.progress ?: -1f) >= 0f)
    val pageSettled = pageSupportsRefresh && !pageLoading
    val hiddenFromAccessibility = LocalBrowserContentAccessibilityHidden.current
    val indicatorColor = MaterialTheme.colorScheme.primary.toArgb()
    val indicatorBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()

    AndroidView(
        factory = { context -> BrowserSwipeRefreshLayout(context) },
        update = { container ->
            val view = container.engineView
            view.updateBrowserContentAccessibility(hiddenFromAccessibility)
            val renderTarget = tab?.let { currentTab ->
                browserCommandTargetForTab(currentTab, app.browserStore)
            }
            if (renderTarget is BrowserCommandTarget.Raw) {
                val rawOwnedTab = requireNotNull(tab) { "Raw render target requires a tab" }
                val rawSession = renderTarget.session
                // Gecko does not install a text-selection action mode for raw embedders by default.
                // Never mutate the raw session after ownership has been relinquished.
                if (rawOwnedTab.ownsRawSession(rawSession) && rawSession.selectionActionDelegate == null) {
                    view.context.findActivity()?.let { activity ->
                        rawSession.setSelectionActionDelegate(BasicSelectionActionDelegate(activity))
                    }
                }
            }
            container.bindSession(
                runtime = app.runtime,
                store = app.browserStore,
                tabId = tabId?.toString(),
                target = renderTarget,
                privateMode = isPrivate,
            )
            container.configurePullToRefresh(
                pageSupportsRefresh = pageSupportsRefresh,
                pageLoading = pageLoading,
                indicatorColor = indicatorColor,
                indicatorBackgroundColor = indicatorBackgroundColor,
                onRefresh = {
                    tab?.let { currentTab ->
                        if (pageSupportsRefresh) {
                            reloadOrStopBrowser(
                                tab = currentTab,
                                browserStore = app.browserStore,
                                isLoading = false,
                            )
                        }
                    }
                },
            )
            previewStore.maybeCapture(
                view = view,
                tabId = tabId,
                url = url,
                isPrivate = isPrivate,
                pageSettled = pageSettled,
            )
        },
        onRelease = { container -> container.clearPullToRefresh() },
        modifier = modifier,
    )
}