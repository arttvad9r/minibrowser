package com.artt.minibrowser.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.artt.minibrowser.engine.Tab
import org.mozilla.geckoview.GeckoView

internal fun View.updateBrowserContentAccessibility(hidden: Boolean) {
    importantForAccessibility = if (hidden) {
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    } else {
        View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
}

/** Thin Compose/GeckoView bridge. Browser chrome remains independent of Gecko session objects. */
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
    val pageSettled = tab != null && tab.progress < 0f &&
        (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))

    AndroidView(
        factory = { context -> GeckoView(context) },
        update = { view ->
            view.updateBrowserContentAccessibility(hiddenFromAccessibility)
            if (view.session !== session) {
                view.releaseSession()
                session?.let(view::setSession)
            }
            previewStore.maybeCapture(
                view = view,
                tabId = tabId,
                url = url,
                isPrivate = isPrivate,
                pageSettled = pageSettled,
            )
        },
        onRelease = { view ->
            // The view only borrows the session. TabManager remains responsible for persistence and
            // closing it; releasing here prevents a disposed AndroidView from retaining the session.
            view.releaseSession()
        },
        modifier = modifier,
    )
}
