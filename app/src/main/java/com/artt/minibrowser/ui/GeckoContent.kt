package com.artt.minibrowser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    var pullFraction by remember(tabId) { mutableFloatStateOf(0f) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                PullToRefreshGeckoView(context).apply {
                    // GeckoView exposes the web form as a virtual Android Autofill structure so the
                    // user's system provider (Bitwarden, 1Password, Google Password Manager, etc.)
                    // can fill credentials without Minibrowser storing them itself.
                    setAutofillEnabled(true)
                }
            },
            update = { view ->
                view.updateBrowserContentAccessibility(hiddenFromAccessibility)
                if (view.session !== session) {
                    view.trackScrollFor(null)
                    view.releaseSession()
                    session?.let { nextSession ->
                        // Gecko does not install a text-selection action mode for embedders by
                        // default. The built-in delegate supplies Select all / Copy / Cut / Paste /
                        // Process text using Android's standard contextual toolbar.
                        if (nextSession.selectionActionDelegate == null) {
                            view.context.findActivity()?.let { activity ->
                                nextSession.setSelectionActionDelegate(BasicSelectionActionDelegate(activity))
                            }
                        }
                        view.setSession(nextSession)
                        view.trackScrollFor(nextSession)
                    }
                }
                view.configurePullToRefresh(
                    enabled = pageSettled,
                    onProgress = { progress -> pullFraction = progress },
                    onRefresh = {
                        if (pageSettled) session?.reload()
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
            onRelease = { view ->
                // The view only borrows the session. TabManager remains responsible for persistence and
                // closing it; releasing here prevents a disposed AndroidView from retaining the session.
                view.clearPullToRefresh()
                view.releaseSession()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (pullFraction > 0f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .offset(y = (pullFraction.coerceIn(0f, 1f) * 12f).dp)
                    .size(40.dp),
                shape = CircleShape,
                tonalElevation = 3.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { pullFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
