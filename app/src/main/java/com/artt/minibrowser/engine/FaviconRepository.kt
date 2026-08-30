package com.artt.minibrowser.engine

import android.graphics.Bitmap
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal data class FaviconRequest(
    val key: String,
    val origin: String?,
)

/**
 * Owns favicon memory caching, disk/network loading and cache invalidation.
 *
 * UI observes only [generation] and asks for cached/loaded bitmaps; fetch/decode policy stays in
 * the engine layer next to the bounded privacy-preserving disk fetcher.
 */
internal object FaviconRepository {
    private const val MEMORY_CACHE_BYTES = 4 * 1024 * 1024

    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val _generation = MutableStateFlow(0L)
    val generation = _generation.asStateFlow()

    fun request(source: String): FaviconRequest {
        val origin = faviconOrigin(source)
        return FaviconRequest(
            key = origin ?: source.trim().lowercase(),
            origin = origin,
        )
    }

    fun cached(request: FaviconRequest): Bitmap? = synchronized(memoryCache) {
        memoryCache.get(request.key)
    }

    suspend fun load(
        request: FaviconRequest,
        iconsDir: File,
        expectedGeneration: Long,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (expectedGeneration != _generation.value) return@withContext null
        cached(request)?.let { return@withContext it }
        val origin = request.origin ?: return@withContext null
        val file = FaviconFetcher.fetch(origin, iconsDir)
        val bitmap = file.takeIf(File::exists)?.let(::decodeSampledFavicon) ?: return@withContext null
        if (!putIfCurrent(request.key, bitmap, expectedGeneration)) return@withContext null
        bitmap
    }

    suspend fun clear(iconsDir: File) = withContext(Dispatchers.IO) {
        _generation.value += 1L
        synchronized(memoryCache) { memoryCache.evictAll() }
        FaviconFetcher.clear(iconsDir)
    }

    private fun putIfCurrent(key: String, bitmap: Bitmap, expectedGeneration: Long): Boolean {
        if (expectedGeneration != _generation.value) return false
        return synchronized(memoryCache) {
            if (expectedGeneration != _generation.value) {
                false
            } else {
                memoryCache.put(key, bitmap)
                true
            }
        }
    }
}
