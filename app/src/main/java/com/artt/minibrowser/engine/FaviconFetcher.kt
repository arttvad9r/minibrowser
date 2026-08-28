package com.artt.minibrowser.engine

import android.graphics.BitmapFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded favicon fetcher.
 *
 * Prefer the site's own icon, then a requested 128px favicon, with the old DuckDuckGo endpoint as
 * a final fallback. Only the hostname is sent to fallback favicon services. Cache v2 intentionally
 * invalidates older 16/32px files that looked pixelated when Compose rendered them at phone dpi.
 */
object FaviconFetcher {
    private const val CACHE_VERSION = "v2"
    private const val MAX_BYTES = 1024 * 1024
    private const val GOOD_ICON_DIMENSION = 96
    private const val NEGATIVE_TTL_MS = 6 * 60 * 60 * 1000L
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<File>>()
    private val negative = ConcurrentHashMap<String, Long>()

    fun cacheFile(host: String, iconsDir: File): File {
        val md5 = MessageDigest.getInstance("MD5").digest(host.lowercase().trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(iconsDir, "${CACHE_VERSION}_$md5.png")
    }

    suspend fun fetch(host: String, iconsDir: File): File {
        val key = host.lowercase().trim()
        val dst = cacheFile(key, iconsDir)
        if (dst.exists()) return dst
        if (negative[key]?.let { it > System.currentTimeMillis() } == true) return dst
        val deferred = inFlight.computeIfAbsent(key) {
            scope.async { fetchOnce(key, dst) }
        }
        return try {
            deferred.await().also {
                if (!it.exists()) negative[key] = System.currentTimeMillis() + NEGATIVE_TTL_MS
            }
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private suspend fun fetchOnce(host: String, dst: File): File = withContext(Dispatchers.IO) {
        dst.parentFile?.mkdirs()
        val encodedHost = URLEncoder.encode(host, StandardCharsets.UTF_8.name())
        val candidates = listOf(
            "https://$host/favicon.ico",
            "https://www.google.com/s2/favicons?domain=$encodedHost&sz=128",
            "https://icons.duckduckgo.com/ip3/$host.ico",
        )

        var best: File? = null
        var bestDimension = 0

        candidates.forEachIndexed { index, url ->
            val temp = File("${dst.path}.$index.tmp")
            temp.delete()
            val dimensions = downloadCandidate(url, temp)
            if (dimensions == null) {
                temp.delete()
                return@forEachIndexed
            }

            val dimension = minOf(dimensions.first, dimensions.second)
            if (dimension > bestDimension) {
                best?.delete()
                best = temp
                bestDimension = dimension
            } else {
                temp.delete()
            }

            if (bestDimension >= GOOD_ICON_DIMENSION) return@forEachIndexed
        }

        val selected = best
        if (selected != null && selected.exists()) {
            runCatching {
                Files.move(
                    selected.toPath(),
                    dst.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                if (!selected.renameTo(dst)) {
                    selected.delete()
                    dst.delete()
                }
            }
        }
        dst
    }

    private fun downloadCandidate(url: String, dst: File): Pair<Int, Int>? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Minibrowser")
        return try {
            if (conn.responseCode !in 200..299 || conn.contentLengthLong > MAX_BYTES) return null
            val type = conn.contentType.orEmpty().lowercase()
            if (type.isNotBlank() && !type.startsWith("image/") && type != "application/octet-stream") {
                return null
            }

            conn.inputStream.use { input ->
                dst.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return null
                        output.write(buffer, 0, read)
                    }
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(dst.path, bounds)
            if (!isValidFaviconDimensions(bounds.outWidth, bounds.outHeight)) {
                dst.delete()
                null
            } else {
                bounds.outWidth to bounds.outHeight
            }
        } catch (_: Throwable) {
            dst.delete()
            null
        } finally {
            conn.disconnect()
        }
    }
}

internal const val FAVICON_MAX_DIMENSION = 4096
internal const val FAVICON_MAX_PIXELS = 16_000_000L

internal fun isValidFaviconDimensions(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width <= FAVICON_MAX_DIMENSION &&
        height <= FAVICON_MAX_DIMENSION && width.toLong() * height.toLong() <= FAVICON_MAX_PIXELS

internal fun faviconSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    while (width / sample > maxDimension || height / sample > maxDimension) sample *= 2
    return sample
}

fun decodeSampledFavicon(file: File, maxDimensionPx: Int = 128): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (!isValidFaviconDimensions(bounds.outWidth, bounds.outHeight)) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = faviconSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
    }
    return BitmapFactory.decodeFile(file.path, options)
}
