package com.artt.minibrowser.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import org.mozilla.geckoview.GeckoView
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * In-process cache of real GeckoView renders for the tab switcher.
 *
 * Normal-tab previews stay memory-only. Private tabs are deliberately excluded even from this
 * transient cache: otherwise opening the overview from a normal tab could expose private-page
 * pixels to a screenshot despite FLAG_SECURE being enabled while the private tab itself is active.
 */
object TabPreviewStore {
    private const val MAX_PREVIEW_WIDTH = 420

    private val previews = mutableStateMapOf<Long, Bitmap>()
    private val lastCapturedUrl = mutableMapOf<Long, String>()
    private val inFlight = mutableSetOf<Long>()
    private val removedTabs = mutableSetOf<Long>()
    private val privateTabs = mutableSetOf<Long>()

    private var currentView = WeakReference<GeckoView>(null)
    private var currentTabId: Long? = null
    private var currentUrl: String = ""

    operator fun get(tabId: Long): Bitmap? =
        if (tabId in privateTabs) null else previews[tabId]

    fun attach(view: GeckoView, tabId: Long?, url: String, isPrivate: Boolean) {
        currentView = WeakReference(view)
        currentTabId = tabId
        currentUrl = url
        if (tabId == null) return

        removedTabs.remove(tabId)
        if (isPrivate) {
            privateTabs += tabId
            previews.remove(tabId)
            lastCapturedUrl.remove(tabId)
            inFlight.remove(tabId)
        } else {
            privateTabs.remove(tabId)
        }
    }

    /** Capture once after a URL has settled; opening/switching tabs can force a fresher frame. */
    fun maybeCapture(
        view: GeckoView,
        tabId: Long?,
        url: String,
        isPrivate: Boolean,
        pageSettled: Boolean,
    ) {
        attach(view, tabId, url, isPrivate)
        val id = tabId ?: return
        if (isPrivate || !pageSettled || !isPreviewableUrl(url) || lastCapturedUrl[id] == url) return
        capture(view, id, url)
    }

    fun captureCurrent() {
        val view = currentView.get() ?: return
        val id = currentTabId ?: return
        if (id in privateTabs || !isPreviewableUrl(currentUrl)) return
        capture(view, id, currentUrl)
    }

    /** Called before GeckoView releases the old session so its last visible frame is not lost. */
    fun captureBeforeSessionSwap(view: GeckoView) {
        if (currentView.get() === view) captureCurrent()
    }

    fun remove(tabId: Long) {
        removedTabs += tabId
        privateTabs.remove(tabId)
        previews.remove(tabId)
        lastCapturedUrl.remove(tabId)
        inFlight.remove(tabId)
        if (currentTabId == tabId) {
            currentTabId = null
            currentUrl = ""
        }
    }

    private fun capture(view: GeckoView, tabId: Long, url: String) {
        if (tabId in privateTabs || tabId in removedTabs || !inFlight.add(tabId)) return
        runCatching {
            view.capturePixels().accept(
                { source ->
                    inFlight.remove(tabId)
                    if (source != null && source.width > 0 && source.height > 0) {
                        if (tabId in privateTabs || tabId in removedTabs) {
                            if (!source.isRecycled) source.recycle()
                        } else {
                            // Do not recycle a replaced preview here: Crossfade may still draw it
                            // for a few frames after the state map changes.
                            previews[tabId] = downscale(source)
                            lastCapturedUrl[tabId] = url
                        }
                    }
                },
                {
                    // A compositor that is not ready is a normal transient state; fallback UI stays.
                    inFlight.remove(tabId)
                },
            )
        }.onFailure { inFlight.remove(tabId) }
    }

    private fun downscale(source: Bitmap): Bitmap {
        if (source.width <= MAX_PREVIEW_WIDTH) return source
        val ratio = MAX_PREVIEW_WIDTH.toFloat() / source.width.toFloat()
        val height = (source.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, MAX_PREVIEW_WIDTH, height, true)
        if (scaled !== source && !source.isRecycled) source.recycle()
        return scaled
    }

    private fun isPreviewableUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}
