package com.artt.minibrowser.engine

import android.graphics.BitmapFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded, privacy-preserving favicon fetcher.
 *
 * Fetch only the conventional same-origin /favicon.ico. Third-party favicon proxy services are
 * intentionally avoided because querying them reveals the user's visited hostname. Redirects are
 * followed manually and only when they remain on the exact same origin. Cache v4 keys by canonical
 * origin so HTTP sites and non-default ports do not accidentally reuse an HTTPS/default-port icon.
 */
object FaviconFetcher {
    private const val CACHE_VERSION = "v4"
    private const val MAX_BYTES = 1024 * 1024
    private const val MAX_REDIRECTS = 3
    private const val NEGATIVE_TTL_MS = 6 * 60 * 60 * 1000L
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<File>>()
    private val negative = ConcurrentHashMap<String, Long>()

    fun cacheFile(key: String, iconsDir: File): File {
        val md5 = MessageDigest.getInstance("MD5").digest(key.lowercase().trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(iconsDir, "${CACHE_VERSION}_$md5.png")
    }

    suspend fun fetch(pageUrlOrHost: String, iconsDir: File): File {
        val origin = faviconOrigin(pageUrlOrHost)
            ?: return cacheFile("invalid:${pageUrlOrHost.trim()}", iconsDir)
        val dst = cacheFile(origin, iconsDir)
        if (dst.exists()) return dst
        if (negative[origin]?.let { it > System.currentTimeMillis() } == true) return dst
        val deferred = inFlight.computeIfAbsent(origin) {
            scope.async { fetchOnce(origin, dst) }
        }
        return try {
            deferred.await().also {
                if (!it.exists()) negative[origin] = System.currentTimeMillis() + NEGATIVE_TTL_MS
            }
        } finally {
            inFlight.remove(origin, deferred)
        }
    }

    private suspend fun fetchOnce(origin: String, dst: File): File = withContext(Dispatchers.IO) {
        dst.parentFile?.mkdirs()
        val temp = File("${dst.path}.tmp")
        temp.delete()
        if (downloadCandidate("$origin/favicon.ico", temp) != null) {
            runCatching {
                Files.move(
                    temp.toPath(),
                    dst.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                if (!temp.renameTo(dst)) {
                    temp.delete()
                    dst.delete()
                }
            }
        } else {
            temp.delete()
        }
        dst
    }

    private fun downloadCandidate(url: String, dst: File): Pair<Int, Int>? {
        var current = runCatching { URL(url) }.getOrNull() ?: return null
        var redirects = 0

        while (true) {
            val conn = current.openConnection() as? HttpURLConnection ?: return null
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Minibrowser")

            try {
                val responseCode = conn.responseCode
                if (responseCode.isHttpRedirect()) {
                    if (redirects >= MAX_REDIRECTS) return null
                    current = sameOriginFaviconRedirect(current, conn.getHeaderField("Location")) ?: return null
                    redirects++
                    continue
                }
                if (responseCode !in 200..299 || conn.contentLengthLong > MAX_BYTES) return null

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
                    return null
                }
                return bounds.outWidth to bounds.outHeight
            } catch (_: Throwable) {
                dst.delete()
                return null
            } finally {
                conn.disconnect()
            }
        }
    }
}

/** Canonical network origin for favicon fetching; bare hosts retain the old HTTPS default. */
internal fun faviconOrigin(pageUrlOrHost: String): String? = runCatching {
    val value = pageUrlOrHost.trim()
    if (value.isEmpty()) return@runCatching null
    val input = if (value.contains("://")) value else "https://$value"
    val uri = URI(input)
    val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
        ?: return@runCatching null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val defaultPort = if (scheme == "https") 443 else 80
    val port = uri.port.takeUnless { it == defaultPort }
    URI(scheme, null, host, port, null, null, null).toASCIIString()
}.getOrNull()

private fun Int.isHttpRedirect(): Boolean =
    this == HttpURLConnection.HTTP_MOVED_PERM ||
        this == HttpURLConnection.HTTP_MOVED_TEMP ||
        this == HttpURLConnection.HTTP_SEE_OTHER ||
        this == 307 || this == 308

internal fun sameOriginFaviconRedirect(current: URL, location: String?): URL? {
    if (location.isNullOrBlank()) return null
    val next = runCatching { URL(current, location) }.getOrNull() ?: return null
    if (!current.protocol.equals(next.protocol, ignoreCase = true)) return null
    if (!current.host.equals(next.host, ignoreCase = true)) return null
    if (effectivePort(current) != effectivePort(next)) return null
    if (!next.userInfo.isNullOrBlank()) return null
    return next
}

private fun effectivePort(url: URL): Int = if (url.port >= 0) url.port else url.defaultPort

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
