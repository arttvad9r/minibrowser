package com.artt.minibrowser.engine

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

// ponytail: иконки через DDG-сервис (наружу уходит только домен);
// заменить на парсинг <link rel="icon"> страницы, если нужна автономность
object FaviconFetcher {
    private const val MAX_BYTES = 1024 * 1024
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memory = ConcurrentHashMap<String, File>()
    private val inFlight = ConcurrentHashMap<String, Deferred<File>>()

    fun cacheFile(host: String, iconsDir: File): File {
        val md5 = MessageDigest.getInstance("MD5").digest(host.lowercase().trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(iconsDir, "$md5.png")
    }

    suspend fun fetch(host: String, iconsDir: File): File {
        val key = host.lowercase().trim()
        val dst = cacheFile(key, iconsDir)
        if (memory[key]?.exists() == true || dst.exists()) {
            memory[key] = dst
            return dst
        }
        val deferred = inFlight.computeIfAbsent(key) {
            scope.async { fetchOnce(key, dst) }
        }
        return try {
            deferred.await().also { if (it.exists()) memory[key] = it }
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private suspend fun fetchOnce(host: String, dst: File): File = withContext(Dispatchers.IO) {
        dst.parentFile?.mkdirs()
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
                    dst.outputStream().use { output ->
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
            }.onFailure { dst.delete() }
        } finally {
            conn.disconnect()
        }
        dst
    }
}
