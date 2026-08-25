package com.artt.minibrowser.engine

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// ponytail: иконки через DDG-сервис (наружу уходит только домен);
// заменить на парсинг <link rel="icon"> страницы, если нужна автономность
object FaviconFetcher {
    private const val MAX_BYTES = 1024 * 1024
    private const val NEGATIVE_TTL_MS = 6 * 60 * 60 * 1000L
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<File>>()
    private val negative = ConcurrentHashMap<String, Long>()

    fun cacheFile(host: String, iconsDir: File): File {
        val md5 = MessageDigest.getInstance("MD5").digest(host.lowercase().trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(iconsDir, "$md5.png")
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
            deferred.await().also { if (!it.exists()) negative[key] = System.currentTimeMillis() + NEGATIVE_TTL_MS }
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private suspend fun fetchOnce(host: String, dst: File): File = withContext(Dispatchers.IO) {
        dst.parentFile?.mkdirs()
        val temp = File("${dst.path}.tmp")
        val conn = URL("https://icons.duckduckgo.com/ip3/$host.ico").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.instanceFollowRedirects = false
        try {
            if (conn.responseCode !in 200..299 || (conn.contentLengthLong > MAX_BYTES)) return@withContext dst
            val type = conn.contentType.orEmpty().lowercase()
            if (type.isNotBlank() && !type.startsWith("image/") && type != "application/octet-stream") return@withContext dst
            runCatching {
                conn.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) error("favicon too large")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (BitmapFactory.decodeFile(temp.path) == null) error("invalid favicon image")
                runCatching {
                    Files.move(temp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }.getOrElse {
                    if (!temp.renameTo(dst)) error("favicon atomic move failed")
                }
            }.onFailure {
                temp.delete()
                dst.delete()
            }
        } finally {
            conn.disconnect()
        }
        dst
    }
}
