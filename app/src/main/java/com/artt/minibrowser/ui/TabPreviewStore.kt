package com.artt.minibrowser.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import org.mozilla.geckoview.GeckoView
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * In-process cache of real GeckoView renders for the tab switcher.
 *
 * Normal-tab previews stay memory-only and are kept in a byte-bounded LRU. Private tabs are
 * deliberately excluded even from this transient cache.
 */
internal class TabPreviewStore {
    private companion object {
        const val MAX_PREVIEW_WIDTH = 320
        const val DEFAULT_MAX_CACHE_BYTES = 16L * 1024L * 1024L
        const val DEFAULT_BACKGROUND_CACHE_BYTES = 4L * 1024L * 1024L
        const val RETIRE_DELAY_MS = 1_000L
        const val OVERVIEW_CAPTURE_DELAY_MS = 420L
    }

    private data class DeferredPreview(val bitmap: Bitmap, val url: String)

    private val previews = mutableStateMapOf<Long, Bitmap>()
    private val lru = LinkedHashMap<Long, Unit>(16, 0.75f, true)
    private var cachedBytes = 0L
    private var maxCacheBytes = DEFAULT_MAX_CACHE_BYTES
    private var backgroundCacheBytes = DEFAULT_BACKGROUND_CACHE_BYTES
    private val lastCapturedUrl = mutableMapOf<Long, String>()
    private val inFlight = mutableSetOf<Long>()
    private val removedTabs = mutableSetOf<Long>()
    private val privateTabs = mutableSetOf<Long>()
    private val deferredPreviews = mutableMapOf<Long, DeferredPreview>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scaleExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "minibrowser-tab-preview-scale").apply { isDaemon = true }
    }
    private var generation = 0
    private var overviewVisible = false

    private var hostView = WeakReference<GeckoView>(null)
    private var currentView = WeakReference<GeckoView>(null)
    private var currentTabId: Long? = null
    private var currentUrl: String = ""

    operator fun get(tabId: Long): Bitmap? {
        if (tabId in privateTabs || tabId in removedTabs) return null
        val bitmap = previews[tabId] ?: return null
        lru[tabId] = Unit
        return bitmap
    }

    /** Configure byte budgets from the device capability profile. */
    fun configureMemoryPolicy(maxBytes: Long, backgroundBytes: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { configureMemoryPolicy(maxBytes, backgroundBytes) }
            return
        }
        maxCacheBytes = maxBytes.coerceAtLeast(0L)
        backgroundCacheBytes = backgroundBytes.coerceIn(0L, maxCacheBytes)
        trimCache(maxCacheBytes)
    }

    /**
     * Release reconstructible UI memory without changing logical tabs. UI-hidden trims keep only a
     * small warm preview set; background/low-memory trims drop every preview and invalidate pending
     * compositor readbacks so they cannot immediately allocate the memory again.
     */
    fun trimMemory(aggressive: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { trimMemory(aggressive) }
            return
        }
        generation++
        deferredPreviews.values.map { it.bitmap }.forEach(::retire)
        deferredPreviews.clear()
        inFlight.clear()
        if (aggressive) {
            previews.keys.toList().forEach(::removePreview)
        } else {
            trimCache(backgroundCacheBytes)
        }
    }

    /**
     * Keep newly captured pixels out of a currently visible overview. A preview that appears halfway
     * through the user's glance reads as a card jump even if the capture itself is fast. Deferred
     * previews are published after the overview leaves composition and are ready on the next open.
     */
    fun setOverviewVisible(visible: Boolean) {
        overviewVisible = visible
        if (!visible) flushDeferredPreviews()
    }

    fun attach(view: GeckoView, tabId: Long?, url: String, isPrivate: Boolean) {
        val previousHost = hostView.get()
        if (previousHost !== view) {
            // Tab ids are monotonic only inside one TabManager. A recreated Activity can restore a
            // lower max id and later reuse a tombstoned id from the previous host. A new GeckoView
            // marks that host boundary. Invalidate every old compositor callback before releasing
            // the tombstones so a late bitmap cannot be published into a reused id.
            generation++
            inFlight.clear()
            removedTabs.clear()
            hostView = WeakReference(view)
        }
        currentView = WeakReference(view)
        currentTabId = tabId
        currentUrl = url
        if (tabId == null) return

        if (tabId in removedTabs) {
            removePreview(tabId)
            removeDeferredPreview(tabId)
            lastCapturedUrl.remove(tabId)
            inFlight.remove(tabId)
            return
        }
        if (isPrivate) {
            privateTabs += tabId
            removePreview(tabId)
            removeDeferredPreview(tabId)
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
     * captured only when the user actually opens the tab overview.
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
        // Intentionally deferred to captureCurrent().
    }

    /**
     * Reuse the existing preview while the tab remains on the same URL. A compositor readback is
     * only needed when the page URL changed or there is no preview yet, and it is scheduled well
     * after the overview reveal so it cannot land in the transition's critical frames.
     */
    fun captureCurrent() {
        val view = currentView.get() ?: return
        val id = currentTabId ?: return
        val url = currentUrl
        if (id in privateTabs || id in removedTabs || !isPreviewableUrl(url)) return

        val cached = previews[id]
        if (lastCapturedUrl[id] == url && cached != null && !cached.isRecycled) {
            lru[id] = Unit
            return
        }

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

    /** Session swaps are latency-critical; never read back the old compositor here. */
    fun captureBeforeSessionSwap(view: GeckoView) {
        if (currentView.get() !== view) return
        // Deliberately no-op.
    }

    fun remove(tabId: Long) {
        removedTabs += tabId
        privateTabs.remove(tabId)
        removePreview(tabId)
        removeDeferredPreview(tabId)
        lastCapturedUrl.remove(tabId)
        inFlight.remove(tabId)
        if (currentTabId == tabId) {
            currentTabId = null
            currentUrl = ""
        }
    }

    /**
     * Drops every preview and invalidates callbacks from captures that started before this call.
     * Known old tab ids stay blocked for the lifetime of the current GeckoView host so a final old-UI
     * Compose pass cannot start a fresh post-clear capture before TabManager closes those sessions.
     */
    fun clear() {
        generation++
        removedTabs += previews.keys
        removedTabs += deferredPreviews.keys
        removedTabs += lastCapturedUrl.keys
        removedTabs += inFlight
        removedTabs += privateTabs
        currentTabId?.let(removedTabs::add)
        val retired = previews.values.toList() + deferredPreviews.values.map { it.bitmap }
        previews.clear()
        deferredPreviews.clear()
        lru.clear()
        cachedBytes = 0L
        lastCapturedUrl.clear()
        inFlight.clear()
        privateTabs.clear()
        overviewVisible = false
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
                        if (expectedGeneration != generation) {
                            inFlight.remove(tabId)
                            source?.let(::retire)
                            return@post
                        }
                        if (source == null || source.width <= 0 || source.height <= 0) {
                            inFlight.remove(tabId)
                            source?.let(::retire)
                            return@post
                        }
                        if (tabId in privateTabs || tabId in removedTabs) {
                            inFlight.remove(tabId)
                            retire(source)
                            return@post
                        }

                        // Bitmap scaling is CPU/allocation work. Do not perform it on the main thread
                        // while the user may be scrolling or selecting a card in the overview.
                        scaleExecutor.execute {
                            val scaled = runCatching { downscale(source) }.getOrElse {
                                retire(source)
                                null
                            }
                            mainHandler.post {
                                inFlight.remove(tabId)
                                if (scaled == null) return@post
                                if (
                                    expectedGeneration != generation ||
                                    tabId in privateTabs ||
                                    tabId in removedTabs
                                ) {
                                    retire(scaled)
                                } else {
                                    publishOrDefer(tabId, scaled, url)
                                }
                            }
                        }
                    }
                },
                {
                    mainHandler.post { inFlight.remove(tabId) }
                },
            )
        }.onFailure { inFlight.remove(tabId) }
    }

    private fun publishOrDefer(tabId: Long, bitmap: Bitmap, url: String) {
        if (overviewVisible) {
            deferredPreviews.put(tabId, DeferredPreview(bitmap, url))?.bitmap?.let(::retire)
        } else {
            putPreview(tabId, bitmap)
            lastCapturedUrl[tabId] = url
        }
    }

    private fun flushDeferredPreviews() {
        if (deferredPreviews.isEmpty()) return
        val pending = deferredPreviews.toMap()
        deferredPreviews.clear()
        pending.forEach { (tabId, deferred) ->
            if (tabId in privateTabs || tabId in removedTabs) {
                retire(deferred.bitmap)
            } else {
                putPreview(tabId, deferred.bitmap)
                lastCapturedUrl[tabId] = deferred.url
            }
        }
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
        trimCache(maxCacheBytes)
    }

    private fun removePreview(tabId: Long) {
        lru.remove(tabId)
        previews.remove(tabId)?.let {
            cachedBytes = (cachedBytes - it.byteCount.toLong()).coerceAtLeast(0L)
            retire(it)
        }
    }

    private fun removeDeferredPreview(tabId: Long) {
        deferredPreviews.remove(tabId)?.bitmap?.let(::retire)
    }

    private fun trimCache(targetBytes: Long) {
        while (cachedBytes > targetBytes && lru.isNotEmpty()) {
            val eldest = lru.entries.iterator().next().key
            removePreview(eldest)
        }
    }

    private fun retire(bitmap: Bitmap) {
        mainHandler.postDelayed({
            val stillReferenced = previews.values.any { it === bitmap } ||
                deferredPreviews.values.any { it.bitmap === bitmap }
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
