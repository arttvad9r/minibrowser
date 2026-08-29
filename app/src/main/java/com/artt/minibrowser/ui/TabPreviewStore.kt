package com.artt.minibrowser.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import org.mozilla.geckoview.GeckoView
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import kotlin.math.roundToInt

/**
 * In-process cache of real GeckoView renders for the tab switcher.
 *
 * Normal-tab previews stay memory-only and are kept in a byte-bounded LRU. Private tabs are
 * deliberately excluded even from this transient cache.
 */
object TabPreviewStore {
    private const val MAX_PREVIEW_WIDTH = 420
    private const val MAX_CACHE_BYTES = 24L * 1024L * 1024L
    private const val RETIRE_DELAY_MS = 1_000L
    private const val OVERVIEW_CAPTURE_DELAY_MS = 160L

    private val previews = mutableStateMapOf<Long, Bitmap>()
    private val lru = LinkedHashMap<Long, Unit>(16, 0.75f, true)
    private var cachedBytes = 0L
    private val lastCapturedUrl = mutableMapOf<Long, String>()
    private val inFlight = mutableSetOf<Long>()
    private val removedTabs = mutableSetOf<Long>()
    private val privateTabs = mutableSetOf<Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var generation = 0

    private var currentView = WeakReference<GeckoView>(null)
    private var currentTabId: Long? = null
    private var currentUrl: String = ""

    operator fun get(tabId: Long): Bitmap? {
        if (tabId in privateTabs || tabId in removedTabs) return null
        val bitmap = previews[tabId] ?: return null
        lru[tabId] = Unit
        return bitmap
    }

    fun attach(view: GeckoView, tabId: Long?, url: String, isPrivate: Boolean) {
        currentView = WeakReference(view)
        currentTabId = tabId
        currentUrl = url
        if (tabId == null) return

        if (tabId in removedTabs) {
            removePreview(tabId)
            lastCapturedUrl.remove(tabId)
            inFlight.remove(tabId)
            return
        }
        if (isPrivate) {
            privateTabs += tabId
            removePreview(tabId)
            lastCapturedUrl.remove(tabId)
            inFlight.remove(tabId)
        } else {
            privateTabs.remove(tabId)
        }
    }

    /**
     * Keep track of the visible GeckoView, but do not take a screenshot automatically when every
     * page finishes loading. capturePixels() is compositor work and downscaling allocates a bitmap;
     * doing that on the normal navigation path made page completion visibly hitch. A fresh frame is
     * captured only when the user actually opens the tab overview or before a session swap.
     */
    fun maybeCapture(
        view: GeckoView,
        tabId: Long?,
        url: String,
        isPrivate: Boolean,
        pageSettled: Boolean,
    ) {
        attach(view, tabId, url, isPrivate)
        if (!pageSettled) return
        // Intentionally deferred to captureCurrent()/captureBeforeSessionSwap().
    }

    /**
     * Opening the overview must not synchronously contend with a Gecko compositor readback.
     * Schedule the fresh preview just after the short overview fade; stale targets are discarded
     * if the tab/session changes in the meantime.
     */
    fun captureCurrent() {
        val view = currentView.get() ?: return
        val id = currentTabId ?: return
        val url = currentUrl
        if (id in privateTabs || id in removedTabs || !isPreviewableUrl(url)) return
        val expectedGeneration = generation

        mainHandler.postDelayed({
            if (
                generation != expectedGeneration ||
                currentView.get() !== view ||
                currentTabId != id ||
                currentUrl != url ||
                id in privateTabs ||
                id in removedTabs
            ) return@postDelayed
            capture(view, id, url)
        }, OVERVIEW_CAPTURE_DELAY_MS)
    }

    /** Called before GeckoView releases the old session so its last visible frame is not lost. */
    fun captureBeforeSessionSwap(view: GeckoView) {
        if (currentView.get() !== view) return
        val id = currentTabId ?: return
        val url = currentUrl
        if (id in privateTabs || id in removedTabs || !isPreviewableUrl(url)) return
        capture(view, id, url)
    }

    fun remove(tabId: Long) {
        removedTabs += tabId
        privateTabs.remove(tabId)
        removePreview(tabId)
        lastCapturedUrl.remove(tabId)
        inFlight.remove(tabId)
        if (currentTabId == tabId) {
            currentTabId = null
            currentUrl = ""
        }
    }

    /**
     * Drops every preview and invalidates callbacks from captures that started before this call.
     * Known old tab ids stay blocked for the rest of this process so a final old-UI Compose pass
     * cannot start a fresh post-clear capture before TabManager finishes closing those sessions.
     */
    fun clear() {
        generation++
        removedTabs += previews.keys
        removedTabs += lastCapturedUrl.keys
        removedTabs += inFlight
        removedTabs += privateTabs
        currentTabId?.let(removedTabs::add)
        val retired = previews.values.toList()
        previews.clear()
        lru.clear()
        cachedBytes = 0L
        lastCapturedUrl.clear()
        inFlight.clear()
        privateTabs.clear()
        currentView = WeakReference(null)
        currentTabId = null
        currentUrl = ""
        retired.forEach(::retire)
    }

    private fun capture(view: GeckoView, tabId: Long, url: String) {
        if (tabId in privateTabs || tabId in removedTabs || !inFlight.add(tabId)) return
        val expectedGeneration = generation
        runCatching {
            view.capturePixels().accept(
                { source ->
                    mainHandler.post {
                        inFlight.remove(tabId)
                        if (expectedGeneration != generation) {
                            source?.let(::retire)
                            return@post
                        }
                        if (source != null && source.width > 0 && source.height > 0) {
                            if (tabId in privateTabs || tabId in removedTabs) {
                                retire(source)
                            } else {
                                putPreview(tabId, downscale(source))
                                lastCapturedUrl[tabId] = url
                            }
                        }
                    }
                },
                {
                    mainHandler.post {
                        inFlight.remove(tabId)
                    }
                },
            )
        }.onFailure { inFlight.remove(tabId) }
    }

    private fun putPreview(tabId: Long, bitmap: Bitmap) {
        val previous = previews.put(tabId, bitmap)
        if (previous !== bitmap) {
            previous?.let {
                cachedBytes -= it.byteCount.toLong()
                retire(it)
            }
            cachedBytes += bitmap.byteCount.toLong()
        }
        lru[tabId] = Unit
        trimCache()
    }

    private fun removePreview(tabId: Long) {
        lru.remove(tabId)
        previews.remove(tabId)?.let {
            cachedBytes = (cachedBytes - it.byteCount.toLong()).coerceAtLeast(0L)
            retire(it)
        }
    }

    private fun trimCache() {
        while (cachedBytes > MAX_CACHE_BYTES && lru.isNotEmpty()) {
            val eldest = lru.entries.iterator().next().key
            removePreview(eldest)
        }
    }

    /** Crossfade may draw the old bitmap for a few frames, so recycle only after it has left state. */
    private fun retire(bitmap: Bitmap) {
        mainHandler.postDelayed({
            val stillReferenced = previews.values.any { it === bitmap }
            if (!stillReferenced && !bitmap.isRecycled) bitmap.recycle()
        }, RETIRE_DELAY_MS)
    }

    private fun downscale(source: Bitmap): Bitmap {
        if (source.width <= MAX_PREVIEW_WIDTH) return source
        val ratio = MAX_PREVIEW_WIDTH.toFloat() / source.width.toFloat()
        val height = (source.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, MAX_PREVIEW_WIDTH, height, true)
        if (scaled !== source) retire(source)
        return scaled
    }

    private fun isPreviewableUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}
