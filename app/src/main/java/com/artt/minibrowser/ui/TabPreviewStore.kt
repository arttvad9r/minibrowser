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
 * deliberately excluded even from this transient cache: otherwise opening the overview from a
 * normal tab could expose private-page pixels to a screenshot despite FLAG_SECURE being enabled
 * while the private tab itself is active.
 */
object TabPreviewStore {
    private const val MAX_PREVIEW_WIDTH = 420
    private const val MAX_CACHE_BYTES = 24L * 1024L * 1024L
    private const val RETIRE_DELAY_MS = 1_000L

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
        if (tabId in privateTabs) return null
        val bitmap = previews[tabId] ?: return null
        lru[tabId] = Unit
        return bitmap
    }

    fun attach(view: GeckoView, tabId: Long?, url: String, isPrivate: Boolean) {
        currentView = WeakReference(view)
        currentTabId = tabId
        currentUrl = url
        if (tabId == null) return

        removedTabs.remove(tabId)
        if (isPrivate) {
            privateTabs += tabId
            removePreview(tabId)
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
     * This is used by browsing-data clearing so page pixels cannot re-enter memory after the UI
     * has already reported the data as cleared.
     */
    fun clear() {
        generation++
        val retired = previews.values.toList()
        previews.clear()
        lru.clear()
        cachedBytes = 0L
        lastCapturedUrl.clear()
        inFlight.clear()
        removedTabs.clear()
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
                        // A compositor that is not ready is a normal transient state; fallback UI stays.
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
