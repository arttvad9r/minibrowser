package com.artt.minibrowser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.Tab
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
    val session = tab?.session
    val tabId = tab?.id
    val url = tab?.url.orEmpty()
    val isPrivate = tab?.isPrivate == true
    val hiddenFromAccessibility = LocalBrowserContentAccessibilityHidden.current
    val pageSupportsRefresh = tab != null &&
        (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))
    val pageLoading = tab != null && tab.progress >= 0f
    val pageSettled = pageSupportsRefresh && !pageLoading
    val indicatorColor = MaterialTheme.colorScheme.primary.toArgb()
    val indicatorBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()

    AndroidView(
        factory = { context -> BrowserSwipeRefreshLayout(context) },
        update = { container ->
            val view = container.engineView
            view.updateBrowserContentAccessibility(hiddenFromAccessibility)
            session?.let { nextSession ->
                // Gecko does not install a text-selection action mode for embedders by default.
                // Keep Android's standard contextual toolbar until selection is migrated to an
                // Android Components SelectionActionDelegate.
                if (nextSession.selectionActionDelegate == null) {
                    view.context.findActivity()?.let { activity ->
                        nextSession.setSelectionActionDelegate(BasicSelectionActionDelegate(activity))
                    }
                }
            }
            val app = view.context.applicationContext as BrowserApp
            container.bindSession(
                runtime = app.runtime,
                session = session,
                privateMode = isPrivate,
            )
            container.configurePullToRefresh(
                pageSupportsRefresh = pageSupportsRefresh,
                pageLoading = pageLoading,
                indicatorColor = indicatorColor,
                indicatorBackgroundColor = indicatorBackgroundColor,
                onRefresh = {
                    if (pageSupportsRefresh) session?.reload()
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
