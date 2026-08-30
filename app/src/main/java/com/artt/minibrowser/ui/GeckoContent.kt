package com.artt.minibrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.artt.minibrowser.engine.Tab
import org.mozilla.geckoview.GeckoView

/** Thin Compose/GeckoView bridge. Browser chrome remains independent of Gecko session objects. */
@Composable
internal fun GeckoContent(
    tab: Tab?,
    modifier: Modifier = Modifier,
) {
    val session = tab?.session
    val tabId = tab?.id
    val url = tab?.url.orEmpty()
    val isPrivate = tab?.isPrivate == true
    val pageSettled = tab != null && tab.progress < 0f &&
        (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))

    AndroidView(
        factory = { context -> GeckoView(context) },
        update = { view ->
            if (view.session !== session) {
                TabPreviewStore.captureBeforeSessionSwap(view)
                view.releaseSession()
                session?.let(view::setSession)
            }
            TabPreviewStore.maybeCapture(
                view = view,
                tabId = tabId,
                url = url,
                isPrivate = isPrivate,
                pageSettled = pageSettled,
            )
        },
        modifier = modifier,
    )
}
