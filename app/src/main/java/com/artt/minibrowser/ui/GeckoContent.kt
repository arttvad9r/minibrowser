@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.artt.minibrowser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import com.artt.minibrowser.engine.Tab
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoView

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

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = {
            if (pageSettled) session?.reload()
        },
        modifier = modifier,
    ) {
        AndroidView(
            factory = { context ->
                GeckoView(context).apply {
                    // GeckoView exposes the web form as a virtual Android Autofill structure so the
                    // user's system provider (Bitwarden, 1Password, Google Password Manager, etc.)
                    // can fill credentials without Minibrowser storing them itself.
                    setAutofillEnabled(true)
                    // AndroidView can forward unconsumed scroll deltas to the Compose parent only
                    // when nested scrolling is enabled on the hosted View. PullToRefreshBox then
                    // receives the downward overscroll after Gecko has reached the top of the page.
                    ViewCompat.setNestedScrollingEnabled(this, true)
                }
            },
            update = { view ->
                view.updateBrowserContentAccessibility(hiddenFromAccessibility)
                if (view.session !== session) {
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
                    }
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
            modifier = Modifier.matchParentSize(),
        )
    }
}
